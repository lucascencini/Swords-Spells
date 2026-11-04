package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Données et outils communs à tous les sorts. */
abstract class AbstractSpell implements Spell {

    private final String id;
    private final String name;
    private final int cost;
    private final int minRange;
    private final int maxRange;
    private final TargetMode mode;
    private final CastStyle style;

    AbstractSpell(String id, String name, int cost, int minRange, int maxRange, TargetMode mode, CastStyle style) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.mode = mode;
        this.style = style;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public int cost() { return cost; }
    @Override public int minRange() { return minRange; }
    @Override public int maxRange() { return maxRange; }
    @Override public TargetMode targetMode() { return mode; }
    @Override public CastStyle style() { return style; }

    /** Distance de Manhattan comprise entre la portée minimale et maximale. */
    protected final boolean inRange(Fighter caster, Cell target) {
        int d = caster.position().distanceTo(target);
        return d >= minRange && d <= maxRange;
    }

    protected static List<Cell> single(Cell cell) {
        return List.of(cell);
    }

    @Override
    public String toString() {
        return name;
    }
}
