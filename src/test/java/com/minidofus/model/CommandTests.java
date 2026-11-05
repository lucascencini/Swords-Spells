package com.minidofus.model;

import com.minidofus.Check;
import com.minidofus.model.ai.AiAction;
import com.minidofus.model.ai.AiPlayer;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Commandes réseau : encodage, et surtout déterminisme (base du jeu en ligne). */
public final class CommandTests {

    private CommandTests() { }

    public static void run() {
        encoding();
        determinism();
    }

    private static void encoding() {
        Check.suite("Commandes : encodage");
        for (Command c : List.of(new Command.Move(3, 14), new Command.Cast(4, 0, 9), new Command.EndTurn())) {
            Check.equal(c, Command.decode(c.encode()), "aller-retour " + c.encode());
        }
        Check.that(Command.decode("X 1") == null, "type inconnu refusé");
        Check.that(Command.decode("M 1") == null, "arguments manquants refusés");
        Check.that(Command.decode("C a b c") == null, "nombres invalides refusés");
        Check.that(Command.decode("") == null, "texte vide refusé");
    }

    /** Combat identique sur deux machines : même graine. */
    static Battle create(MapTheme theme, long seed, List<FighterClass> red, List<FighterClass> blue) {
        Arena arena = MapGenerator.generate(theme, seed);
        List<Fighter> fighters = new ArrayList<>();
        for (int i = 0; i < red.size(); i++) fighters.add(new Fighter(red.get(i), Team.RED, i, arena.redSpawns().get(i)));
        for (int i = 0; i < blue.size(); i++) fighters.add(new Fighter(blue.get(i), Team.BLUE, i, arena.blueSpawns().get(i)));
        Battle b = new Battle(arena.board(), fighters, new Random(seed));
        b.start();
        return b;
    }

    private static void determinism() {
        Check.suite("Commandes : déterminisme (serveur et clients)");
        List<FighterClass> red = List.of(FighterCatalog.WARRIOR, FighterCatalog.ARCHER, FighterCatalog.MAGE);
        List<FighterClass> blue = List.of(FighterCatalog.MAGE, FighterCatalog.WARRIOR, FighterCatalog.ARCHER);
        int games = 0;
        int identical = 0;
        int commands = 0;
        for (MapTheme theme : MapTheme.values()) {
            long seed = 900 + theme.ordinal();
            Battle server = create(theme, seed, red, blue);
            Battle client = create(theme, seed, red, blue);
            AiPlayer ai = new AiPlayer(Difficulty.NORMAL, new Random(seed));
            boolean same = server.stateHash() == client.stateHash();
            int actions = 0;
            while (!server.isOver() && server.round() <= 60 && same) {
                AiAction a = ai.decide(server);
                Command c = actions >= 14 ? new Command.EndTurn() : a.toCommand(server);
                // le client reçoit la commande sous forme de texte, comme par le réseau
                Command received = Command.decode(c.encode());
                boolean ok = server.apply(c);
                if (!ok) {
                    c = new Command.EndTurn();
                    server.apply(c);
                    received = c;
                }
                client.apply(received);
                commands++;
                actions = c instanceof Command.EndTurn ? 0 : actions + 1;
                same = server.stateHash() == client.stateHash();
            }
            games++;
            if (same && server.isOver() && client.isOver() && server.winner() == client.winner()) identical++;
        }
        Check.equal(games, identical, "6 parties 3c3 rejouées à l'identique côté client (" + commands + " commandes)");
        Battle a = create(MapTheme.FOREST, 1, red, blue);
        Battle b = create(MapTheme.FOREST, 2, red, blue);
        Check.that(a.stateHash() != b.stateHash(), "graines différentes : empreintes différentes");
        Battle c = create(MapTheme.FOREST, 1, red, blue);
        Check.that(!c.apply(new Command.Cast(9, 0, 0)), "rang de sort inconnu refusé");
        Check.that(!c.apply(new Command.Move(-5, 40)), "case hors grille refusée");
        Check.equal(a.stateHash(), c.stateHash(), "une commande refusée ne change rien");
    }
}
