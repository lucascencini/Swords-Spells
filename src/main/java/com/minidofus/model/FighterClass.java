package com.minidofus.model;

import java.util.List;

/**
 * Définition immuable d'une classe de combattant : caractéristiques et sorts.
 * {@code initiative} détermine l'ordre de jeu ; {@code preferredRange} guide l'IA (distance idéale à l'ennemi).
 */
public final class FighterClass {

    private final String id;
    private final String displayName;
    private final int maxHp;
    private final int maxPa;
    private final int maxPm;
    private final int initiative;
    private final int preferredRange;
    private final List<Spell> spells;
    private final String className;
    private final int level;
    private final double power;
    private final double resist;

    public FighterClass(String id, String displayName, int maxHp, int maxPa, int maxPm, int initiative,
                        int preferredRange, List<Spell> spells) {
        this(id, displayName, displayName, 1, maxHp, maxPa, maxPm, initiative, preferredRange, spells, 1, 0);
    }

    /**
     * Combattant issu d'un personnage : nom propre, niveau, puissance (multiplie dégâts et soins infligés) et
     * résistance (fraction des dégâts reçus annulée).
     */
    public FighterClass(String id, String displayName, String className, int level, int maxHp, int maxPa, int maxPm,
                        int initiative, int preferredRange, List<Spell> spells, double power, double resist) {
        this.className = className;
        this.level = level;
        this.power = power;
        this.resist = resist;
        this.id = id;
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.maxPa = maxPa;
        this.maxPm = maxPm;
        this.initiative = initiative;
        this.preferredRange = preferredRange;
        this.spells = List.copyOf(spells);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int maxHp() { return maxHp; }
    public int maxPa() { return maxPa; }
    public int maxPm() { return maxPm; }
    public int initiative() { return initiative; }
    public int preferredRange() { return preferredRange; }
    public List<Spell> spells() { return spells; }
    /** Nom de la classe (« Mage »), quand {@link #displayName} est celui d'un personnage. */
    public String className() { return className; }
    public int level() { return level; }
    public double power() { return power; }
    public double resist() { return resist; }
}
