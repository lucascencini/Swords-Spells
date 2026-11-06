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
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;

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
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Serveur de jeu : un salon, un combat à la fois. Il fait autorité sur les règles, joue l'ordinateur
 * (places vides, joueurs déconnectés), applique le chrono et diffuse les commandes à tous les clients.
 * Toute la logique tourne sur un seul fil ({@link #logic}) : pas de verrou à gérer.
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
    private ServerSocket socket;
    private volatile boolean running;
    private int nextPlayerId = 1;

    // combat en cours
    private Battle battle;
    private final List<Command> history = new ArrayList<>();
    private final List<Long> hashes = new ArrayList<>();
    private String matchHeader;          // arguments du message MATCH, renvoyés aux retardataires
    private int[] fighterSlot;           // place du salon de chaque combattant
    private AiPlayer ai;
    private long turnDeadline;           // 0 = pas de chrono
    private int aiActions;
    private int matchToken;              // invalide les tâches planifiées d'un combat précédent

    /** Un joueur connecté (ou en attente de reconnexion). */
    private final class Client {
        final Socket socket;
        final BufferedWriter out;
        int playerId = -1;
        String name;
        String token;
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

    public GameServer(int port, String password, Consumer<String> log) {
        this.port = port;
        this.password = password == null ? "" : password;
        this.log = log;
    }

    /** Démarre l'écoute ; renvoie le port réel (utile avec le port 0 dans les tests). */
    public int start() throws IOException {
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
        logic.execute(() -> {
            for (Client c : clients) closeQuietly(c.socket);
        });
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
        systemChat(c.name + " s'est déconnecté" + (lobby.inMatch ? " : l'ordinateur joue pour lui en attendant." : "."));
        if (lobby.chefId == c.playerId) electChef();
        broadcastLobby();
        if (lobby.inMatch) scheduleAiIfNeeded();
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
            case Protocol.START -> startMatch(c);
            case Protocol.CMD -> command(c, m.intArg(0, -1), Command.decode(m.arg(1)));
            case Protocol.RESYNC -> {
                if (lobby.inMatch) sendMatch(c);
            }
            default -> c.send(Message.of(Protocol.NOTICE, "Message inconnu : " + m.type()));
        }
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
            reject(c, "Mot de passe incorrect");
            return;
        }
        String token = m.arg(3);
        Client previous = byName(name);
        if (previous != null) {
            if (!previous.token.equals(token)) {
                reject(c, "Le pseudo « " + name + " » est déjà utilisé");
                return;
            }
            // reconnexion (jeton valide) : la nouvelle connexion remplace l'ancienne, même si elle n'est pas encore tombée
            clients.remove(previous);
            previous.alive = false;
            closeQuietly(previous.socket);
            c.playerId = previous.playerId;
            c.name = previous.name;
            c.token = previous.token;
            clients.add(c);
            setConnected(c.playerId, true);
            c.send(Message.of(Protocol.WELCOME, c.playerId, c.token));
            log(name + " s'est reconnecté");
            systemChat(name + " est de retour.");
            broadcastLobby();
            if (lobby.inMatch) sendMatch(c);
            return;
        }
        if (lobby.players.size() >= Protocol.MAX_PLAYERS) {
            reject(c, "Le salon est complet (" + Protocol.MAX_PLAYERS + " joueurs)");
            return;
        }
        c.playerId = nextPlayerId++;
        c.name = name;
        c.token = HexFormat.of().formatHex(secure.generateSeed(12));
        clients.add(c);
        lobby.players.add(new LobbyState.Player(c.playerId, name, true));
        if (lobby.chefId < 0) lobby.chefId = c.playerId;
        c.send(Message.of(Protocol.WELCOME, c.playerId, c.token));
        log(name + " a rejoint le salon (" + c.socket.getRemoteSocketAddress() + ")");
        systemChat(name + " a rejoint le salon.");
        broadcastLobby();
        if (lobby.inMatch) sendMatch(c);
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

    private void claim(Client c, int slot, boolean take) {
        if (lobby.inMatch || slot < 0 || slot >= LobbyState.SLOTS || !lobby.isUsed(slot)) return;
        if (take && lobby.slotOwner[slot] < 0) lobby.slotOwner[slot] = c.playerId;
        else if (!take && lobby.slotOwner[slot] == c.playerId) lobby.slotOwner[slot] = -1;
        broadcastLobby();
    }

    private void changeClass(Client c, int slot, String classId) {
        if (lobby.inMatch || slot < 0 || slot >= LobbyState.SLOTS) return;
        boolean allowed = lobby.slotOwner[slot] == c.playerId
                || (lobby.chefId == c.playerId && lobby.slotOwner[slot] < 0);
        if (!allowed) return;
        for (FighterClass fc : FighterCatalog.ALL) {
            if (fc.id().equals(classId)) {
                lobby.slotClass[slot] = classId;
                broadcastLobby();
                return;
            }
        }
    }

    private void settings(Client c, Message m) {
        if (lobby.inMatch || lobby.chefId != c.playerId) return;
        try {
            lobby.map = "random".equals(m.arg(0)) ? null : MapTheme.valueOf(m.arg(0));
            lobby.teamSize = Math.max(1, Math.min(3, m.intArg(1, lobby.teamSize)));
            lobby.difficulty = Difficulty.valueOf(m.arg(2));
            lobby.turnSeconds = Math.max(0, Math.min(300, m.intArg(3, lobby.turnSeconds)));
        } catch (IllegalArgumentException e) {
            c.send(Message.of(Protocol.NOTICE, "Réglages invalides"));
        }
        for (int s = 0; s < LobbyState.SLOTS; s++) if (!lobby.isUsed(s)) lobby.slotOwner[s] = -1;
        broadcastLobby();
    }

    // ------------------------------------------------------------------ combat

    private void startMatch(Client c) {
        if (lobby.inMatch || lobby.chefId != c.playerId) return;
        MapTheme theme = lobby.map != null ? lobby.map : MapTheme.values()[secure.nextInt(MapTheme.values().length)];
        long seed = secure.nextInt(1 << 24);
        List<FighterClass> red = lobby.classes(Team.RED);
        List<FighterClass> blue = lobby.classes(Team.BLUE);
        battle = createBattle(theme, seed, red, blue);
        fighterSlot = new int[battle.fighters().size()];
        StringBuilder controllers = new StringBuilder();
        for (int i = 0; i < battle.fighters().size(); i++) {
            Fighter f = battle.fighters().get(i);
            fighterSlot[i] = LobbyState.slotOf(f.team(), f.slot());
            if (i > 0) controllers.append(',');
            controllers.append(lobby.slotOwner[fighterSlot[i]]);
        }
        matchHeader = String.join("\t", String.valueOf(seed), theme.name(),
                ids(red), ids(blue), controllers.toString(), String.valueOf(lobby.turnSeconds));
        ai = new AiPlayer(lobby.difficulty, new Random(seed * 7 + 1));
        history.clear();
        hashes.clear();
        matchToken++;
        lobby.inMatch = true;
        log("Combat lancé : " + theme.displayName() + ", " + lobby.teamSize + " contre " + lobby.teamSize);
        broadcastLobby();
        for (Client k : clients) sendMatch(k);
        beginTurnClock();
        scheduleAiIfNeeded();
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

    private static String ids(List<FighterClass> classes) {
        return String.join(",", classes.stream().map(FighterClass::id).toList());
    }

    /** Envoie le combat en cours à un client : en-tête puis toutes les commandes déjà jouées. */
    private void sendMatch(Client c) {
        if (!lobby.inMatch) return;
        c.send(new Message(Protocol.MATCH, List.of(matchHeader.split("\t", -1))));
        for (int i = 0; i < history.size(); i++) {
            c.send(Message.of(Protocol.CMD, i, history.get(i).encode(), hashes.get(i)));
        }
        c.send(Message.of(Protocol.SYNCED));
        sendTimer(c);
    }

    private void command(Client c, int expectedIndex, Command command) {
        if (!lobby.inMatch || command == null) return;
        if (expectedIndex != history.size()) return; // commande partie avant la précédente : ignorée
        int fighterIndex = battle.fighters().indexOf(battle.active());
        if (controllerOf(fighterIndex) != c.playerId) {
            c.send(Message.of(Protocol.NOTICE, "Ce n'est pas votre tour"));
            return;
        }
        if (!execute(command)) c.send(Message.of(Protocol.NOTICE, "Action impossible"));
    }

    /** Applique et diffuse une commande ; renvoie faux si elle est invalide. */
    private boolean execute(Command command) {
        Fighter before = battle.active();
        if (!battle.apply(command)) return false;
        history.add(command);
        hashes.add(battle.stateHash());
        broadcast(Message.of(Protocol.CMD, history.size() - 1, command.encode(), battle.stateHash()));
        if (battle.isOver()) {
            endMatch();
            return true;
        }
        if (battle.active() != before || command instanceof Command.EndTurn) {
            aiActions = 0;
            beginTurnClock();
        }
        scheduleAiIfNeeded();
        return true;
    }

    private void endMatch() {
        Team w = battle.winner();
        log("Combat terminé : victoire de l'équipe " + w.displayName());
        systemChat("Victoire de l'équipe " + w.displayName() + " !");
        lobby.inMatch = false;
        turnDeadline = 0;
        matchToken++;
        broadcastLobby();
    }

    /** Joueur qui contrôle le combattant (-1 = ordinateur : place libre ou joueur déconnecté). */
    private int controllerOf(int fighterIndex) {
        int owner = lobby.slotOwner[fighterSlot[fighterIndex]];
        LobbyState.Player p = lobby.player(owner);
        return p != null && p.connected() ? owner : -1;
    }

    private void beginTurnClock() {
        turnDeadline = lobby.turnSeconds > 0 ? System.currentTimeMillis() + lobby.turnSeconds * 1000L + TIMER_GRACE_MS : 0;
        for (Client c : clients) sendTimer(c);
    }

    private void sendTimer(Client c) {
        if (!lobby.inMatch) return;
        // la marge de grâce n'est pas annoncée : le joueur voit exactement la durée choisie dans le salon
        long left = turnDeadline == 0 ? 0 : Math.max(1, (turnDeadline - TIMER_GRACE_MS - System.currentTimeMillis() + 999) / 1000);
        c.send(Message.of(Protocol.TIMER, left));
    }

    private void scheduleAiIfNeeded() {
        if (!lobby.inMatch) return;
        int fighterIndex = battle.fighters().indexOf(battle.active());
        if (controllerOf(fighterIndex) >= 0) return;
        int token = matchToken;
        int expected = history.size();
        logic.schedule(() -> {
            if (token != matchToken || !lobby.inMatch || history.size() != expected) return;
            if (controllerOf(battle.fighters().indexOf(battle.active())) >= 0) return;
            AiAction a = ai.decide(battle);
            Command cmd = aiActions >= MAX_AI_ACTIONS ? new Command.EndTurn() : a.toCommand(battle);
            aiActions++;
            if (!execute(cmd)) execute(new Command.EndTurn());
        }, AI_STEP_MS, TimeUnit.MILLISECONDS);
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
            if (lobby.inMatch && turnDeadline > 0 && now > turnDeadline) {
                Fighter f = battle.active();
                if (controllerOf(battle.fighters().indexOf(f)) >= 0) {
                    systemChat("Temps écoulé pour " + f.name() + " : fin du tour.");
                    execute(new Command.EndTurn());
                }
            }
            if (!lobby.inMatch) {
                boolean changed = false;
                for (Client c : new ArrayList<>(clients)) {
                    if (!c.alive && c.playerId >= 0 && now - c.disconnectedAt > LOBBY_GRACE_MS) {
                        removePlayer(c);
                        changed = true;
                    }
                }
                if (changed) broadcastLobby();
            }
        } catch (RuntimeException e) {
            log("Erreur interne : " + e);
        }
    }

    private void removePlayer(Client c) {
        clients.remove(c);
        lobby.players.removeIf(p -> p.id() == c.playerId);
        for (int s = 0; s < LobbyState.SLOTS; s++) if (lobby.slotOwner[s] == c.playerId) lobby.slotOwner[s] = -1;
        if (lobby.chefId == c.playerId) electChef();
        log(c.name + " a quitté le salon");
    }

    // ------------------------------------------------------------------ outils

    private Client byName(String name) {
        for (Client c : clients) if (name.equalsIgnoreCase(c.name)) return c;
        return null;
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
