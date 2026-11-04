package com.minidofus.model;

import java.util.Arrays;

/**
 * Grille carrée de combat, stockée dans des tableaux à plat indexés par {@code y * size + x}.
 * Un obstacle peut être destructible : il a alors des points de structure ({@link #obstacleHp}).
 */
public final class Board {

    private final int size;
    private final Terrain[] terrain;
    private final boolean[] obstacle;
    private final int[] obstacleHp;
    private final byte[] variant;

    public Board(int size) {
        this.size = size;
        this.terrain = new Terrain[size * size];
        this.obstacle = new boolean[size * size];
        this.obstacleHp = new int[size * size];
        this.variant = new byte[size * size];
        Arrays.fill(terrain, Terrain.GRASS);
    }

    private Board(Board other) {
        this.size = other.size;
        this.terrain = other.terrain.clone();
        this.obstacle = other.obstacle.clone();
        this.obstacleHp = other.obstacleHp.clone();
        this.variant = other.variant;
    }

    public Board copy() { return new Board(this); }

    public int size() { return size; }
    public int index(int x, int y) { return y * size + x; }
    public int index(Cell c) { return index(c.x(), c.y()); }
    public Cell cellAt(int index) { return new Cell(index % size, index / size); }
    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size; }
    public boolean inBounds(Cell c) { return inBounds(c.x(), c.y()); }

    public Terrain terrain(int x, int y) { return terrain[index(x, y)]; }
    public void setTerrain(int x, int y, Terrain t) { terrain[index(x, y)] = t; }

    /** Variante graphique du sol, choisie par le générateur de carte. */
    public int variant(int x, int y) { return variant[index(x, y)]; }
    public void setVariant(int x, int y, int v) { variant[index(x, y)] = (byte) v; }

    public boolean hasObstacle(int x, int y) { return obstacle[index(x, y)]; }
    public boolean hasObstacle(Cell c) { return inBounds(c) && hasObstacle(c.x(), c.y()); }
    public void setObstacle(int x, int y, boolean value) { setObstacle(x, y, value, 0); }

    /** @param hp points de structure, 0 = indestructible */
    public void setObstacle(int x, int y, boolean value, int hp) {
        obstacle[index(x, y)] = value;
        obstacleHp[index(x, y)] = value ? hp : 0;
    }

    public boolean isDestructible(Cell c) { return hasObstacle(c) && obstacleHp[index(c)] > 0; }
    public int obstacleHp(Cell c) { return inBounds(c) ? obstacleHp[index(c)] : 0; }

    /**
     * Abîme un obstacle destructible.
     *
     * @return dégâts réellement infligés (0 si indestructible)
     */
    int damageObstacle(Cell c, int amount) {
        if (!isDestructible(c)) return 0;
        int i = index(c);
        int dealt = Math.min(amount, obstacleHp[i]);
        obstacleHp[i] -= dealt;
        if (obstacleHp[i] <= 0) obstacle[i] = false;
        return dealt;
    }

    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && terrain[index(x, y)].isWalkable() && !obstacle[index(x, y)];
    }

    public boolean isWalkable(Cell c) { return isWalkable(c.x(), c.y()); }

    /** La case bloque-t-elle la ligne de vue ? (obstacles oui, eau non) */
    public boolean blocksSight(Cell c) { return hasObstacle(c); }
}
