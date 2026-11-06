package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Command;
import com.minidofus.server.AccountStore;
import com.minidofus.net.GameServer;
import com.minidofus.net.LobbyState;
import com.minidofus.net.MatchInfo;
import com.minidofus.net.Message;
import com.minidofus.net.NetClient;
import com.minidofus.net.Protocol;
import com.minidofus.ui.ChatLog;
import com.minidofus.ui.Hud;
import com.minidofus.ui.account.AccountView;
import com.minidofus.ui.account.CreateHeroScreen;
import com.minidofus.ui.account.HubScreen;
import com.minidofus.ui.account.SoloScreen;
import com.minidofus.ui.account.SpellbookScreen;
import com.minidofus.ui.account.TalentScreen;
import com.minidofus.util.Json;
import com.minidofus.ui.menu.ConnectScreen;
import com.minidofus.ui.menu.LobbyScreen;
import com.minidofus.ui.menu.Screen;
import com.minidofus.view.PoseTable;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Partie en ligne côté joueur : connexion, salon, combat. Traite les messages du serveur dans la boucle du
 * jeu et indique à la fenêtre quoi afficher ({@link #screen()} ou {@link #session()}). Peut aussi faire
 * tourner le serveur sur ce PC (« Héberger »).
 */
final class OnlineController implements GameSession.OnlineLink {

    private final Assets assets;
    private final PoseTable poses;
    private final SoundPlayer sound;
    private final Hud hud;
    private final Consumer<Point2D.Double> focus;
    private final Runnable onExit;
    private final ChatLog chat = new ChatLog();
    private final ConnectScreen connectScreen;
    private final LobbyScreen lobbyScreen;
    private final HubScreen hubScreen;
    private final CreateHeroScreen createScreen;
    private final TalentScreen talentScreen;
    private final SpellbookScreen spellbookScreen;
    private final SoloScreen soloScreen;
    private AccountView account;
    private boolean withAccount;
    private boolean inSolo;
    private String pendingSelect;
    private List<String> lastRewards = List.of();
    private NetClient client;
    private GameServer hostedServer;
    private LobbyState lobby = new LobbyState();
    private Screen screen;
    private GameSession session;
    private boolean everConnected;
    private String banner;

    OnlineController(Assets assets, PoseTable poses, SoundPlayer sound, Hud hud, Consumer<Graphics2D> background,
                     Consumer<Point2D.Double> focus, Runnable onExit) {
        this.assets = assets;
        this.poses = poses;
        this.sound = sound;
        this.hud = hud;
        this.focus = focus;
        this.onExit = onExit;
        this.connectScreen = new ConnectScreen(background, this::connect, this::exit);
        this.lobbyScreen = new LobbyScreen(assets, chat, background, this::send, this::leaveLobby);
        this.hubScreen = new HubScreen(assets, background, new HubScreen.Actions() {
            @Override public void create() {
                createScreen.reset();
                screen = createScreen;
            }
            @Override public void talents(AccountView.Hero hero) {
                talentScreen.open(hero);
                screen = talentScreen;
            }
            @Override public void grimoire(AccountView.Hero hero) {
                spellbookScreen.open(hero);
                screen = spellbookScreen;
            }
            @Override public void delete(AccountView.Hero hero) {
                send(Message.of(Protocol.CHAR_DEL, hero.id()));
            }
            @Override public void solo() {
                if (account == null || account.characters().isEmpty()) {
                    hubScreen.setStatus("Créez d'abord un personnage.");
                    return;
                }
                AccountView.Hero h = hubScreen.selected();
                soloScreen.open(account, h == null ? null : h.id());
                screen = soloScreen;
            }
            @Override public void lobby() {
                lobbyScreen.setLeaveLabel("Retour à mes personnages");
                screen = lobbyScreen;
            }
            @Override public void logout() {
                disconnect("Déconnecté.");
            }
        });
        this.createScreen = new CreateHeroScreen(assets, background, (name, classId) -> {
            pendingSelect = name;
            send(Message.of(Protocol.CHAR_NEW, name, classId));
            hubScreen.setStatus("Création de " + name + "…");
            screen = hubScreen;
        }, () -> screen = hubScreen);
        this.talentScreen = new TalentScreen(assets, background, (hero, ranks) -> {
            StringBuilder t = new StringBuilder();
            for (Map.Entry<String, Integer> e : ranks.entrySet()) {
                if (e.getValue() <= 0) continue;
                t.append(t.length() > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue());
            }
            send(Message.of(Protocol.CHAR_SAVE, hero.id(), t.toString(), String.join(",", hero.spells())));
        }, () -> screen = hubScreen);
        this.spellbookScreen = new SpellbookScreen(assets, background, (hero, spells) -> {
            StringBuilder t = new StringBuilder();
            for (Map.Entry<String, Integer> e : hero.talents().entrySet()) {
                t.append(t.length() > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue());
            }
            send(Message.of(Protocol.CHAR_SAVE, hero.id(), t.toString(), String.join(",", spells)));
        }, () -> screen = hubScreen);
        this.soloScreen = new SoloScreen(assets, background, choice -> {
            inSolo = true;
            lastRewards = List.of();
            send(Message.of(Protocol.SOLO, choice.size(), choice.map(), choice.difficulty().name(),
                    String.join(",", choice.characterIds())));
        }, () -> screen = hubScreen);
        this.screen = connectScreen;
    }

    Screen screen() { return screen; }
    GameSession session() { return session; }
    ChatLog chat() { return chat; }

    /** Bandeau d'état de la connexion (reconnexion en cours…), ou {@code null}. */
    String banner() { return banner; }

    void layout(int w, int h) {
        connectScreen.layout(w, h);
        lobbyScreen.layout(w, h);
        hubScreen.layout(w, h);
        createScreen.layout(w, h);
        talentScreen.layout(w, h);
        spellbookScreen.layout(w, h);
        soloScreen.layout(w, h);
    }

    // ------------------------------------------------------------------ connexion

    private void connect(ConnectScreen.Request r) {
        if (r.hostHere()) {
            try {
                // comptes et personnages du serveur hébergé : dans le dossier de l'utilisateur
                java.nio.file.Path data = java.nio.file.Path.of(System.getProperty("user.home"), ".spells-and-swords", "server");
                hostedServer = new GameServer(Protocol.DEFAULT_PORT, r.password(), data, System.out::println);
                hostedServer.start();
            } catch (IOException e) {
                hostedServer = null;
                connectScreen.setStatus("Impossible d'héberger sur le port " + Protocol.DEFAULT_PORT
                        + " (déjà utilisé ?) : " + e.getMessage(), true);
                return;
            }
            lobbyScreen.setServerLabel("Vos amis se connectent à : " + String.join("  ou  ", lanAddresses())
                    + "  (même réseau, ou port " + Protocol.DEFAULT_PORT + " ouvert sur votre box)");
        } else {
            lobbyScreen.setServerLabel("Serveur : " + r.host() + (r.port() == Protocol.DEFAULT_PORT ? "" : ":" + r.port()));
        }
        chat.clear();
        account = null;
        inSolo = false;
        withAccount = r.mode() != ConnectScreen.Mode.GUEST;
        if (withAccount) {
            // le mot de passe du compte ne quitte jamais le PC : seule une clé dérivée est envoyée
            String key = AccountStore.clientKey(r.name(), r.accountPassword());
            String flag = r.mode() == ConnectScreen.Mode.REGISTER ? "1" : r.hostHere() ? "2" : "";
            client = new NetClient(r.host(), r.port(), r.name(), r.password(), key, flag);
        } else {
            client = new NetClient(r.host(), r.port(), r.name(), r.password());
        }
        lobbyScreen.setLeaveLabel(withAccount ? "Retour à mes personnages" : "Quitter le salon");
        client.start();
    }

    /** Adresses locales (IPv4) de ce PC, à communiquer aux amis quand on héberge. */
    private static List<String> lanAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // adresses inconnues
        }
        if (out.isEmpty()) out.add("l'adresse IP de ce PC");
        return out;
    }

    private void send(Message m) {
        if (client != null) client.send(m);
    }

    /** « Quitter le salon » : retour aux personnages avec un compte, sinon sortie du mode en ligne. */
    private void leaveLobby() {
        if (!withAccount) {
            exit();
            return;
        }
        for (int s = 0; s < LobbyState.SLOTS; s++) {
            if (lobby.slotOwner[s] == localPlayerId()) send(Message.of(Protocol.RELEASE, s));
        }
        screen = hubScreen;
    }

    /** Déconnexion du compte : retour à l'écran de connexion (le serveur hébergé s'arrête). */
    private void disconnect(String status) {
        if (client != null) client.close();
        if (hostedServer != null) hostedServer.close();
        client = null;
        hostedServer = null;
        session = null;
        account = null;
        inSolo = false;
        everConnected = false;
        banner = null;
        screen = connectScreen;
        connectScreen.setStatus(status, false);
    }

    /** « Quitter la partie » du menu pause : abandon d'un combat solo (sans expérience), sinon sortie du mode en ligne. */
    void quitMatch() {
        if (inSolo) backToLobby();
        else exit();
    }

    /** Quitte le mode en ligne (et arrête le serveur hébergé). */
    void exit() {
        if (client != null) client.close();
        if (hostedServer != null) hostedServer.close();
        client = null;
        hostedServer = null;
        session = null;
        onExit.run();
    }

    /** Après la fin d'un combat : retour au salon. */
    void backToLobby() {
        session = null;
        if (inSolo) {
            inSolo = false;
            send(Message.of(Protocol.SOLO_QUIT));
            if (!lastRewards.isEmpty()) hubScreen.setStatus(String.join("\n", lastRewards));
            screen = hubScreen;
        } else {
            screen = lobbyScreen;
        }
    }

    // ------------------------------------------------------------------ messages

    void update() {
        if (client == null) return;
        Message m;
        // un message peut fermer la connexion (refus, serveur injoignable) : client redevient null en cours de boucle
        while (client != null && (m = client.poll()) != null) handle(m);
    }

    private void handle(Message m) {
        switch (m.type()) {
            case NetClient.STATUS -> status(NetClient.Status.valueOf(m.arg(0)), m.arg(1));
            case Protocol.LOBBY -> {
                lobby = LobbyState.decode(m.arg(0));
                lobbyScreen.setState(lobby, client.playerId());
                lobbyScreen.setInfo("");
                if (screen == connectScreen && !withAccount) screen = lobbyScreen;
            }
            case Protocol.ACCOUNT -> account(AccountView.parse(m.arg(0)));
            case Protocol.REWARD -> rewards(m.arg(0));
            case Protocol.CHAT -> chat.add(m.arg(0), m.arg(1));
            case Protocol.NOTICE -> {
                chat.add("", m.arg(0));
                if (session != null) session.rejected();
                else if (screen == hubScreen || screen == createScreen) hubScreen.setStatus(m.arg(0));
                else if (screen == talentScreen) talentScreen.flash(m.arg(0));
                else if (screen == spellbookScreen) spellbookScreen.flash(m.arg(0));
                else if (screen == soloScreen) {
                    soloScreen.flash(m.arg(0));
                    inSolo = false;
                }
            }
            case Protocol.MATCH -> {
                session = new GameSession(MatchInfo.parse(m), this, assets, poses, sound, hud, focus);
                session.setBackLabel(inSolo ? "Retour à mes personnages (Échap)" : "Retour au salon (Échap)");
                lastRewards = List.of();
                screen = null;
            }
            case Protocol.CMD -> {
                if (session != null) session.receive(m);
            }
            case Protocol.SYNCED -> {
                if (session != null) session.markSynced();
            }
            case Protocol.TIMER -> {
                if (session != null) session.setTimer(m.intArg(0, 0));
            }
            default -> { }
        }
    }

    private void account(AccountView a) {
        boolean first = account == null;
        account = a;
        hubScreen.setAccount(a);
        if (pendingSelect != null) {
            for (AccountView.Hero h : a.characters()) {
                if (h.name().equalsIgnoreCase(pendingSelect)) {
                    hubScreen.select(h.id());
                    hubScreen.setStatus(h.name() + " vous rejoint !");
                    pendingSelect = null;
                }
            }
        }
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (AccountView.Hero h : a.characters()) {
            ids.add(h.id());
            labels.add(h.name() + " niv. " + h.level());
        }
        lobbyScreen.setCharacters(ids, labels);
        AccountView.Hero sel = hubScreen.selected();
        if (screen == talentScreen && sel != null) talentScreen.refresh(a.hero(sel.id()));
        if (screen == spellbookScreen && sel != null) spellbookScreen.refresh(a.hero(sel.id()));
        if (first && (screen == connectScreen || screen == null && session == null)) {
            screen = hubScreen;
            hubScreen.setStatus("Bienvenue, " + a.name() + " !");
        }
    }

    /** Expérience gagnée par ses personnages à la fin d'un combat. */
    @SuppressWarnings("unchecked")
    private void rewards(String json) {
        List<String> lines = new ArrayList<>();
        for (Object o : (List<Object>) Json.parse(json)) {
            Map<String, Object> r = (Map<String, Object>) o;
            int ups = Json.integer(r.get("levelUps"), 0);
            String line = Json.string(r.get("name"), "?") + " : +" + Json.integer(r.get("xp"), 0) + " XP";
            if (ups > 0) {
                int lvl = Json.integer(r.get("level"), 1);
                line += "  —  niveau " + lvl + " atteint ! (+" + ups + " point" + (ups > 1 ? "s" : "") + " de talent)";
            }
            lines.add(line);
        }
        lastRewards = lines;
        if (session != null) session.setRewards(lines);
        for (String l : lines) chat.add("", l);
    }

    private void status(NetClient.Status s, String detail) {
        switch (s) {
            case CONNECTED -> {
                banner = null;
                if (everConnected) chat.add("", "Reconnecté au serveur.");
                everConnected = true;
            }
            case RECONNECTING -> banner = "Connexion perdue — reconnexion en cours…";
            case CLOSED -> {
                banner = null;
                if (client != null) client.close();
                client = null;
                session = null;
                account = null;
                inSolo = false;
                screen = connectScreen;
                connectScreen.setStatus(everConnected ? "Déconnecté : " + detail : detail, true);
                everConnected = false;
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ GameSession.OnlineLink

    @Override
    public int localPlayerId() {
        return client == null ? -1 : client.playerId();
    }

    @Override
    public String playerName(int playerId) {
        LobbyState.Player p = lobby.player(playerId);
        return p == null ? null : p.name();
    }

    @Override
    public void send(int index, Command command) {
        send(Message.of(Protocol.CMD, index, command.encode()));
    }

    @Override
    public void resync() {
        chat.add("", "Désynchronisation détectée : rechargement du combat…");
        send(Message.of(Protocol.RESYNC));
    }

    void sendChat(String text) {
        if (!text.isBlank()) send(Message.of(Protocol.CHAT, text));
    }
}
