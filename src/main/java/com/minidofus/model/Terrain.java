package com.minidofus.model;

/** Nature du sol d'une case. L'eau est infranchissable ; la glace se traverse comme le sol ordinaire. */
public enum Terrain {
    /** Sol ordinaire du biome (herbe, neige, sable, dalles). */
    GRASS(true),
    /** Chemin pavé. */
    STONE(true),
    WATER(false),
    /** Lac gelé : décoratif, praticable. */
    ICE(true);

    private final boolean walkable;

    Terrain(boolean walkable) {
        this.walkable = walkable;
    }

    public boolean isWalkable() {
        return walkable;
    }
}
