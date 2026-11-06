package com.minidofus.model.map;

import com.minidofus.model.Board;
import com.minidofus.model.Cell;
import com.minidofus.model.Pathfinder;
import com.minidofus.model.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Génère une arène reproductible à partir d'une graine. Garanties : tous les points d'apparition
 * sont reliés par des chemins praticables (même si toutes les caisses restaient intactes), les zones
 * de départ sont dégagées, l'eau est infranchissable et aucun grand arbre ne masque le terrain de jeu.
 */
public final class MapGenerator {

    public static final int SIZE = 15;
    private static final int MAX_ATTEMPTS = 100;
    private static final int SPAWN_CLEARANCE = 2;
    /** Profondeur (x + y) minimale des arbres : en deçà, ils seraient dessinés au-dessus de l'horizon. */
    private static final double MIN_TREE_DEPTH = SIZE - 2;
    private static final int[] PLAIN_GRASS = {0, 1, 3, 4};
    private static final int[] DECORATED_GRASS = {2, 5};

    private MapGenerator() { }

    public static Arena generate(MapTheme theme, long seed) {
        Random rng = new Random(seed);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Arena arena = build(theme, seed, rng, true);
            if (arena != null) return arena;
        }
        return build(theme, seed, rng, false); // repli : arène sans obstacles, toujours valide
    }

    /** Points d'apparition : un coin de la carte par équipe, trois places chacun. */
    public static final List<Cell> RED_SPAWNS = List.of(new Cell(1, 1), new Cell(3, 1), new Cell(1, 3));
    public static final List<Cell> BLUE_SPAWNS = List.of(new Cell(SIZE - 2, SIZE - 2), new Cell(SIZE - 4, SIZE - 2),
            new Cell(SIZE - 2, SIZE - 4));

    private static Arena build(MapTheme theme, long seed, Random rng, boolean withObstacles) {
        Board board = new Board(SIZE);
        Cell spawnA = RED_SPAWNS.get(0);
        Cell spawnB = BLUE_SPAWNS.get(0);

        paintGround(board, rng);
        if (withObstacles) {
            if (theme.feature() == MapTheme.Feature.STONE_PATH) paintStonePath(board, rng, spawnA, spawnB);
            if (theme.feature() == MapTheme.Feature.LAKE) paintLake(board, rng, spawnA, spawnB);
        }

        List<DecorPlacement> decor = new ArrayList<>();
        if (withObstacles) {
            placeObstacles(theme, board, rng, spawnA, spawnB, decor);
            if (!allSpawnsConnected(board)) return null;
        }
        placeFlowers(theme, board, rng, decor);
        placeTrees(theme, rng, decor);
        return new Arena(theme, seed, board, List.copyOf(decor), RED_SPAWNS, BLUE_SPAWNS);
    }

    // ------------------------------------------------------------------ sol

    private static void paintGround(Board board, Random rng) {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                int[] pool = rng.nextInt(10) < 8 ? PLAIN_GRASS : DECORATED_GRASS;
                board.setVariant(x, y, pool[rng.nextInt(pool.length)]);
            }
        }
    }

    /** Chemin de pierre sinueux d'un point d'apparition à l'autre (marche monotone, parfois large de 2). */
    private static void paintStonePath(Board board, Random rng, Cell from, Cell to) {
        int x = from.x();
        int y = from.y();
        while (x != to.x() || y != to.y()) {
            board.setTerrain(x, y, Terrain.STONE);
            if (rng.nextInt(100) < 35) {
                int wx = x + (rng.nextBoolean() ? 1 : 0);
                int wy = y + (wx == x ? 1 : 0);
                if (board.inBounds(wx, wy)) board.setTerrain(wx, wy, Terrain.STONE);
            }
            boolean moveX = y == to.y() || (x != to.x() && rng.nextBoolean());
            if (moveX) x++; else y++;
        }
        board.setTerrain(x, y, Terrain.STONE);
    }

    /** Étang irrégulier : croissance aléatoire d'une tache d'eau loin des points d'apparition. */
    private static void paintLake(Board board, Random rng, Cell a, Cell b) {
        List<Cell> candidates = new ArrayList<>();
        for (int x = 2; x < SIZE - 2; x++) {
            for (int y = 2; y < SIZE - 2; y++) {
                Cell c = new Cell(x, y);
                if (c.distanceTo(a) >= 6 && c.distanceTo(b) >= 6) candidates.add(c);
            }
        }
        if (candidates.isEmpty()) return;
        Cell seedCell = candidates.get(rng.nextInt(candidates.size()));
        List<Cell> water = new ArrayList<>();
        water.add(seedCell);
        board.setTerrain(seedCell.x(), seedCell.y(), Terrain.WATER);
        int target = 14 + rng.nextInt(9);
        for (int guard = 0; guard < 400 && water.size() < target; guard++) {
            Cell base = water.get(rng.nextInt(water.size()));
            int[] d = Cell.ORTHOGONAL[rng.nextInt(4)];
            Cell next = base.plus(d[0], d[1]);
            if (!board.inBounds(next) || nearSpawn(next, SPAWN_CLEARANCE + 2)) continue;
            if (board.terrain(next.x(), next.y()) == Terrain.WATER) continue;
            board.setTerrain(next.x(), next.y(), Terrain.WATER);
            water.add(next);
        }
    }

    // ------------------------------------------------------------------ obstacles, fleurs, arbres

    private static void placeObstacles(MapTheme theme, Board board, Random rng, Cell a, Cell b, List<DecorPlacement> out) {
        int placed = 0;
        for (int attempts = 0; placed < theme.obstacles() && attempts < 400; attempts++) {
            int x = 1 + rng.nextInt(SIZE - 2);
            int y = 1 + rng.nextInt(SIZE - 2);
            Cell c = new Cell(x, y);
            if (nearSpawn(c, SPAWN_CLEARANCE)) continue;
            if (!board.isWalkable(x, y)) continue; // déjà un obstacle ou de l'eau
            DecorKind kind = theme.obstacleKinds().get(rng.nextInt(theme.obstacleKinds().size()));
            board.setObstacle(x, y, true, kind.durability());
            out.add(new DecorPlacement(kind, x, y, 0.92 + rng.nextDouble() * 0.16, rng.nextBoolean()));
            placed++;
        }
    }

    private static void placeFlowers(MapTheme theme, Board board, Random rng, List<DecorPlacement> out) {
        DecorKind[] kinds = {DecorKind.FLOWERS_PURPLE, DecorKind.FLOWERS_RED, DecorKind.FLOWERS_WHITE,
                DecorKind.FLOWERS_YELLOW, DecorKind.FLOWERS_BLUE};
        for (int i = 0; i < theme.flowers(); i++) {
            int x = rng.nextInt(SIZE);
            int y = rng.nextInt(SIZE);
            if (!board.isWalkable(x, y)) continue;
            out.add(new DecorPlacement(kinds[rng.nextInt(kinds.length)],
                    x + (rng.nextDouble() - 0.5) * 0.5, y + (rng.nextDouble() - 0.5) * 0.5,
                    0.9 + rng.nextDouble() * 0.25, rng.nextBoolean()));
        }
    }

    /**
     * Arbres sur les côtés et au premier plan de l'arène (jamais derrière : ils flotteraient dans le ciel),
     * hors de l'emprise visuelle du terrain de jeu.
     */
    private static void placeTrees(MapTheme theme, Random rng, List<DecorPlacement> out) {
        DecorKind[] kinds = {DecorKind.TREE_OAK_1, DecorKind.TREE_OAK_2, DecorKind.TREE_OAK_3, DecorKind.TREE_DECIDUOUS,
                DecorKind.TREE_PINE_1, DecorKind.TREE_PINE_2, DecorKind.TREE_PINE_3, DecorKind.BUSH};
        List<double[]> placed = new ArrayList<>();
        for (int attempts = 0; placed.size() < theme.trees() && attempts < 3000; attempts++) {
            double gx = -8 + rng.nextDouble() * (SIZE + 15);
            double gy = -8 + rng.nextDouble() * (SIZE + 15);
            if (gx > -1.2 && gx < SIZE + 0.2 && gy > -1.2 && gy < SIZE + 0.2) continue; // trop près du terrain
            if (gx + gy < MIN_TREE_DEPTH) continue;                                     // derrière l'arène
            DecorKind kind = kinds[rng.nextInt(kinds.length)];
            if (occludesArena(gx, gy, kind)) continue;
            if (tooClose(placed, gx, gy, 1.5)) continue;
            placed.add(new double[] {gx, gy});
            out.add(new DecorPlacement(kind, gx, gy, 0.9 + rng.nextDouble() * 0.25, rng.nextBoolean()));
        }
    }

    private static boolean tooClose(List<double[]> placed, double gx, double gy, double min) {
        for (double[] p : placed) {
            if (Math.hypot(p[0] - gx, p[1] - gy) < min) return true;
        }
        return false;
    }

    /**
     * Un objet de grande taille posé en (gx, gy) recouvre à l'écran les cases situées « derrière » lui
     * (x+y plus petit) dans une bande de largeur {@code halfWidth} autour de x-y.
     */
    public static boolean occludesArena(double gx, double gy, DecorKind kind) {
        double depth = gx + gy;
        double lateral = gx - gy;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                double d = x + y;
                if (d <= depth + 0.5 && d >= depth - kind.occlusionRows()
                        && Math.abs((x - y) - lateral) <= kind.halfWidth()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ validation

    /** Les deux cases sont-elles reliées par des cases praticables ? */
    public static boolean connected(Board board, Cell a, Cell b) {
        return Pathfinder.compute(board, a, SIZE * SIZE, c -> false).costTo(b) > 0;
    }

    /** Tous les points d'apparition sont-ils reliés au premier ? */
    public static boolean allSpawnsConnected(Board board) {
        Pathfinder.Reach reach = Pathfinder.compute(board, RED_SPAWNS.get(0), SIZE * SIZE, c -> false);
        for (Cell c : RED_SPAWNS) if (!c.equals(RED_SPAWNS.get(0)) && !reach.canReach(c)) return false;
        for (Cell c : BLUE_SPAWNS) if (!reach.canReach(c)) return false;
        return true;
    }

    private static boolean nearSpawn(Cell c, int distance) {
        for (Cell s : RED_SPAWNS) if (c.distanceTo(s) <= distance) return true;
        for (Cell s : BLUE_SPAWNS) if (c.distanceTo(s) <= distance) return true;
        return false;
    }
}
