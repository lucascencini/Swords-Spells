package com.minidofus.view;

/** Orientation à l'écran d'un combattant. */
public enum Facing {
    SE, SW, NE, NW;

    /** Orientation correspondant à un déplacement sur la grille (axe dominant). */
    public static Facing of(int dx, int dy, Facing fallback) {
        if (dx == 0 && dy == 0) return fallback;
        if (Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? SE : NW;
        return dy > 0 ? SW : NE;
    }
}
