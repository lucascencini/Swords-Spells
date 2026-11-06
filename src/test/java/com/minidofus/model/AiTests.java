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

/** Tests de l'IA : choix évidents, et parties complètes ordinateur contre ordinateur. */
public final class AiTests {

    private AiTests() { }

    public static void run() {
        obviousChoices();
        fullGames();
        difficultyMatters();
    }

    private static void obviousChoices() {
        Check.suite("IA : choix évidents");
        Board board = new Board(15);
        Fighter w = new Fighter(FighterCatalog.WARRIOR, Team.RED, 0, new Cell(5, 5));
        Fighter m = new Fighter(FighterCatalog.MAGE, Team.BLUE, 0, new Cell(6, 5));
        Battle b = new Battle(board, List.of(w, m), new Random(1));
        b.startWithOrder(List.of(w, m));
        m.takeDamage(50);
        AiAction action = new AiPlayer(Difficulty.NORMAL, new Random(1)).decide(b);
        Check.that(action instanceof AiAction.Cast, "attaque immédiatement (" + action + ")");
        apply(b, action);
        Check.that(b.isOver(), "et achève l'ennemi à 10 PV");

        Board board2 = new Board(15);
        Fighter w2 = new Fighter(FighterCatalog.WARRIOR, Team.RED, 0, new Cell(2, 2));
        Fighter m2 = new Fighter(FighterCatalog.MAGE, Team.BLUE, 0, new Cell(5, 2));
        Battle b2 = new Battle(board2, List.of(w2, m2), new Random(1));
        b2.startWithOrder(List.of(w2, m2));
        AiAction a2 = new AiPlayer(Difficulty.NORMAL, new Random(1)).decide(b2);
        Check.that(!(a2 instanceof AiAction.EndTurn), "un guerrier à 3 cases n'attend pas sans rien faire (" + a2 + ")");

        Board board3 = new Board(15);
        Fighter m3 = new Fighter(FighterCatalog.MAGE, Team.RED, 0, new Cell(5, 5));
        Fighter ally3 = new Fighter(FighterCatalog.WARRIOR, Team.RED, 1, new Cell(5, 4));
        Fighter e3 = new Fighter(FighterCatalog.WARRIOR, Team.BLUE, 0, new Cell(6, 4));
        Battle b3 = new Battle(board3, List.of(m3, ally3, e3), new Random(1));
        b3.startWithOrder(List.of(m3, e3, ally3));
        AiPlayer ai = new AiPlayer(Difficulty.NORMAL, new Random(1));
        for (int i = 0; i < 10 && b3.active() == m3; i++) {
            AiAction a = ai.decide(b3);
            if (a instanceof AiAction.Cast c && c.spell().id().equals("fireball")) {
                Check.that(!c.spell().impactCells(b3, m3, c.target()).contains(ally3.position()),
                        "pas de boule de feu sur son propre allié");
            }
            if (!apply(b3, a)) break;
        }
    }

    /** Applique une action de déplacement ou de sort ; renvoie false si elle est refusée ou si c'est une fin de tour. */
    private static boolean apply(Battle b, AiAction a) {
        if (a instanceof AiAction.Move mv) return b.move(mv.target());
        if (a instanceof AiAction.Cast c) return b.cast(c.spell(), c.target());
        return false;
    }

    /** Joue une partie complète ; renvoie l'équipe gagnante (ou null si nulle après 60 manches). */
    static Team play(MapTheme theme, long seed, List<FighterClass> red, List<FighterClass> blue,
                     Difficulty redLevel, Difficulty blueLevel) {
        Arena arena = MapGenerator.generate(theme, seed);
        List<Fighter> fighters = new ArrayList<>();
        for (int i = 0; i < red.size(); i++) fighters.add(new Fighter(red.get(i), Team.RED, i, arena.redSpawns().get(i)));
        for (int i = 0; i < blue.size(); i++) fighters.add(new Fighter(blue.get(i), Team.BLUE, i, arena.blueSpawns().get(i)));
        Battle b = new Battle(arena.board(), fighters, new Random(seed));
        b.start();
        AiPlayer redAi = new AiPlayer(redLevel, new Random(seed * 31));
        AiPlayer blueAi = new AiPlayer(blueLevel, new Random(seed * 17));
        int actions = 0;
        while (!b.isOver() && b.round() <= 60) {
            AiPlayer ai = b.active().team() == Team.RED ? redAi : blueAi;
            AiAction a = ai.decide(b);
            if (a instanceof AiAction.EndTurn || actions >= 14 || !apply(b, a)) {
                if (!b.isOver()) b.endTurn();
                actions = 0;
            } else {
                actions++;
            }
        }
        return b.winner();
    }

    private static void fullGames() {
        Check.suite("IA : parties complètes");
        List<FighterClass> all = FighterCatalog.ALL;
        int finished = 0;
        int games = 0;
        long t0 = System.nanoTime();
        for (MapTheme theme : MapTheme.values()) {
            for (int size = 1; size <= 3; size++) {
                List<FighterClass> red = new ArrayList<>();
                List<FighterClass> blue = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    red.add(all.get((i + size) % all.size()));
                    blue.add(all.get((i + size + 1) % all.size()));
                }
                games++;
                if (play(theme, 100 + size, red, blue, Difficulty.NORMAL, Difficulty.NORMAL) != null) finished++;
            }
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        Check.equal(games, finished, "toutes les parties (1v1, 2v2, 3v3 sur chaque carte) se terminent");
        System.out.printf(java.util.Locale.ROOT, "  %d parties IA contre IA en %.1f s%n", games, seconds);
    }

    private static void difficultyMatters() {
        Check.suite("IA : le niveau compte");
        int hardWins = 0;
        int games = 12;
        for (int i = 0; i < games; i++) {
            List<FighterClass> team = List.of(FighterCatalog.ALL.get(i % FighterCatalog.ALL.size()),
                    FighterCatalog.ALL.get((i + 1) % FighterCatalog.ALL.size()));
            boolean hardIsRed = i % 2 == 0;
            Team w = play(MapTheme.values()[i % MapTheme.values().length], 500 + i, team, team,
                    hardIsRed ? Difficulty.HARD : Difficulty.EASY, hardIsRed ? Difficulty.EASY : Difficulty.HARD);
            if (w == (hardIsRed ? Team.RED : Team.BLUE)) hardWins++;
        }
        System.out.println("  Difficile bat Facile " + hardWins + " fois sur " + games);
        Check.that(hardWins >= games * 2 / 3, "le niveau difficile bat le niveau facile au moins 2 fois sur 3");
    }
}
