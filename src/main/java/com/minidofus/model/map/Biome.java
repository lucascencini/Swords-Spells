package com.minidofus.model.map;

/** Grande famille de décor : détermine le sol, le fond d'écran, les particules et l'ambiance sonore. */
public enum Biome {
    MEADOW(true),
    SNOW(true),
    DESERT(true),
    /** Souterrain : pas de ciel, le décor peut donc entourer toute l'arène. */
    DUNGEON(false);

    private final boolean hasSky;

    Biome(boolean hasSky) {
        this.hasSky = hasSky;
    }

    public boolean hasSky() {
        return hasSky;
    }
}
