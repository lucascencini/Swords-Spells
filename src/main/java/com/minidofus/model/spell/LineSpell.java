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
 * Rayon en ligne droite : touche tous les ennemis alignés entre le lanceur et la case visée (il traverse les
 * combattants, s'arrête sur un obstacle, qu'il endommage s'il est destructible).
 */
public final class LineSpell extends AbstractSpell {

    private final IntRange damage;

    public LineSpell(Base base, IntRange damage, CastStyle style) {
        super(base.inLine().noLineOfSight(), TargetMode.CELL, style);
        this.damage = damage;
    }

    @Override
    public String effectSummary() {
        return damage.scaled(powerMult()) + " dégâts à chaque ennemi de la ligne (traverse les combattants)" + onHitSummary();
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return ctx.board().inBounds(target) && isInRange(caster.position(), target) && !target.equals(caster.position());
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        int[] d = direction(caster.position(), target);
        List<Cell> cells = new ArrayList<>();
        Cell c = caster.position();
        for (int i = 0; i < maxRange(); i++) {
            c = c.plus(d[0], d[1]);
            if (!ctx.board().inBounds(c)) break;
            cells.add(c);
            if (ctx.board().hasObstacle(c)) break;
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        for (Cell c : impactCells(ctx, caster, target)) {
            Fighter f = ctx.fighterAt(c);
            if (f != null && f.isEnemyOf(caster)) hit(ctx, caster, f, damage);
            else if (f == null && ctx.board().isDestructible(c)) ctx.damageObstacle(caster, c, rollDamage(ctx, damage), style());
        }
    }
}
