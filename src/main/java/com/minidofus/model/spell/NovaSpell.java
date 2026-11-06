package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/** Onde autour du lanceur : touche les ennemis des cases voisines (rayon 1, ou plus avec un talent). */
public final class NovaSpell extends AbstractSpell {

    private final IntRange damage;

    public NovaSpell(Base base, IntRange damage, CastStyle style) {
        super(base.noLineOfSight(), TargetMode.SELF, style);
        this.damage = damage;
    }

    private int radius() {
        return 1 + param("radius");
    }

    @Override
    public String effectSummary() {
        return damage.scaled(powerMult()) + " dégâts aux ennemis à " + radius() + " case" + (radius() > 1 ? "s" : "")
                + " autour de vous" + onHitSummary();
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return target.equals(caster.position());
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>();
        int r = radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx == 0 && dy == 0) continue;
                Cell c = caster.position().plus(dx, dy);
                if (ctx.board().inBounds(c)) cells.add(c);
            }
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        for (Cell c : impactCells(ctx, caster, target)) {
            Fighter f = ctx.fighterAt(c);
            if (f != null && f.isEnemyOf(caster)) hit(ctx, caster, f, damage);
        }
    }
}
