package com.minidofus.model;

import java.util.List;

/** Définition immuable d'une classe de combattant : caractéristiques et sorts. */
public final class FighterClass {

    private final String id;
    private final String displayName;
    private final int maxHp;
    private final int maxPa;
    private final int maxPm;
    private final List<Spell> spells;

    public FighterClass(String id, String displayName, int maxHp, int maxPa, int maxPm, List<Spell> spells) {
        this.id = id;
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.maxPa = maxPa;
        this.maxPm = maxPm;
        this.spells = List.copyOf(spells);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int maxHp() { return maxHp; }
    public int maxPa() { return maxPa; }
    public int maxPm() { return maxPm; }
    public List<Spell> spells() { return spells; }
}
