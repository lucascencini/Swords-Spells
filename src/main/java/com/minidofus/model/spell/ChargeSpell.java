package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.MoveStyle;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Charge en ligne droite vers la case visée : le lanceur s'arrête devant le premier obstacle ou
 * combattant. Il frappe l'ennemi ou la caisse rencontrée (jamais un allié).
 */
public final class ChargeSpell extends AbstractSpell {

    private final IntRange damage;

    public ChargeSpell(Base base, IntRange damage) {
        super(base.inLine().noLineOfSight(), TargetMode.LINE, CastStyle.DASH);
        this.damage = damage;
    }

    public IntRange damage() { return damage; }

    /** Trajet prévu : cases parcourues, ennemi ou caisse percutés (éventuellement aucun). */
    public record Plan(List<Cell> path, Fighter hitFighter, Cell hitObstacle) {
        public boolean isEmpty() {
            return path.isEmpty() && hitFighter == null && hitObstacle == null;
        }
    }

    public Plan plan(SpellContext ctx, Fighter caster, Cell target) {
        Cell from = caster.position();
        int dx = Integer.signum(target.x() - from.x());
        int dy = Integer.signum(target.y() - from.y());
        List<Cell> path = new ArrayList<>();
        Cell current = from;
        for (int i = 0; i < from.distanceTo(target); i++) {
            Cell next = current.plus(dx, dy);
            Fighter other = ctx.fighterAt(next);
            if (other != null) return new Plan(path, other.isEnemyOf(caster) ? other : null, null);
            if (!ctx.board().isWalkable(next)) {
                return new Plan(path, null, ctx.board().isDestructible(next) ? next : null);
            }
            path.add(next);
            current = next;
        }
        return new Plan(path, null, null);
    }

    @Override
    public String effectSummary() {
        return damage + " dégâts au premier ennemi ou caisse rencontré";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return reachable(ctx, caster, target) && !plan(ctx, caster, target).isEmpty();
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        Plan plan = plan(ctx, caster, target);
        List<Cell> cells = new ArrayList<>(plan.path());
        if (plan.hitFighter() != null) cells.add(plan.hitFighter().position());
        if (plan.hitObstacle() != null) cells.add(plan.hitObstacle());
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        Plan plan = plan(ctx, caster, target);
        int moved = ctx.moveFighter(caster, plan.path(), MoveStyle.DASH);
        if (moved < plan.path().size() || !caster.isAlive()) return; // arrêté par un piège
        if (plan.hitFighter() != null) ctx.dealDamage(caster, plan.hitFighter(), ctx.roll(damage), style());
        else if (plan.hitObstacle() != null) ctx.damageObstacle(caster, plan.hitObstacle(), ctx.roll(damage), style());
    }
}
