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
 * Charge en ligne droite : le lanceur fonce vers la case visée, s'arrête devant le premier obstacle
 * ou ennemi, et frappe l'ennemi rencontré. La distance parcourue est celle de la case cliquée
 * (le prototype parcourait toujours la portée maximale, quelle que soit la case visée).
 */
public final class ChargeSpell extends AbstractSpell {

    private final IntRange damage;

    public ChargeSpell(String id, String name, int cost, int minRange, int maxRange, IntRange damage) {
        super(id, name, cost, minRange, maxRange, TargetMode.LINE, CastStyle.DASH);
        this.damage = damage;
    }

    /** Trajet prévu : cases parcourues et ennemi percuté (éventuellement {@code null}). */
    public record Plan(List<Cell> path, Fighter hit) {
        public boolean isEmpty() {
            return path.isEmpty() && hit == null;
        }
    }

    public Plan plan(SpellContext ctx, Fighter caster, Cell target) {
        Cell from = caster.position();
        int dx = Integer.signum(target.x() - from.x());
        int dy = Integer.signum(target.y() - from.y());
        int steps = from.distanceTo(target);
        List<Cell> path = new ArrayList<>();
        Fighter hit = null;
        Cell current = from;
        for (int i = 0; i < steps; i++) {
            Cell next = current.plus(dx, dy);
            if (!ctx.board().isWalkable(next)) break;
            Fighter other = ctx.fighterAt(next);
            if (other != null) {
                if (other != caster) hit = other;
                break;
            }
            path.add(next);
            current = next;
        }
        return new Plan(path, hit);
    }

    @Override
    public String effectSummary() {
        return damage + " dégâts";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return inRange(caster, target)
                && caster.position().isAlignedWith(target)
                && !plan(ctx, caster, target).isEmpty();
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        Plan plan = plan(ctx, caster, target);
        List<Cell> cells = new ArrayList<>(plan.path());
        if (plan.hit() != null) cells.add(plan.hit().position());
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        Plan plan = plan(ctx, caster, target);
        ctx.moveFighter(caster, plan.path(), MoveStyle.DASH);
        if (plan.hit() != null) {
            ctx.dealDamage(caster, plan.hit(), damage.roll(ctx.rng()), style());
        }
    }
}
