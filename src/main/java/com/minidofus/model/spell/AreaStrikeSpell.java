package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Sort de zone lancé sur une case à portée (ligne de vue vers le centre). Il touche tout le monde
 * dans la zone, alliés compris (sauf le lanceur), ainsi que les obstacles destructibles.
 */
public final class AreaStrikeSpell extends AbstractSpell {

    /** Forme de la zone. */
    public enum Shape { CROSS, SQUARE }

    private final IntRange damage;
    private final Shape shape;

    public AreaStrikeSpell(Base base, IntRange damage, Shape shape, CastStyle style) {
        super(base, TargetMode.CELL, style);
        this.damage = damage;
        this.shape = shape;
    }

    public IntRange damage() { return damage; }

    @Override
    public String effectSummary() {
        return damage + " dégâts en " + (shape == Shape.CROSS ? "croix" : "carré 3×3") + " (alliés compris)";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return reachable(ctx, caster, target);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (shape == Shape.CROSS && dx != 0 && dy != 0) continue;
                Cell c = target.plus(dx, dy);
                if (ctx.board().inBounds(c)) cells.add(c);
            }
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        for (Cell cell : impactCells(ctx, caster, target)) {
            Fighter victim = ctx.fighterAt(cell);
            if (victim != null && victim != caster) ctx.dealDamage(caster, victim, ctx.roll(damage), style());
            else if (victim == null && ctx.board().isDestructible(cell)) ctx.damageObstacle(caster, cell, ctx.roll(damage), style());
        }
    }
}
