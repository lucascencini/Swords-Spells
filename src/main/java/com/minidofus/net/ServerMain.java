package com.minidofus.net;

import java.io.IOException;

/**
 * Serveur seul, sans fenêtre (pour un VPS) :
 * <pre>java -Xmx128m -jar SpellsAndSwords.jar --server [--port 7777] [--password secret]</pre>
 * Le mot de passe peut aussi venir de la variable d'environnement {@code SWORDS_PASSWORD}.
 */
public final class ServerMain {

    private ServerMain() { }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = Protocol.DEFAULT_PORT;
        String password = System.getenv().getOrDefault("SWORDS_PASSWORD", "");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--password" -> password = args[++i];
                case "--server" -> { }
                default -> System.err.println("Option inconnue : " + args[i]);
            }
        }
        GameServer server = new GameServer(port, password, System.out::println);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Thread.currentThread().join(); // le serveur tourne jusqu'à l'arrêt du programme
    }
}
