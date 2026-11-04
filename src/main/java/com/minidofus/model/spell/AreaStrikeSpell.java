package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/** Sort de zone en croix (centre + 4 voisins), lançable sur n'importe quelle case à portée. */
public final class AreaStrikeSpell extends AbstractSpell {

    private final IntRange damage;

    public AreaStrikeSpell(String id, String name, int cost, int minRange, int maxRange, IntRange damage, CastStyle style) {
        super(id, name, cost, minRange, maxRange, TargetMode.CELL, style);
        this.damage = damage;
    }

    @Override
    public String effectSummary() {
        return damage + " dégâts (croix)";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return ctx.board().inBounds(target) && inRange(caster, target);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>(5);
        cells.add(target);
        for (int[] d : Cell.ORTHOGONAL) {
            Cell c = target.plus(d[0], d[1]);
            if (ctx.board().inBounds(c)) cells.add(c);
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        for (Cell cell : impactCells(ctx, caster, target)) {
            Fighter victim = ctx.fighterAt(cell);
            if (victim != null && victim != caster) {
                ctx.dealDamage(caster, victim, damage.roll(ctx.rng()), style());
            }
        }
    }
}
