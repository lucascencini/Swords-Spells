package com.minidofus.net;

import java.io.IOException;

/**
 * Serveur seul, sans fenêtre (pour un VPS) :
 * <pre>java -Xmx128m -jar SpellsAndSwords.jar --server [--port 7777] [--password secret] [--data dossier]</pre>
 * Le mot de passe peut aussi venir de la variable d'environnement {@code SWORDS_PASSWORD}, le dossier de la base
 * des comptes de {@code SWORDS_DATA} (par défaut {@code ./data}).
 */
public final class ServerMain {

    private ServerMain() { }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = Protocol.DEFAULT_PORT;
        String password = System.getenv().getOrDefault("SWORDS_PASSWORD", "");
        String data = System.getenv().getOrDefault("SWORDS_DATA", "data");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--password" -> password = args[++i];
                case "--data" -> data = args[++i];
                case "--server" -> { }
                default -> System.err.println("Option inconnue : " + args[i]);
            }
        }
        GameServer server = new GameServer(port, password, java.nio.file.Path.of(data), System.out::println);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Thread.currentThread().join(); // le serveur tourne jusqu'à l'arrêt du programme
    }
}
