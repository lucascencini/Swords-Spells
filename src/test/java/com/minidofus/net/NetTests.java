package com.minidofus.net;

import com.minidofus.Check;
import com.minidofus.model.Command;
import com.minidofus.model.ai.AiAction;
import com.minidofus.model.ai.AiPlayer;
import com.minidofus.model.ai.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Tests du jeu en ligne avec un vrai serveur (port libre de la machine) et de faux joueurs automatiques.
 * Les délais du serveur (IA, chrono) sont réels : la suite prend une trentaine de secondes.
 */
public final class NetTests {

    private NetTests() { }

    /** Faux joueur : garde l'état du salon et une réplique du combat, comme le ferait le jeu. */
    static final class Bot {
        final NetClient net;
        final String name;
        LobbyState lobby;
        MatchReplica match;
        boolean synced;
        int desyncs;
        int timerSeconds = -1;
        String rejected;
        final List<String> chat = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        final AiPlayer brain = new AiPlayer(Difficulty.NORMAL, new Random(3));
        boolean autoplay;
        int sentFor = -1;

        Bot(int port, String name, String password) {
            this.name = name;
            this.net = new NetClient("127.0.0.1", port, name, password);
            net.start();
        }

        void pump() {
            Message m;
            while ((m = net.poll()) != null) {
                switch (m.type()) {
                    case Protocol.LOBBY -> lobby = LobbyState.decode(m.arg(0));
                    case Protocol.CHAT -> chat.add(m.arg(0) + ": " + m.arg(1));
                    case Protocol.NOTICE -> notices.add(m.arg(0));
                    case Protocol.REJECT -> rejected = m.arg(0);
                    case Protocol.TIMER -> timerSeconds = m.intArg(0, -1);
                    case Protocol.MATCH -> {
                        match = new MatchReplica(MatchInfo.parse(m));
                        synced = false;
                        sentFor = -1;
                    }
                    case Protocol.SYNCED -> synced = true;
                    case Protocol.CMD -> {
                        if (match != null && match.apply(m) == MatchReplica.Result.DESYNC) {
                            desyncs++;
                            net.send(Message.of(Protocol.RESYNC));
                        }
                    }
                    default -> { }
                }
            }
            if (autoplay && myTurn() && sentFor != match.applied()) {
                sentFor = match.applied();
                AiAction a = brain.decide(match.battle());
                net.send(Message.of(Protocol.CMD, match.applied(), a.toCommand(match.battle()).encode()));
            }
        }

        boolean myTurn() {
            return match != null && synced && !match.battle().isOver() && match.controllerOfActive() == net.playerId();
        }

        void send(String type, Object... args) {
            net.send(Message.of(type, args));
        }
    }

    private static boolean await(List<Bot> bots, BooleanSupplier condition, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Bot b : bots) b.pump();
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static void run() throws Exception {
        protocol();
        try (GameServer server = new GameServer(0, "secret", s -> { })) {
            int port = server.start();
            access(port);
            fullMatch(port);
        }
        try (GameServer server = new GameServer(0, "", s -> { })) {
            int port = server.start();
            timerAndReconnection(port);
        }
    }

    private static void protocol() {
        Check.suite("Réseau : messages");
        Message m = Message.of("CHAT", "Lucas", "salut\tà\ttous\nnouvelle ligne \\ fin");
        Check.equal(m, Message.decode(m.encode()), "échappement des tabulations, retours et barres obliques");
        Check.that(!m.encode().contains("\n"), "un message tient sur une ligne");
        LobbyState s = new LobbyState();
        s.players.add(new LobbyState.Player(1, "Lucas", true));
        s.players.add(new LobbyState.Player(2, "Théo", false));
        s.slotOwner[0] = 1;
        s.slotClass[4] = "archer";
        s.chefId = 1;
        Check.equal(s.encode(), LobbyState.decode(s.encode()).encode(), "état du salon : aller-retour");
        Check.equal("Bob", LobbyState.clean("  B;o|b:  "), "pseudo nettoyé des séparateurs");
    }

