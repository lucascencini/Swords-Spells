package com.minidofus.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Parcours en largeur sur la grille avec des tableaux d'entiers (pas de String, pas de {@code shift()}). */
public final class Pathfinder {

    private Pathfinder() { }

    /** Résultat d'un parcours : coût et prédécesseur de chaque case atteignable. */
    public static final class Reach {
        private final Board board;
        private final Cell origin;
        private final int[] cost;
        private final int[] previous;

        private Reach(Board board, Cell origin, int[] cost, int[] previous) {
            this.board = board;
            this.origin = origin;
            this.cost = cost;
            this.previous = previous;
        }

        /** Coût (en PM) pour atteindre la case, 0 pour l'origine, -1 si inatteignable. */
        public int costTo(Cell cell) {
            return board.inBounds(cell) ? cost[board.index(cell)] : -1;
        }

        public boolean canReach(Cell cell) {
            return costTo(cell) > 0;
        }

        /** Chemin de la première case jusqu'à {@code target} (origine exclue). Vide si inatteignable. */
        public List<Cell> pathTo(Cell target) {
            if (!canReach(target)) return List.of();
            List<Cell> path = new ArrayList<>();
            int i = board.index(target);
            int originIndex = board.index(origin);
            while (i != originIndex) {
                path.add(board.cellAt(i));
                i = previous[i];
            }
            Collections.reverse(path);
            return path;
        }

        /** Toutes les cases atteignables, origine exclue. */
        public List<Cell> cells() {
            List<Cell> result = new ArrayList<>();
            for (int i = 0; i < cost.length; i++) {
                if (cost[i] > 0) result.add(board.cellAt(i));
            }
            return result;
        }
    }

    /**
     * @param occupied cases bloquées en plus du terrain (autres combattants)
     */
    public static Reach compute(Board board, Cell origin, int maxCost, Predicate<Cell> occupied) {
        int n = board.size() * board.size();
        int[] cost = new int[n];
        int[] previous = new int[n];
        Arrays.fill(cost, -1);
        Arrays.fill(previous, -1);
        int[] queue = new int[n];
        int head = 0;
        int tail = 0;
        int start = board.index(origin);
        cost[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int current = queue[head++];
            if (cost[current] >= maxCost) continue;
            Cell cell = board.cellAt(current);
            for (int[] d : Cell.ORTHOGONAL) {
                int nx = cell.x() + d[0];
                int ny = cell.y() + d[1];
                if (!board.isWalkable(nx, ny)) continue;
                int next = board.index(nx, ny);
                if (cost[next] >= 0) continue;
                if (occupied.test(new Cell(nx, ny))) continue;
                cost[next] = cost[current] + 1;
                previous[next] = current;
                queue[tail++] = next;
            }
        }
        return new Reach(board, origin, cost, previous);
    }
}
