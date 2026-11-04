package com.minidofus.model;

/** Coordonnée immutable sur la grille de combat. */
public record Cell(int x, int y) {

    /** Les 4 voisins orthogonaux (dx, dy). */
    public static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public int distanceTo(Cell other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    public Cell plus(int dx, int dy) {
        return new Cell(x + dx, y + dy);
    }

    public boolean isAlignedWith(Cell other) {
        return x == other.x || y == other.y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
