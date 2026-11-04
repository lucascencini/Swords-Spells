package com.minidofus.model;

import com.minidofus.Check;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.DecorKind;
import com.minidofus.model.map.DecorPlacement;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;

/** Tests de la génération de carte sur de nombreuses graines. */
public final class MapTests {

    private MapTests() { }

    public static void run() {
        Check.suite("Génération de cartes (3 ambiances x 300 graines)");
        int lakeWithWater = 0;
        int pathWithStone = 0;
        int problems = 0;
        for (MapTheme theme : MapTheme.values()) {
            for (long seed = 0; seed < 300; seed++) {
                Arena a = MapGenerator.generate(theme, seed);
                Board board = a.board();
                String tag = theme + "/" + seed;
                if (!MapGenerator.connected(board, a.spawnA(), a.spawnB())) { problems++; Check.that(false, tag + " : spawns non reliés"); }
                if (!board.isWalkable(a.spawnA()) || !board.isWalkable(a.spawnB())) { problems++; Check.that(false, tag + " : spawn non praticable"); }

                int obstacles = 0;
                int water = 0;
                int stone = 0;
                for (int x = 0; x < board.size(); x++) {
                    for (int y = 0; y < board.size(); y++) {
                        Cell c = new Cell(x, y);
                        if (board.hasObstacle(x, y)) {
                            obstacles++;
                            if (c.distanceTo(a.spawnA()) <= 2 || c.distanceTo(a.spawnB()) <= 2) { problems++; Check.that(false, tag + " : obstacle près d'un spawn"); }
                        }
                        if (board.terrain(x, y) == Terrain.WATER) water++;
                        if (board.terrain(x, y) == Terrain.STONE) stone++;
                    }
                }
                long blockingDecor = a.decor().stream().filter(d -> d.kind().isBlocking()).count();
                if (blockingDecor != obstacles) { problems++; Check.that(false, tag + " : décor bloquant != obstacles"); }
                if (theme == MapTheme.LAKE && water >= 10) lakeWithWater++;
                if (theme == MapTheme.RUINS && stone >= 20) pathWithStone++;
                if (theme != MapTheme.LAKE && water != 0) { problems++; Check.that(false, tag + " : eau hors ambiance lac"); }

                for (DecorPlacement d : a.decor()) {
                    if (d.kind().role() == DecorKind.Role.TREE) {
                        if (d.x() > -1.2 && d.x() < 15.2 && d.y() > -1.2 && d.y() < 15.2) { problems++; Check.that(false, tag + " : arbre sur le terrain"); }
                        if (MapGenerator.occludesArena(d.x(), d.y(), d.kind())) { problems++; Check.that(false, tag + " : arbre qui masque le terrain"); }
                    }
                    if (d.kind().role() == DecorKind.Role.FLOWER && !board.isWalkable((int) Math.round(d.x()), (int) Math.round(d.y()))) {
                        problems++;
                        Check.that(false, tag + " : fleur sur obstacle/eau");
                    }
                }
            }
        }
        Check.equal(300, lakeWithWater, "chaque carte « lac » contient un étang d'au moins 10 cases");
        Check.equal(300, pathWithStone, "chaque carte « ruines » a un chemin de pierre");
        Check.equal(0, problems, "toutes les cartes générées sont valides");

        Check.suite("Reproductibilité des cartes");
        Arena a1 = MapGenerator.generate(MapTheme.FOREST, 42);
        Arena a2 = MapGenerator.generate(MapTheme.FOREST, 42);
        Check.equal(a1.decor(), a2.decor(), "même graine : même décor");
        boolean same = true;
        for (int i = 0; i < 225; i++) {
            Cell c = a1.board().cellAt(i);
            same &= a1.board().hasObstacle(c.x(), c.y()) == a2.board().hasObstacle(c.x(), c.y());
            same &= a1.board().variant(c.x(), c.y()) == a2.board().variant(c.x(), c.y());
        }
        Check.that(same, "même graine : même terrain");
        Check.that(!a1.decor().equals(MapGenerator.generate(MapTheme.FOREST, 43).decor()), "graines différentes : cartes différentes");
        long trees = a1.decor().stream().filter(d -> d.kind().role() == DecorKind.Role.TREE).count();
        Check.equal((long) MapTheme.FOREST.trees(), trees, "nombre d'arbres demandé atteint");
    }
}