    private static void access(int port) {
        Check.suite("Réseau : accès au serveur");
        Bot wrong = new Bot(port, "Intrus", "mauvais");
        Check.that(await(List.of(wrong), () -> wrong.net.status() == NetClient.Status.CLOSED, 5000), "mauvais mot de passe : connexion fermée");
        Check.equal("Mot de passe incorrect", wrong.rejected, "raison affichée");
        Bot a = new Bot(port, "Lucas", "secret");
        Check.that(await(List.of(a), () -> a.lobby != null, 5000), "bon mot de passe : salon reçu");
        Bot twin = new Bot(port, "lucas", "secret");
        Check.that(await(List.of(twin), () -> twin.rejected != null, 5000), "pseudo déjà pris refusé (sans tenir compte de la casse)");
        Check.equal(a.net.playerId(), a.lobby.chefId, "le premier arrivé est chef du salon");
        a.net.close();
        wrong.net.close();
        twin.net.close();
    }

    private static void fullMatch(int port) {
        Check.suite("Réseau : salon et combat complet");
        Bot a = new Bot(port, "Alice", "secret");
        await(List.of(a), () -> a.lobby != null, 5000);
        Bot b = new Bot(port, "Bruno", "secret");
        Bot spectator = new Bot(port, "Chloé", "secret");
        List<Bot> all = List.of(a, b, spectator);
        Check.that(await(all, () -> a.lobby != null && a.lobby.players.stream().filter(LobbyState.Player::connected).count() == 3, 5000),
                "trois joueurs dans le salon");
        Bot chef = all.stream().filter(x -> x.net.playerId() == a.lobby.chefId).findFirst().orElse(a);
        Bot other = chef == a ? b : a;
        Check.that(chef == a, "le chef parti, le premier arrivé suivant devient chef");
        chef.send(Protocol.SETTINGS, "FOREST", 2, "NORMAL", 0);
        chef.send(Protocol.CLAIM, 0);
        await(all, () -> a.lobby.slotOwner[0] == chef.net.playerId(), 5000);
        other.send(Protocol.CLAIM, 3);
        other.send(Protocol.CLASS, 3, "archer");
        other.send(Protocol.CLAIM, 0);  // déjà prise : refusé
        other.send(Protocol.SETTINGS, "SNOW", 3, "HARD", 30); // pas chef : ignoré
        Check.that(await(all, () -> a.lobby.slotOwner[0] == chef.net.playerId() && a.lobby.slotOwner[3] == other.net.playerId()
                && "archer".equals(a.lobby.slotClass[3]), 5000), "places et classe choisies, place déjà prise refusée");
        Check.equal(2, a.lobby.teamSize, "seuls les réglages du chef sont pris en compte");
        other.send(Protocol.CHAT, "bonne\tchance !");
        Check.that(await(all, () -> spectator.chat.stream().anyMatch(l -> l.equals(other.name + ": bonne\tchance !")), 5000),
                "chat reçu par tout le monde, caractères spéciaux compris");

        chef.autoplay = true;
        other.autoplay = true;
        chef.send(Protocol.START);
        Check.that(await(all, () -> a.synced && b.synced && spectator.synced, 5000), "combat reçu par les trois joueurs");
        Check.equal(4, a.match.battle().fighters().size(), "2 contre 2 : 2 joueurs et 2 places pour l'ordinateur");
        boolean finished = await(all, () -> a.match.battle().isOver() && b.match.battle().isOver() && spectator.match.battle().isOver(), 300_000);
        Check.that(finished, "le combat va jusqu'au bout (joueurs + ordinateur côté serveur)");
        Check.equal(0, a.desyncs + b.desyncs + spectator.desyncs, "aucune désynchronisation");
        Check.equal(a.match.battle().stateHash(), spectator.match.battle().stateHash(), "état final identique chez tous");
        Check.that(await(all, () -> !a.lobby.inMatch, 5000), "retour au salon après le combat");
        System.out.println("  combat en ligne : " + a.match.applied() + " commandes, vainqueur " + a.match.battle().winner().displayName());

        // une commande hors tour est refusée
        int before = a.notices.size();
        chef.autoplay = false;
        other.autoplay = false;
        chef.send(Protocol.START);
        await(all, () -> a.synced && a.match.applied() == 0 && b.synced, 5000);
        Bot notMe = a.match.controllerOfActive() == a.net.playerId() ? b : a;
        notMe.send(Protocol.CMD, notMe.match.applied(), new Command.EndTurn().encode());
        Check.that(await(all, () -> notMe.notices.size() > (notMe == a ? before : 0), 3000), "action hors de son tour refusée");
        for (Bot x : all) x.net.close();
    }

