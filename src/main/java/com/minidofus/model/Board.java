package com.minidofus.model;

import java.util.Arrays;

/**
 * Grille carrée de combat. Les données sont stockées dans des tableaux à plat indexés par
 * {@code y * size + x} : aucune allocation, aucune clé texte (le prototype utilisait "x,y").
 */
public final class Board {

    private final int size;
    private final Terrain[] terrain;
    private final boolean[] obstacle;
    private final byte[] variant;

    public Board(int size) {
        this.size = size;
        this.terrain = new Terrain[size * size];
        this.obstacle = new boolean[size * size];
        this.variant = new byte[size * size];
        Arrays.fill(terrain, Terrain.GRASS);
    }

    public int size() { return size; }

    public int index(int x, int y) { return y * size + x; }

    public int index(Cell c) { return index(c.x(), c.y()); }

    public Cell cellAt(int index) { return new Cell(index % size, index / size); }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size; }

    public boolean inBounds(Cell c) { return inBounds(c.x(), c.y()); }

    public Terrain terrain(int x, int y) { return terrain[index(x, y)]; }

    public void setTerrain(int x, int y, Terrain t) { terrain[index(x, y)] = t; }

    /** Variante graphique du sol (0..5), choisie par le générateur de carte. */
    public int variant(int x, int y) { return variant[index(x, y)]; }

    public void setVariant(int x, int y, int v) { variant[index(x, y)] = (byte) v; }

    public boolean hasObstacle(int x, int y) { return obstacle[index(x, y)]; }

    public void setObstacle(int x, int y, boolean value) { obstacle[index(x, y)] = value; }

    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && terrain[index(x, y)].isWalkable() && !obstacle[index(x, y)];
    }

    public boolean isWalkable(Cell c) { return isWalkable(c.x(), c.y()); }
}
