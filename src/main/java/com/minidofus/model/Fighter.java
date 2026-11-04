package com.minidofus.model;

/** Combattant en cours de partie : état mutable (position, PV, PA, PM, bouclier). */
public final class Fighter {

    private final FighterClass fighterClass;
    private Cell position;
    private int hp;
    private int pa;
    private int pm;
    private int shieldPercent;
    private int shieldTurns;

    public Fighter(FighterClass fighterClass, Cell start) {
        this.fighterClass = fighterClass;
        this.position = start;
        this.hp = fighterClass.maxHp();
        this.pa = fighterClass.maxPa();
        this.pm = fighterClass.maxPm();
    }

    public String id() { return fighterClass.id(); }
    public String name() { return fighterClass.displayName(); }
    public FighterClass fighterClass() { return fighterClass; }
    public Cell position() { return position; }
    public int hp() { return hp; }
    public int maxHp() { return fighterClass.maxHp(); }
    public int pa() { return pa; }
    public int pm() { return pm; }
    public boolean isAlive() { return hp > 0; }
    public boolean hasShield() { return shieldTurns > 0; }

    void moveTo(Cell cell) { this.position = cell; }
    void spendPa(int amount) { this.pa -= amount; }
    void spendPm(int amount) { this.pm -= amount; }

    /** Applique les dégâts (réduits par le bouclier, 1 minimum) et renvoie les dégâts calculés. */
    int takeDamage(int raw) {
        int dealt = raw;
        if (hasShield()) {
            dealt = Math.max(1, (int) Math.floor(raw * (100 - shieldPercent) / 100.0));
        }
        hp = Math.max(0, hp - dealt);
        return dealt;
    }

    /** Soigne sans dépasser les PV max et renvoie les PV réellement rendus. */
    int heal(int amount) {
        int healed = Math.min(amount, maxHp() - hp);
        hp += healed;
        return healed;
    }

    void applyShield(int percent, int turns) {
        this.shieldPercent = percent;
        this.shieldTurns = turns;
    }

    /**
     * Début de tour : restaure PA/PM et fait vieillir le bouclier.
     *
     * @return {@code true} si le bouclier vient d'expirer
     */
    boolean startTurn() {
        pa = fighterClass.maxPa();
        pm = fighterClass.maxPm();
        if (shieldTurns > 0 && --shieldTurns == 0) {
            shieldPercent = 0;
            return true;
        }
        return false;
    }
}