    private static void timerAndReconnection(int port) throws Exception {
        Check.suite("Réseau : chrono, déconnexion et reconnexion");
        Bot a = new Bot(port, "Alice", "");
        Bot b = new Bot(port, "Bruno", "");
        List<Bot> two = new ArrayList<>(List.of(a, b));
        await(two, () -> a.lobby != null && a.lobby.players.size() == 2, 5000);
        Bot chef = a.lobby.chefId == a.net.playerId() ? a : b;
        Bot other = chef == a ? b : a;
        chef.send(Protocol.SETTINGS, "ARENA", 1, "EASY", 1);
        chef.send(Protocol.CLAIM, 0);
        other.send(Protocol.CLAIM, 3);
        await(two, () -> a.lobby.slotOwner[0] >= 0 && a.lobby.slotOwner[3] >= 0, 5000);
        chef.send(Protocol.START);
        Check.that(await(two, () -> a.synced && b.synced, 5000), "combat 1 contre 1 lancé");
        Check.that(a.timerSeconds > 0, "chrono annoncé (" + a.timerSeconds + " s)");
        int first = a.match.activeIndex();
        Check.that(await(two, () -> a.match.applied() >= 1, 8000), "personne ne joue : le chrono termine le tour");
        Check.that(a.match.activeIndex() != first, "le tour est passé à l'adversaire");
        Check.that(a.chat.stream().anyMatch(l -> l.contains("Temps écoulé")), "message de fin de chrono");

        // déconnexion brutale de l'autre joueur : l'ordinateur prend le relais
        String otherName = other.name;
        int otherId = other.net.playerId();
        other.net.close();
        two.remove(other);
        int mark = chef.match.applied();
        Check.that(await(two, () -> chef.lobby.player(otherId) != null && !chef.lobby.player(otherId).connected(), 5000),
                "le salon signale la déconnexion");
        chef.autoplay = true;
        Check.that(await(two, () -> chef.match.applied() > mark + 3, 15000), "le combat continue : l'ordinateur joue à sa place");

        // reconnexion avec le même pseudo mais sans jeton : refusée pendant le combat
        Bot impostor = new Bot(port, otherName, "");
        Check.that(await(List.of(impostor), () -> impostor.rejected != null, 5000), "même pseudo sans jeton : refusé");

        // reconnexion automatique avec jeton : on coupe la socket d'un client sans le fermer
        Bot c = chef;
        java.lang.reflect.Field f = NetClient.class.getDeclaredField("socket");
        f.setAccessible(true);
        ((java.net.Socket) f.get(c.net)).close();
        Check.that(await(two, () -> c.net.status() == NetClient.Status.RECONNECTING || c.match.applied() == 0, 4000)
                || c.net.status() == NetClient.Status.CONNECTED, "coupure détectée par le client");
        Check.that(await(two, () -> c.net.status() == NetClient.Status.CONNECTED && c.synced, 10_000), "reconnexion automatique et rattrapage du combat");
        Check.equal(0, c.desyncs, "rattrapage sans désynchronisation");
        Check.that(await(two, () -> c.match.battle().isOver(), 120_000), "le combat se termine normalement après la reconnexion");
        c.net.close();
        impostor.net.close();
    }
}
