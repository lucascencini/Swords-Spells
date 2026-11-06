package com.minidofus.net;

import com.minidofus.model.Battle;
import com.minidofus.model.Command;
import com.minidofus.model.Fighter;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Team;
import com.minidofus.model.ai.AiAction;
import com.minidofus.model.ai.AiPlayer;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.catalog.SpellBook;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;
import com.minidofus.model.progression.CharacterBuild;
import com.minidofus.model.progression.Progression;
import com.minidofus.model.progression.TalentValidator;
import com.minidofus.server.AccountStore;
import com.minidofus.util.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Serveur de jeu : un salon public (avec son combat) et des combats solo privés contre l'ordinateur. Il fait
 * autorité sur les règles, joue l'ordinateur (places vides, joueurs déconnectés), applique le chrono, gère les
 * comptes et les personnages ({@link AccountStore}) et distribue l'expérience en fin de combat. Toute la logique
 * tourne sur un seul fil ({@link #logic}) : pas de verrou à gérer.
 */
public final class GameServer implements AutoCloseable {

    /** Pause entre deux actions de l'ordinateur, pour que les joueurs suivent les animations. */
    static final long AI_STEP_MS = 900;
    /** Délai accordé en plus du chrono (latence et animations du tour précédent). */
    static final long TIMER_GRACE_MS = 3000;
    /** Au-delà, un joueur déconnecté est retiré du salon (pendant un combat, il garde sa place). */
    static final long LOBBY_GRACE_MS = 60_000;
    /** Sans nouvelles d'un client (même pas un PING) pendant ce délai, la connexion est considérée perdue. */
    static final long SILENCE_TIMEOUT_MS = 20_000;
    private static final int MAX_AI_ACTIONS = 14;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final String password;
    private final Consumer<String> log;
    private final ScheduledExecutorService logic = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "serveur-logique");
        t.setDaemon(true);
        return t;
    });
    private final SecureRandom secure = new SecureRandom();
    private final List<Client> clients = new ArrayList<>();
    private final LobbyState lobby = new LobbyState();
    /** Compte du joueur qui a mis un personnage sur une place du salon. */
    private final String[] slotAccount = new String[LobbyState.SLOTS];
    private final Path dataDir;
    private AccountStore store;
    private ServerSocket socket;
    private volatile boolean running;
    private int nextPlayerId = 1;
    private int nextRoomToken = 1;

    /** Combat du salon public ; combats solo par joueur. */
    private Room publicRoom;
    private final Map<Integer, Room> soloRooms = new HashMap<>();

    private final class Client {
        final Socket socket;
        final BufferedWriter out;
        int playerId = -1;
        String name;
        String token;
        AccountStore.Account account;
        boolean alive = true;
        long disconnectedAt;
        volatile long lastSeen = System.currentTimeMillis();

        Client(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(Message m) {
            if (!alive) return;
            try {
                synchronized (out) {
                    out.write(m.encode());
                    out.write('\n');
                    out.flush();
                }
            } catch (IOException e) {
                alive = false;
            }
        }
    }

    /**
     * Un combat en cours : public (le salon) ou solo (un joueur et ses personnages contre l'ordinateur). Les
     * clients rejouent les commandes diffusées ; les retardataires reçoivent l'en-tête puis tout l'historique.
     */
    private final class Room {
        final int soloOwner;                 // -1 = combat du salon
        final Battle battle;
        final String header;
        final int[] fighterSlot;             // salon : place de chaque combattant
        final int[] fighterOwner;            // solo : joueur de chaque combattant (-1 = ordinateur)
        final String[] fighterAccount;       // compte et personnage de chaque combattant (null = sans progression)
        final String[] fighterCharacter;
        final Difficulty difficulty;
        final int turnSeconds;
        final AiPlayer ai;
        final List<Command> history = new ArrayList<>();
        final List<Long> hashes = new ArrayList<>();
        final int token = nextRoomToken++;
        long turnDeadline;
        int aiActions;
        boolean over;

        Room(int soloOwner, Battle battle, String header, int[] fighterSlot, int[] fighterOwner, String[] fighterAccount,
             String[] fighterCharacter, Difficulty difficulty, int turnSeconds, long seed) {
            this.soloOwner = soloOwner;
            this.battle = battle;
            this.header = header;
            this.fighterSlot = fighterSlot;
            this.fighterOwner = fighterOwner;
            this.fighterAccount = fighterAccount;
            this.fighterCharacter = fighterCharacter;
            this.difficulty = difficulty;
            this.turnSeconds = turnSeconds;
            this.ai = new AiPlayer(difficulty, new Random(seed * 7 + 1));
        }

        boolean solo() {
            return soloOwner >= 0;
        }

        /** Joueur qui contrôle le combattant (-1 = ordinateur : place libre ou joueur déconnecté). */
        int controllerOf(int fighterIndex) {
            int owner = solo() ? fighterOwner[fighterIndex] : lobby.slotOwner[fighterSlot[fighterIndex]];
            if (owner < 0) return -1;
            LobbyState.Player p = lobby.player(owner);
            return p != null && p.connected() && (solo() || !soloRooms.containsKey(owner)) ? owner : -1;
        }

        List<Client> recipients() {
            List<Client> out = new ArrayList<>();
            for (Client c : clients) {
                if (solo() ? c.playerId == soloOwner : !soloRooms.containsKey(c.playerId)) out.add(c);
            }
            return out;
        }

        void broadcast(Message m) {
            for (Client c : recipients()) c.send(m);
        }
    }

    public GameServer(int port, String password, Consumer<String> log) {
        this(port, password, null, log);
    }

    /** {@code dataDir} : dossier de la base des comptes ({@code null} = pas de comptes, invités seulement). */
    public GameServer(int port, String password, Path dataDir, Consumer<String> log) {
        this.port = port;
        this.password = password == null ? "" : password;
        this.dataDir = dataDir;
        this.log = log;
    }

    /** Démarre l'écoute ; renvoie le port réel (utile avec le port 0 dans les tests). */
    public int start() throws IOException {
        if (dataDir != null) {
            store = new AccountStore(dataDir);
            log("Base des comptes : " + dataDir.toAbsolutePath() + " (" + store.size() + " compte(s))");
        }
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        running = true;
        Thread accept = new Thread(this::acceptLoop, "serveur-connexions");
        accept.setDaemon(true);
        accept.start();
        logic.scheduleAtFixedRate(this::tick, 200, 200, TimeUnit.MILLISECONDS);
        log("Serveur à l'écoute sur le port " + socket.getLocalPort()
                + (password.isEmpty() ? " (sans mot de passe)" : " (protégé par mot de passe)"));
        return socket.getLocalPort();
    }

    @Override
    public void close() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // fermeture
        }
        try {
            logic.execute(() -> {
                for (Client c : clients) closeQuietly(c.socket);
            });
        } catch (RejectedExecutionException ignored) {
            // déjà arrêté
        }
        logic.shutdown();
    }

    // ------------------------------------------------------------------ connexions

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = socket.accept();
                s.setTcpNoDelay(true);
                s.setSoTimeout(0);
                Client c = new Client(s);
                Thread reader = new Thread(() -> readLoop(c), "client-" + s.getRemoteSocketAddress());
                reader.setDaemon(true);
                reader.start();
            } catch (SocketException e) {
                if (running) log("Erreur d'écoute : " + e.getMessage());
            } catch (IOException e) {
                log("Connexion refusée : " + e.getMessage());
            }
        }
    }

    private void readLoop(Client c) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(c.socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = readLine(in)) != null) {
                c.lastSeen = System.currentTimeMillis();
                if (line.isEmpty()) continue;
                Message m = Message.decode(line);
                post(() -> handle(c, m));
            }
        } catch (IOException ignored) {
            // déconnexion
        } finally {
            post(() -> disconnected(c));
        }
    }

    /** Confie une tâche au fil de la logique (ignorée si le serveur s'arrête). */
    private void post(Runnable task) {
        if (logic.isShutdown()) return;
        try {
            logic.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log("Erreur interne : " + e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // arrêt en cours
        }
    }

    /** Lecture d'une ligne avec une taille maximale (une ligne trop longue coupe la connexion). */
    private static String readLine(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') return sb.toString();
            if (ch != '\r') sb.append((char) ch);
            if (sb.length() > Message.MAX_LINE) throw new IOException("ligne trop longue");
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void disconnected(Client c) {
        if (!c.alive && !clients.contains(c)) return;
        c.alive = false;
        closeQuietly(c.socket);
        if (c.playerId < 0) {
            clients.remove(c);
            return;
        }
        c.disconnectedAt = System.currentTimeMillis();
        setConnected(c.playerId, false);
        log(c.name + " s'est déconnecté");
        boolean playing = publicRoom != null && !soloRooms.containsKey(c.playerId);
        systemChat(c.name + " s'est déconnecté" + (playing ? " : l'ordinateur joue pour lui en attendant." : "."));
        if (lobby.chefId == c.playerId) electChef();
        broadcastLobby();
        if (publicRoom != null) scheduleAiIfNeeded(publicRoom);
        Room solo = soloRooms.get(c.playerId);
        if (solo != null) scheduleAiIfNeeded(solo);
    }

    // ------------------------------------------------------------------ messages

    private void handle(Client c, Message m) {
        if (c.playerId < 0 && !Protocol.HELLO.equals(m.type())) {
            reject(c, "Présentez-vous d'abord");
            return;
        }
        switch (m.type()) {
            case Protocol.HELLO -> hello(c, m);
            case Protocol.PING -> c.send(Message.of(Protocol.PONG));
            case Protocol.CHAT -> chat(c, m.arg(0));
            case Protocol.CLAIM -> claim(c, m.intArg(0, -1), true);
            case Protocol.RELEASE -> claim(c, m.intArg(0, -1), false);
            case Protocol.CLASS -> changeClass(c, m.intArg(0, -1), m.arg(1));
            case Protocol.SETTINGS -> settings(c, m);
            case Protocol.START -> startPublic(c);
            case Protocol.CMD -> command(c, m.intArg(0, -1), Command.decode(m.arg(1)));
            case Protocol.RESYNC -> {
                Room r = roomOf(c);
                if (r != null) sendMatch(r, c);
            }
            case Protocol.CHAR_NEW -> newCharacter(c, m.arg(0), m.arg(1));
            case Protocol.CHAR_DEL -> deleteCharacter(c, m.arg(0));
            case Protocol.CHAR_SAVE -> saveCharacter(c, m.arg(0), m.arg(1), m.arg(2));
            case Protocol.SOLO -> startSolo(c, m);
            case Protocol.SOLO_QUIT -> quitSolo(c);
            default -> c.send(Message.of(Protocol.NOTICE, "Message inconnu : " + m.type()));
        }
    }

    /** Combat auquel le client participe (solo s'il en a un, sinon celui du salon). */
    private Room roomOf(Client c) {
        Room solo = soloRooms.get(c.playerId);
        return solo != null ? solo : publicRoom;
    }

    private void hello(Client c, Message m) {
        if (c.playerId >= 0) return;
        if (m.intArg(0, -1) != Protocol.VERSION) {
            reject(c, "Version différente du serveur : mettez le jeu à jour (serveur v" + Protocol.VERSION + ")");
            return;
        }
        String name = LobbyState.clean(m.arg(1));
        if (name.isEmpty()) {
            reject(c, "Choisissez un pseudo");
            return;
        }
        if (!password.isEmpty() && !password.equals(m.arg(2))) {
            reject(c, "Mot de passe du serveur incorrect");
            return;
        }
        String token = m.arg(3);
        String accountKey = m.arg(4);
        boolean register = "1".equals(m.arg(5));
        boolean createIfMissing = "2".equals(m.arg(5));              // hébergement sur son PC : compte créé au besoin
        Client previous = byName(name);
        if (previous != null && previous.token.equals(token)) {
            // reconnexion (jeton valide) : la nouvelle connexion remplace l'ancienne, même si elle n'est pas encore tombée
            takeOver(c, previous);
            return;
        }
        // compte : connexion ou création ; sans clé, invité (pas de personnage)
        AccountStore.Account account = null;
        if (!accountKey.isEmpty()) {
            if (store == null) {
                reject(c, "Ce serveur ne gère pas de comptes");
                return;
            }
            try {
                if (register || createIfMissing && store.get(name) == null) {
                    account = store.create(name, accountKey);
                    if (account == null) {
                        reject(c, "Le compte « " + name + " » existe déjà");
                        return;
                    }
                    log("Nouveau compte : " + name);
                } else {
                    account = store.get(name);
                    if (account == null || !store.verify(account, accountKey)) {
                        reject(c, "Compte ou mot de passe incorrect");
                        return;
                    }
                }
            } catch (IOException e) {
                reject(c, "Erreur de la base de comptes : " + e.getMessage());
                return;
            }
            name = account.name;
        } else if (store != null && store.get(name) != null) {
            reject(c, "« " + name + " » est un compte : connectez-vous avec son mot de passe");
            return;
        }
        if (previous != null) {
            if (account == null) {
                reject(c, "« " + name + " » est déjà connecté");
                return;
            }
            // le titulaire du compte se reconnecte (autre PC, jeu relancé) : il reprend sa place
            takeOver(c, previous);
            return;
        }
        if (lobby.players.size() >= Protocol.MAX_PLAYERS) {
            reject(c, "Le serveur est complet (" + Protocol.MAX_PLAYERS + " joueurs)");
            return;
        }
        c.playerId = nextPlayerId++;
        c.name = name;
        c.account = account;
        c.token = HexFormat.of().formatHex(secure.generateSeed(12));
        clients.add(c);
        lobby.players.add(new LobbyState.Player(c.playerId, name, true));
        if (lobby.chefId < 0) lobby.chefId = c.playerId;
        c.send(Message.of(Protocol.WELCOME, c.playerId, c.token));
        sendAccount(c);
        log(name + (account != null ? " (compte)" : " (invité)") + " a rejoint le serveur (" + c.socket.getRemoteSocketAddress() + ")");
        systemChat(name + " a rejoint le salon.");
        broadcastLobby();
        if (publicRoom != null) sendMatch(publicRoom, c);
    }

    /** La nouvelle connexion remplace l'ancienne : même joueur, même place, même combat. */
    private void takeOver(Client c, Client previous) {
        clients.remove(previous);
        previous.alive = false;
        closeQuietly(previous.socket);
        c.playerId = previous.playerId;
        c.name = previous.name;
        c.token = previous.token;
        c.account = previous.account;
        clients.add(c);
        setConnected(c.playerId, true);
        c.send(Message.of(Protocol.WELCOME, c.playerId, c.token));
        sendAccount(c);
        log(c.name + " s'est reconnecté");
        systemChat(c.name + " est de retour.");
        broadcastLobby();
        Room r = roomOf(c);
        if (r != null) sendMatch(r, c);
    }

    private void sendAccount(Client c) {
        if (c.account != null) c.send(Message.of(Protocol.ACCOUNT, c.account.toClientJson()));
    }

    private void reject(Client c, String reason) {
        c.send(Message.of(Protocol.REJECT, reason));
        c.alive = false;
        closeQuietly(c.socket);
    }

    private void chat(Client c, String text) {
        String t = text.replace('\n', ' ').trim();
        if (t.isEmpty()) return;
        if (t.length() > Protocol.MAX_CHAT) t = t.substring(0, Protocol.MAX_CHAT);
        broadcast(Message.of(Protocol.CHAT, c.name, t));
    }

    private void systemChat(String text) {
        broadcast(Message.of(Protocol.CHAT, "", text));
    }

    // ------------------------------------------------------------------ personnages

    private boolean needsAccount(Client c) {
        if (c.account != null) return false;
        c.send(Message.of(Protocol.NOTICE, "Connectez-vous avec un compte pour avoir des personnages"));
        return true;
    }

    private void newCharacter(Client c, String rawName, String classId) {
        if (needsAccount(c)) return;
        String name = CharacterBuild.cleanName(rawName);
        if (name.isEmpty()) {
            c.send(Message.of(Protocol.NOTICE, "Donnez un nom à votre personnage"));
            return;
        }
        if (FighterCatalog.ALL.stream().noneMatch(fc -> fc.id().equals(classId))) {
            c.send(Message.of(Protocol.NOTICE, "Classe inconnue"));
            return;
        }
        if (c.account.characters.size() >= AccountStore.MAX_CHARACTERS) {
            c.send(Message.of(Protocol.NOTICE, "Six personnages au maximum par compte"));
            return;
        }
        if (c.account.characters.stream().anyMatch(ch -> ch.name.equalsIgnoreCase(name))) {
            c.send(Message.of(Protocol.NOTICE, "Vous avez déjà un personnage nommé « " + name + " »"));
            return;
        }
        AccountStore.Character ch = new AccountStore.Character();
        ch.id = store.newCharacterId();
        ch.name = name;
        ch.classId = classId;
        c.account.characters.add(ch);
        persist(c);
        log(c.name + " crée " + name + " (" + FighterCatalog.byId(classId).displayName() + ")");
    }

    private void deleteCharacter(Client c, String id) {
        if (needsAccount(c)) return;
        if (engaged(c, id)) return;
        if (c.account.characters.removeIf(ch -> ch.id.equals(id))) {
            for (int s = 0; s < LobbyState.SLOTS; s++) {
                if (id.equals(lobby.slotChar[s]) && c.account.name.equals(slotAccount[s])) clearSlotCharacter(s);
            }
            persist(c);
            broadcastLobby();
        }
    }

    /** Talents et sorts : le serveur revérifie tout (le client n'est jamais la source d'autorité). */
    private void saveCharacter(Client c, String id, String talentsText, String spellsText) {
        if (needsAccount(c)) return;
        AccountStore.Character ch = c.account.character(id);
        if (ch == null) return;
        if (engaged(c, id)) return;
        Map<String, Integer> talents = new LinkedHashMap<>();
        try {
            for (String kv : talentsText.isEmpty() ? new String[0] : talentsText.split(",")) {
                String[] p = kv.split("=");
                talents.put(p[0], Integer.parseInt(p[1]));
            }
        } catch (RuntimeException e) {
            c.send(Message.of(Protocol.NOTICE, "Talents illisibles"));
            sendAccount(c);
            return;
        }
        String why = TalentValidator.validate(ch.classId, ch.level, talents);
        List<String> spells = spellsText.isEmpty() ? List.of() : List.of(spellsText.split(","));
        if (why == null) why = validateSpells(ch, spells);
        if (why != null) {
            c.send(Message.of(Protocol.NOTICE, why));
            sendAccount(c);
            return;
        }
        ch.talents.clear();
        talents.forEach((k, v) -> {
            if (v > 0) ch.talents.put(k, v);
        });
        ch.spells.clear();
        ch.spells.addAll(spells);
        persist(c);
        refreshSlotLabels();
    }

    private static String validateSpells(AccountStore.Character ch, List<String> spells) {
        int normal = 0;
        int ultimate = 0;
        Set<String> seen = new HashSet<>();
        for (String id : spells) {
            SpellBook.Entry e = SpellBook.entry(ch.classId, id);
            if (e == null) return "Sort inconnu : " + id;
            if (e.level() > ch.level) return e.spell().name() + " : niveau " + e.level() + " requis";
            if (!seen.add(id)) return "Sort en double : " + e.spell().name();
            if (e.ultimate()) ultimate++;
            else normal++;
        }
        if (normal > SpellBook.MAX_EQUIPPED) return SpellBook.MAX_EQUIPPED + " sorts équipés au maximum (plus l'ultime)";
        if (ultimate > 1) return "Un seul sort ultime";
        return null;
    }

    /** Un personnage engagé dans un combat en cours ne peut pas être modifié ni supprimé. */
    private boolean engaged(Client c, String id) {
        for (Room r : allRooms()) {
            if (r.over) continue;
            for (int i = 0; i < r.fighterCharacter.length; i++) {
                if (id.equals(r.fighterCharacter[i]) && c.account.name.equals(r.fighterAccount[i])) {
                    c.send(Message.of(Protocol.NOTICE, "Ce personnage est en plein combat"));
                    return true;
                }
            }
        }
        return false;
    }

    private void persist(Client c) {
        try {
            store.save(c.account);
        } catch (IOException e) {
            log("Sauvegarde impossible du compte " + c.account.name + " : " + e.getMessage());
            c.send(Message.of(Protocol.NOTICE, "Sauvegarde impossible : " + e.getMessage()));
        }
        sendAccount(c);
    }

    // ------------------------------------------------------------------ salon

    private void claim(Client c, int slot, boolean take) {
        if (publicRoom != null || soloRooms.containsKey(c.playerId) || slot < 0 || slot >= LobbyState.SLOTS || !lobby.isUsed(slot)) return;
        if (take && lobby.slotOwner[slot] < 0) {
            lobby.slotOwner[slot] = c.playerId;
        } else if (!take && lobby.slotOwner[slot] == c.playerId) {
            lobby.slotOwner[slot] = -1;
            clearSlotCharacter(slot);
        }
        broadcastLobby();
    }

    /** Classe d'une place, ou « char:id » : le joueur y met un de ses personnages. */
    private void changeClass(Client c, int slot, String value) {
        if (publicRoom != null || slot < 0 || slot >= LobbyState.SLOTS) return;
        boolean owner = lobby.slotOwner[slot] == c.playerId;
        boolean allowed = owner || (lobby.chefId == c.playerId && lobby.slotOwner[slot] < 0);
        if (!allowed) return;
        if (value.startsWith("char:")) {
            if (!owner || c.account == null) return;
            AccountStore.Character ch = c.account.character(value.substring(5));
            if (ch == null) return;
            lobby.slotChar[slot] = ch.id;
            lobby.slotClass[slot] = ch.classId;
            slotAccount[slot] = c.account.name;
            refreshSlotLabels();
            broadcastLobby();
            return;
        }
        for (FighterClass fc : FighterCatalog.ALL) {
            if (fc.id().equals(value)) {
                lobby.slotClass[slot] = value;
                clearSlotCharacter(slot);
                broadcastLobby();
                return;
            }
        }
    }

    private void clearSlotCharacter(int slot) {
        lobby.slotChar[slot] = "";
        lobby.slotLabel[slot] = "";
        slotAccount[slot] = null;
    }

    private AccountStore.Character slotCharacter(int slot) {
        if (lobby.slotChar[slot].isEmpty() || slotAccount[slot] == null || store == null) return null;
        AccountStore.Account a = store.get(slotAccount[slot]);
        return a == null ? null : a.character(lobby.slotChar[slot]);
    }

    private void refreshSlotLabels() {
        for (int s = 0; s < LobbyState.SLOTS; s++) {
            AccountStore.Character ch = slotCharacter(s);
            if (ch == null) {
                if (!lobby.slotChar[s].isEmpty()) clearSlotCharacter(s);
            } else {
                lobby.slotLabel[s] = LobbyState.clean(ch.name) + " niv. " + ch.level;
            }
        }
        broadcastLobby();
    }

    private void settings(Client c, Message m) {
        if (publicRoom != null || lobby.chefId != c.playerId) return;
        try {
            lobby.map = "random".equals(m.arg(0)) ? null : MapTheme.valueOf(m.arg(0));
            lobby.teamSize = Math.max(1, Math.min(3, m.intArg(1, lobby.teamSize)));
            lobby.difficulty = Difficulty.valueOf(m.arg(2));
            lobby.turnSeconds = Math.max(0, Math.min(300, m.intArg(3, lobby.turnSeconds)));
            if (m.args().size() > 4) lobby.equalLevels = "EQUAL".equals(m.arg(4));
        } catch (IllegalArgumentException e) {
            c.send(Message.of(Protocol.NOTICE, "Réglages invalides"));
        }
        for (int s = 0; s < LobbyState.SLOTS; s++) {
            if (!lobby.isUsed(s)) {
                lobby.slotOwner[s] = -1;
                clearSlotCharacter(s);
            }
        }
        broadcastLobby();
    }

    // ------------------------------------------------------------------ combats

    private void startPublic(Client c) {
        if (publicRoom != null || lobby.chefId != c.playerId) return;
        MapTheme theme = lobby.map != null ? lobby.map : MapTheme.values()[secure.nextInt(MapTheme.values().length)];
        long seed = secure.nextInt(1 << 24);
        List<CharacterBuild> red = new ArrayList<>();
        List<CharacterBuild> blue = new ArrayList<>();
        int maxLevel = 1;
        for (Team t : Team.values()) {
            for (int i = 0; i < lobby.teamSize; i++) {
                int s = LobbyState.slotOf(t, i);
                AccountStore.Character ch = slotCharacter(s);
                CharacterBuild b = ch != null ? ch.build() : CharacterBuild.basic(lobby.slotClass[s]);
                maxLevel = Math.max(maxLevel, b.level());
                (t == Team.RED ? red : blue).add(b);
            }
        }
        int override = lobby.equalLevels ? maxLevel : 0;
        Battle battle = createBattleFromBuilds(theme, seed, red, blue, override);
        int n = battle.fighters().size();
        int[] slots = new int[n];
        String[] accounts = new String[n];
        String[] chars = new String[n];
        StringBuilder controllers = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Fighter f = battle.fighters().get(i);
            slots[i] = LobbyState.slotOf(f.team(), f.slot());
            if (slotCharacter(slots[i]) != null) {
                accounts[i] = slotAccount[slots[i]];
                chars[i] = lobby.slotChar[slots[i]];
            }
            if (i > 0) controllers.append(',');
            controllers.append(lobby.slotOwner[slots[i]]);
        }
        String header = header(seed, theme, red, blue, controllers.toString(), lobby.turnSeconds, override);
        publicRoom = new Room(-1, battle, header, slots, null, accounts, chars, lobby.difficulty, lobby.turnSeconds, seed);
        lobby.inMatch = true;
        log("Combat lancé : " + theme.displayName() + ", " + lobby.teamSize + " contre " + lobby.teamSize
                + (override > 0 ? " (niveaux égalisés : " + override + ")" : ""));
        broadcastLobby();
        for (Client k : publicRoom.recipients()) sendMatch(publicRoom, k);
        beginTurnClock(publicRoom);
        scheduleAiIfNeeded(publicRoom);
    }

    /** Combat solo : les personnages du joueur (rouges) contre l'ordinateur, au niveau moyen de l'équipe. */
    private void startSolo(Client c, Message m) {
        if (needsAccount(c)) return;
        if (soloRooms.containsKey(c.playerId)) return;
        int size = Math.max(1, Math.min(3, m.intArg(0, 1)));
        MapTheme theme;
        Difficulty difficulty;
        try {
            theme = "random".equals(m.arg(1)) ? MapTheme.values()[secure.nextInt(MapTheme.values().length)] : MapTheme.valueOf(m.arg(1));
            difficulty = Difficulty.valueOf(m.arg(2));
        } catch (IllegalArgumentException e) {
            c.send(Message.of(Protocol.NOTICE, "Réglages invalides"));
            return;
        }
        List<AccountStore.Character> team = new ArrayList<>();
        for (String id : m.arg(3).split(",")) {
            AccountStore.Character ch = c.account.character(id);
            if (ch == null || team.contains(ch)) continue;
            team.add(ch);
        }
        if (team.size() != size) {
            c.send(Message.of(Protocol.NOTICE, "Choisissez " + size + " personnage" + (size > 1 ? "s différents" : "")));
            return;
        }
        // le joueur quitte ses places du salon : il ne peut pas être à deux endroits à la fois
        for (int s = 0; s < LobbyState.SLOTS; s++) {
            if (lobby.slotOwner[s] == c.playerId) {
                lobby.slotOwner[s] = -1;
                clearSlotCharacter(s);
            }
        }
        long seed = secure.nextInt(1 << 24);
        List<CharacterBuild> red = new ArrayList<>();
        int levelSum = 0;
        for (AccountStore.Character ch : team) {
            red.add(ch.build());
            levelSum += ch.level;
        }
        int enemyLevel = Math.max(1, Math.round(levelSum / (float) size));
        List<CharacterBuild> blue = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String cls = FighterCatalog.ALL.get(secure.nextInt(FighterCatalog.ALL.size())).id();
            blue.add(new CharacterBuild("", cls, enemyLevel, Map.of(), List.of()));
        }
        Battle battle = createBattleFromBuilds(theme, seed, red, blue, 0);
        int n = battle.fighters().size();
        int[] owners = new int[n];
        String[] accounts = new String[n];
        String[] chars = new String[n];
        StringBuilder controllers = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Fighter f = battle.fighters().get(i);
            boolean mine = f.team() == Team.RED;
            owners[i] = mine ? c.playerId : -1;
            if (mine) {
                accounts[i] = c.account.name;
                chars[i] = team.get(f.slot()).id;
            }
            if (i > 0) controllers.append(',');
            controllers.append(owners[i]);
        }
        String header = header(seed, theme, red, blue, controllers.toString(), 0, 0);
        Room room = new Room(c.playerId, battle, header, null, owners, accounts, chars, difficulty, 0, seed);
        soloRooms.put(c.playerId, room);
        log(c.name + " lance un combat solo (" + size + " contre " + size + ", " + difficulty + ", niveau " + enemyLevel + ")");
        broadcastLobby();
        sendMatch(room, c);
        scheduleAiIfNeeded(room);
    }

    private void quitSolo(Client c) {
        Room r = soloRooms.remove(c.playerId);
        if (r == null) return;
        if (!r.over) log(c.name + " abandonne son combat solo (pas d'expérience)");
        r.over = true;
        broadcastLobby();
        if (publicRoom != null) sendMatch(publicRoom, c);
    }

    private static String header(long seed, MapTheme theme, List<CharacterBuild> red, List<CharacterBuild> blue,
                                 String controllers, int turnSeconds, int levelOverride) {
        return String.join("\t", String.valueOf(seed), theme.name(), builds(red), builds(blue), controllers,
                String.valueOf(turnSeconds), String.valueOf(levelOverride));
    }

    private static String builds(List<CharacterBuild> list) {
        return String.join("|", list.stream().map(CharacterBuild::encode).toList());
    }

    /** Même construction que les clients : la graine suffit à obtenir exactement le même combat. */
    public static Battle createBattle(MapTheme theme, long seed, List<FighterClass> red, List<FighterClass> blue) {
        Arena arena = MapGenerator.generate(theme, seed);
        List<Fighter> fighters = new ArrayList<>();
        for (int i = 0; i < red.size(); i++) fighters.add(new Fighter(red.get(i), Team.RED, i, arena.redSpawns().get(i)));
        for (int i = 0; i < blue.size(); i++) fighters.add(new Fighter(blue.get(i), Team.BLUE, i, arena.blueSpawns().get(i)));
        Battle b = new Battle(arena.board(), fighters, new Random(seed));
        b.start();
        return b;
    }

    public static Battle createBattleFromBuilds(MapTheme theme, long seed, List<CharacterBuild> red, List<CharacterBuild> blue,
                                               int levelOverride) {
        return createBattle(theme, seed, red.stream().map(b -> b.toFighterClass(levelOverride)).toList(),
                blue.stream().map(b -> b.toFighterClass(levelOverride)).toList());
    }

    /** Envoie un combat à un client : en-tête puis toutes les commandes déjà jouées. */
    private void sendMatch(Room r, Client c) {
        if (r == null || r.over) return;
        if (!r.recipients().contains(c)) return;
        c.send(new Message(Protocol.MATCH, List.of(r.header.split("\t", -1))));
        for (int i = 0; i < r.history.size(); i++) {
            c.send(Message.of(Protocol.CMD, i, r.history.get(i).encode(), r.hashes.get(i)));
        }
        c.send(Message.of(Protocol.SYNCED));
        sendTimer(r, c);
    }

    private void command(Client c, int expectedIndex, Command command) {
        Room r = roomOf(c);
        if (r == null || r.over || command == null) return;
        if (expectedIndex != r.history.size()) return; // commande partie avant la précédente : ignorée
        int fighterIndex = r.battle.fighters().indexOf(r.battle.active());
        if (r.controllerOf(fighterIndex) != c.playerId) {
            c.send(Message.of(Protocol.NOTICE, "Ce n'est pas votre tour"));
            return;
        }
        if (!execute(r, command)) c.send(Message.of(Protocol.NOTICE, "Action impossible"));
    }

    /** Applique et diffuse une commande ; renvoie faux si elle est invalide. */
    private boolean execute(Room r, Command command) {
        Fighter before = r.battle.active();
        if (!r.battle.apply(command)) return false;
        r.history.add(command);
        r.hashes.add(r.battle.stateHash());
        r.broadcast(Message.of(Protocol.CMD, r.history.size() - 1, command.encode(), r.battle.stateHash()));
        if (r.battle.isOver()) {
            endMatch(r);
            return true;
        }
        if (r.battle.active() != before || command instanceof Command.EndTurn) {
            r.aiActions = 0;
            beginTurnClock(r);
        }
        scheduleAiIfNeeded(r);
        return true;
    }

    private void endMatch(Room r) {
        Team w = r.battle.winner();
        r.over = true;
        r.turnDeadline = 0;
        distributeRewards(r);
        if (r.solo()) {
            log("Combat solo de " + nameOf(r.soloOwner) + " terminé : " + (w == Team.RED ? "victoire" : "défaite"));
            return; // la pièce reste jusqu'au retour du joueur (SOLO_QUIT)
        }
        log("Combat terminé : victoire de l'équipe " + w.displayName());
        systemChat("Victoire de l'équipe " + w.displayName() + " !");
        publicRoom = null;
        lobby.inMatch = false;
        refreshSlotLabels();
    }

    /** Expérience de chaque personnage engagé, enregistrée puis annoncée à son joueur. */
    private void distributeRewards(Room r) {
        if (store == null) return;
        Map<String, List<Object>> reports = new LinkedHashMap<>();
        List<Fighter> fighters = r.battle.fighters();
        for (int i = 0; i < fighters.size(); i++) {
            if (r.fighterCharacter[i] == null) continue;
            AccountStore.Account a = store.get(r.fighterAccount[i]);
            AccountStore.Character ch = a == null ? null : a.character(r.fighterCharacter[i]);
            if (ch == null) continue;
            Fighter f = fighters.get(i);
            double enemyLevel = 0;
            int enemies = 0;
            boolean humanEnemy = false;
            for (int j = 0; j < fighters.size(); j++) {
                Fighter e = fighters.get(j);
                if (e.team() == f.team()) continue;
                enemyLevel += e.fighterClass().level();
                enemies++;
                if (r.solo() ? r.fighterOwner[j] >= 0 : lobby.slotOwner[r.fighterSlot[j]] >= 0) humanEnemy = true;
            }
            enemyLevel /= Math.max(1, enemies);
            boolean won = r.battle.winner() == f.team();
            int xp = Progression.reward(ch.level, enemyLevel, won, humanEnemy ? null : r.difficulty);
            int[] after = Progression.addXp(ch.level, ch.xp, xp);
            ch.level = after[0];
            ch.xp = after[1];
            if (won) ch.wins++;
            else ch.losses++;
            Map<String, Object> rep = new LinkedHashMap<>();
            rep.put("id", ch.id);
            rep.put("name", ch.name);
            rep.put("xp", xp);
            rep.put("level", ch.level);
            rep.put("levelUps", after[2]);
            rep.put("won", won);
            reports.computeIfAbsent(a.name, k -> new ArrayList<>()).add(rep);
            if (after[2] > 0) log(ch.name + " (" + a.name + ") passe niveau " + ch.level);
        }
        for (Map.Entry<String, List<Object>> e : reports.entrySet()) {
            AccountStore.Account a = store.get(e.getKey());
            try {
                store.save(a);
            } catch (IOException ex) {
                log("Sauvegarde impossible du compte " + a.name + " : " + ex.getMessage());
            }
            for (Client c : clients) {
                if (c.account == a) {
                    c.send(Message.of(Protocol.REWARD, Json.write(e.getValue())));
                    sendAccount(c);
                }
            }
        }
    }

    private void beginTurnClock(Room r) {
        r.turnDeadline = r.turnSeconds > 0 ? System.currentTimeMillis() + r.turnSeconds * 1000L + TIMER_GRACE_MS : 0;
        for (Client c : r.recipients()) sendTimer(r, c);
    }

    private void sendTimer(Room r, Client c) {
        if (r.over) return;
        // la marge de grâce n'est pas annoncée : le joueur voit exactement la durée choisie dans le salon
        long left = r.turnDeadline == 0 ? 0 : Math.max(1, (r.turnDeadline - TIMER_GRACE_MS - System.currentTimeMillis() + 999) / 1000);
        c.send(Message.of(Protocol.TIMER, left));
    }

    private void scheduleAiIfNeeded(Room r) {
        if (r.over) return;
        int fighterIndex = r.battle.fighters().indexOf(r.battle.active());
        if (r.controllerOf(fighterIndex) >= 0) return;
        int expected = r.history.size();
        logic.schedule(() -> {
            if (r.over || r.history.size() != expected) return;
            if (r != publicRoom && soloRooms.get(r.soloOwner) != r) return;
            if (r.controllerOf(r.battle.fighters().indexOf(r.battle.active())) >= 0) return;
            AiAction a = r.ai.decide(r.battle);
            Command cmd = r.aiActions >= MAX_AI_ACTIONS ? new Command.EndTurn() : a.toCommand(r.battle);
            r.aiActions++;
            if (!execute(r, cmd)) execute(r, new Command.EndTurn());
        }, AI_STEP_MS, TimeUnit.MILLISECONDS);
    }

    private List<Room> allRooms() {
        List<Room> rooms = new ArrayList<>(soloRooms.values());
        if (publicRoom != null) rooms.add(publicRoom);
        return rooms;
    }

    // ------------------------------------------------------------------ horloge

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            for (Client c : new ArrayList<>(clients)) {
                if (c.alive && now - c.lastSeen > SILENCE_TIMEOUT_MS) {
                    log((c.name == null ? "Client" : c.name) + " ne répond plus");
                    closeQuietly(c.socket); // le fil de lecture s'arrête et signale la déconnexion
                }
            }
            for (Room r : allRooms()) {
                if (r.over || r.turnDeadline <= 0 || now <= r.turnDeadline) continue;
                Fighter f = r.battle.active();
                if (r.controllerOf(r.battle.fighters().indexOf(f)) >= 0) {
                    systemChat("Temps écoulé pour " + f.name() + " : fin du tour.");
                    execute(r, new Command.EndTurn());
                }
            }
            boolean changed = false;
            for (Client c : new ArrayList<>(clients)) {
                if (c.alive || c.playerId < 0 || now - c.disconnectedAt <= LOBBY_GRACE_MS) continue;
                boolean inPublic = publicRoom != null && controlsSomething(publicRoom, c.playerId);
                if (inPublic) continue;                               // il garde sa place jusqu'à la fin du combat
                Room solo = soloRooms.remove(c.playerId);
                if (solo != null) solo.over = true;                   // abandon : pas d'expérience
                removePlayer(c);
                changed = true;
            }
            if (changed) broadcastLobby();
        } catch (RuntimeException e) {
            log("Erreur interne : " + e);
        }
    }

    private boolean controlsSomething(Room r, int playerId) {
        for (int i = 0; i < r.fighterSlot.length; i++) if (lobby.slotOwner[r.fighterSlot[i]] == playerId) return true;
        return false;
    }

    private void removePlayer(Client c) {
        clients.remove(c);
        lobby.players.removeIf(p -> p.id() == c.playerId);
        for (int s = 0; s < LobbyState.SLOTS; s++) {
            if (lobby.slotOwner[s] == c.playerId) {
                lobby.slotOwner[s] = -1;
                clearSlotCharacter(s);
            }
        }
        if (lobby.chefId == c.playerId) electChef();
        log(c.name + " a quitté le serveur");
    }

    // ------------------------------------------------------------------ outils

    private Client byName(String name) {
        for (Client c : clients) if (name.equalsIgnoreCase(c.name)) return c;
        return null;
    }

    private String nameOf(int playerId) {
        LobbyState.Player p = lobby.player(playerId);
        return p == null ? "?" : p.name();
    }

    private void setConnected(int playerId, boolean connected) {
        for (int i = 0; i < lobby.players.size(); i++) {
            LobbyState.Player p = lobby.players.get(i);
            if (p.id() == playerId) lobby.players.set(i, new LobbyState.Player(p.id(), p.name(), connected));
        }
    }

    private void electChef() {
        lobby.chefId = -1;
        for (LobbyState.Player p : lobby.players) {
            if (p.connected()) {
                lobby.chefId = p.id();
                systemChat(p.name() + " devient chef du salon.");
                return;
            }
        }
    }

    private void broadcastLobby() {
        lobby.solo.clear();
        lobby.solo.addAll(soloRooms.keySet());
        broadcast(Message.of(Protocol.LOBBY, lobby.encode()));
    }

    private void broadcast(Message m) {
        for (Client c : clients) c.send(m);
    }

    private void log(String text) {
        log.accept("[" + LocalTime.now().format(TIME) + "] " + text);
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // déjà fermé
        }
    }
}
