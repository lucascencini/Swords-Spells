package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.MoveStyle;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/**
 * Déplacement instantané vers une case libre, sans tenir compte des obstacles entre les deux :
 * téléportation (n'importe quelle direction) ou bond (en ligne droite).
 */
public final class MobilitySpell extends AbstractSpell {

    private final MoveStyle moveStyle;

    public MobilitySpell(Base base, CastStyle style, MoveStyle moveStyle) {
        super(base.noLineOfSight(), base.lineOnly() ? TargetMode.LINE : TargetMode.FREE_CELL, style);
        this.moveStyle = moveStyle;
    }

    @Override
    public String effectSummary() {
        return (lineOnly() ? "Bondit en ligne droite" : "Se téléporte") + " jusqu'à " + maxRange() + " cases, par-dessus les obstacles";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return reachable(ctx, caster, target) && isFreeCell(ctx, target);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        return single(target);
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        ctx.teleport(caster, target, moveStyle);
    }
}
