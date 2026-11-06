package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Command;
import com.minidofus.net.GameServer;
import com.minidofus.net.LobbyState;
import com.minidofus.net.MatchInfo;
import com.minidofus.net.Message;
import com.minidofus.net.NetClient;
import com.minidofus.net.Protocol;
import com.minidofus.ui.ChatLog;
import com.minidofus.ui.Hud;
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
        this.lobbyScreen = new LobbyScreen(assets, chat, background, this::send, this::exit);
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
    }

    // ------------------------------------------------------------------ connexion

    private void connect(ConnectScreen.Request r) {
        if (r.hostHere()) {
            try {
                hostedServer = new GameServer(Protocol.DEFAULT_PORT, r.password(), System.out::println);
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
        client = new NetClient(r.host(), r.port(), r.name(), r.password());
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
        screen = lobbyScreen;
    }

    // ------------------------------------------------------------------ messages

    void update() {
        if (client == null) return;
        Message m;
        while ((m = client.poll()) != null) handle(m);
    }

    private void handle(Message m) {
        switch (m.type()) {
            case NetClient.STATUS -> status(NetClient.Status.valueOf(m.arg(0)), m.arg(1));
            case Protocol.LOBBY -> {
                lobby = LobbyState.decode(m.arg(0));
                lobbyScreen.setState(lobby, client.playerId());
                lobbyScreen.setInfo("");
                if (screen == connectScreen) screen = lobbyScreen;
            }
            case Protocol.CHAT -> chat.add(m.arg(0), m.arg(1));
            case Protocol.NOTICE -> {
                chat.add("", m.arg(0));
                if (session != null) session.rejected();
            }
            case Protocol.MATCH -> {
                session = new GameSession(MatchInfo.parse(m), this, assets, poses, sound, hud, focus);
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
