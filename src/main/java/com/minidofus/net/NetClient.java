package com.minidofus.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connexion d'un joueur au serveur. Les messages reçus sont mis en file ({@link #poll}) et traités par la
 * boucle du jeu ; l'envoi est possible depuis n'importe quel fil. En cas de coupure après l'accueil du
 * serveur, le client se reconnecte tout seul (avec son jeton) pendant {@link #RECONNECT_WINDOW_MS}.
 * Les changements d'état de la connexion arrivent aussi dans la file, sous le type {@link #STATUS}.
 */
public final class NetClient implements AutoCloseable {

    /** Message local (jamais envoyé sur le réseau) : arg 0 = {@link Status}, arg 1 = détail. */
    public static final String STATUS = "_STATUS";
    static final long PING_MS = 5_000;
    static final long RECONNECT_DELAY_MS = 3_000;
    static final long RECONNECT_WINDOW_MS = 120_000;
    private static final int CONNECT_TIMEOUT_MS = 6_000;

    public enum Status { CONNECTING, CONNECTED, RECONNECTING, CLOSED }

    private final String host;
    private final int port;
    private final String name;
    private final String password;
    private final ConcurrentLinkedQueue<Message> inbox = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "client-reseau");
        t.setDaemon(true);
        return t;
    });
    private volatile Socket socket;
    private volatile BufferedWriter out;
    private volatile String token = "";
    private volatile int playerId = -1;
    private volatile boolean closed;
    private volatile Status status = Status.CONNECTING;
    private volatile long lostAt;

    /** Clé de compte (dérivée du mot de passe, vide = invité) et création de compte demandée. */
    private final String accountKey;
    /** « 1 » : créer le compte ; « 2 » : le créer s'il n'existe pas ; vide : connexion. */
    private final String register;

    public NetClient(String host, int port, String name, String password) {
        this(host, port, name, password, "", false);
    }

    public NetClient(String host, int port, String name, String password, String accountKey, boolean register) {
        this(host, port, name, password, accountKey, register ? "1" : "");
    }

    public NetClient(String host, int port, String name, String password, String accountKey, String register) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.password = password == null ? "" : password;
        this.accountKey = accountKey == null ? "" : accountKey;
        this.register = register == null ? "" : register;
    }

    /** Lance la connexion en arrière-plan. */
    public void start() {
        timer.execute(this::connect);
        timer.scheduleAtFixedRate(() -> send(Message.of(Protocol.PING)), PING_MS, PING_MS, TimeUnit.MILLISECONDS);
    }

    public Message poll() {
        return inbox.poll();
    }

    public Status status() { return status; }
    public int playerId() { return playerId; }
    public String name() { return name; }

    public void send(Message m) {
        BufferedWriter w = out;
        if (w == null) return;
        try {
            synchronized (this) {
                w.write(m.encode());
                w.write('\n');
                w.flush();
            }
        } catch (IOException e) {
            // la coupure sera détectée par le fil de lecture
        }
    }

    @Override
    public void close() {
        closed = true;
        setStatus(Status.CLOSED, "Déconnecté");
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // déjà fermée
        }
        timer.shutdownNow();
    }

    // ------------------------------------------------------------------ connexion

    private void connect() {
        if (closed) return;
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            socket = s;
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            // une reconnexion (jeton connu) ne recrée jamais le compte
            send(Message.of(Protocol.HELLO, Protocol.VERSION, name, password, token, accountKey,
                    token.isEmpty() ? register : ""));
            Thread reader = new Thread(() -> readLoop(s), "client-lecture");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            connectionLost(token.isEmpty() ? "Serveur injoignable (" + host + ":" + port + ") : " + reason(e) : reason(e));
        }
    }

    private void readLoop(Socket s) {
        String rejection = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;
                Message m = Message.decode(line);
                switch (m.type()) {
                    case Protocol.WELCOME -> {
                        playerId = m.intArg(0, -1);
                        token = m.arg(1);
                        lostAt = 0;
                        setStatus(Status.CONNECTED, "");
                    }
                    case Protocol.REJECT -> rejection = m.arg(0);
                    case Protocol.PONG -> { }
                    default -> { }
                }
                if (!Protocol.PONG.equals(m.type())) inbox.add(m);
            }
        } catch (IOException ignored) {
            // coupure
        }
        out = null;
        if (rejection != null) {
            closed = true;
            setStatus(Status.CLOSED, rejection);
            timer.shutdown();
        } else if (!closed && socket == s) {
            connectionLost("Connexion perdue");
        }
    }

    private void connectionLost(String reason) {
        if (closed) return;
        if (token.isEmpty()) { // jamais accueilli : échec de connexion, pas de reconnexion
            closed = true;
            setStatus(Status.CLOSED, reason);
            timer.shutdown();
            return;
        }
        if (lostAt == 0) lostAt = System.currentTimeMillis();
        if (System.currentTimeMillis() - lostAt > RECONNECT_WINDOW_MS) {
            closed = true;
            setStatus(Status.CLOSED, "Impossible de se reconnecter au serveur");
            timer.shutdown();
            return;
        }
        setStatus(Status.RECONNECTING, reason);
        timer.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void setStatus(Status s, String detail) {
        if (status == s && s != Status.RECONNECTING) return;
        status = s;
        inbox.add(Message.of(STATUS, s.name(), detail));
    }

    private static String reason(IOException e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
