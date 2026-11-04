package com.minidofus.model;

/** Nature du sol d'une case. L'eau est infranchissable (elle était traversable dans le prototype). */
public enum Terrain {
    GRASS(true),
    STONE(true),
    WATER(false);

    private final boolean walkable;

    Terrain(boolean walkable) {
        this.walkable = walkable;
    }

    public boolean isWalkable() {
        return walkable;
    }
}
