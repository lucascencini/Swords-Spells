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

    public FighterClass(String id, String displayName, int maxHp, int maxPa, int maxPm, int initiative,
                        int preferredRange, List<Spell> spells) {
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
}
