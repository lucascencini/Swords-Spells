package com.minidofus.model.map;

/** Grande famille de décor : détermine le sol, le fond d'écran, les particules et l'ambiance sonore. */
public enum Biome {
    MEADOW(true),
    SNOW(true),
    DESERT(true),
    /** Crépuscule : cendres et braises dans l'air. */
    DUSK(true);

    private final boolean hasSky;

    Biome(boolean hasSky) {
        this.hasSky = hasSky;
    }

    public boolean hasSky() {
        return hasSky;
    }
}
