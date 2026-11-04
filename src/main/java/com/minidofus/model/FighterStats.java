package com.minidofus.model;

/** Statistiques cumulées d'un combattant pendant le combat (affichées à la fin). */
public final class FighterStats {

    private int damageDealt;
    private int damageTaken;
    private int healingDone;
    private int kills;
    private int spellsCast;
    private int cellsMoved;
    private int obstaclesDestroyed;

    public int damageDealt() { return damageDealt; }
    public int damageTaken() { return damageTaken; }
    public int healingDone() { return healingDone; }
    public int kills() { return kills; }
    public int spellsCast() { return spellsCast; }
    public int cellsMoved() { return cellsMoved; }
    public int obstaclesDestroyed() { return obstaclesDestroyed; }

    void addDamageDealt(int v) { damageDealt += v; }
    void addDamageTaken(int v) { damageTaken += v; }
    void addHealing(int v) { healingDone += v; }
    void addKill() { kills++; }
    void addSpell() { spellsCast++; }
    void addCells(int v) { cellsMoved += v; }
    void addObstacleDestroyed() { obstaclesDestroyed++; }

    FighterStats copy() {
        FighterStats s = new FighterStats();
        s.damageDealt = damageDealt;
        s.damageTaken = damageTaken;
        s.healingDone = healingDone;
        s.kills = kills;
        s.spellsCast = spellsCast;
        s.cellsMoved = cellsMoved;
        s.obstaclesDestroyed = obstaclesDestroyed;
        return s;
    }
}
