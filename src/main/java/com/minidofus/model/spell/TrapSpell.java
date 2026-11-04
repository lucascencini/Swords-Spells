package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Pose un piège sur une case libre : le premier ennemi qui y entre est stoppé et subit des dégâts. */
public final class TrapSpell extends AbstractSpell {

    private final IntRange damage;
    private final int maxTraps;

    public TrapSpell(Base base, IntRange damage, int maxTraps) {
        super(base, TargetMode.FREE_CELL, CastStyle.TRAP);
        this.damage = damage;
        this.maxTraps = maxTraps;
    }

    public IntRange damage() { return damage; }

    @Override
    public String effectSummary() {
        return damage + " dégâts et arrêt net · " + maxTraps + " pièges maximum";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return reachable(ctx, caster, target) && isFreeCell(ctx, target) && ctx.trapAt(target) == null;
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        return single(target);
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        ctx.placeTrap(caster, target, damage, maxTraps);
    }
}
