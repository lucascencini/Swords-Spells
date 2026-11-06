package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.StatusKind;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Attaque à cible unique sur un ennemi ou un obstacle destructible, avec deux options :
 * repousser la cible de {@code push} cases, ou la réduire au silence pendant {@code silenceTurns} tours.
 */
public final class StrikeSpell extends AbstractSpell {

    private final IntRange damage;
    private final int push;
    private final int silenceTurns;

    public StrikeSpell(Base base, IntRange damage, CastStyle style) {
        this(base, damage, style, 0, 0);
    }

    public StrikeSpell(Base base, IntRange damage, CastStyle style, int push, int silenceTurns) {
        super(base, TargetMode.ENEMY, style);
        this.damage = damage;
        this.push = push;
        this.silenceTurns = silenceTurns;
    }

    public IntRange damage() { return damage; }
    public int push() { return push; }

    @Override
    public String effectSummary() {
        String s = damage.scaled(powerMult()) + " dégâts";
        if (push > 0) s += ", repousse de " + push;
        if (silenceTurns > 0) s += ", silence " + silenceTurns + " tour";
        return s + onHitSummary();
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        if (!reachable(ctx, caster, target)) return false;
        Fighter victim = ctx.fighterAt(target);
        return victim != null ? victim.isEnemyOf(caster) : ctx.board().isDestructible(target);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>(List.of(target));
        Fighter victim = ctx.fighterAt(target);
        if (push > 0 && victim != null) {
            int[] d = direction(caster.position(), target);
            Cell c = target;
            for (int i = 0; i < push; i++) {
                c = c.plus(d[0], d[1]);
                if (!ctx.board().isWalkable(c) || ctx.fighterAt(c) != null) break;
                cells.add(c);
            }
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        Fighter victim = ctx.fighterAt(target);
        if (victim == null) {
            ctx.damageObstacle(caster, target, rollDamage(ctx, damage), style());
            return;
        }
        hit(ctx, caster, victim, damage);
        if (!victim.isAlive()) return;
        if (silenceTurns > 0) ctx.addStatus(caster, victim, StatusKind.SILENCE, silenceTurns, 0);
        if (push > 0) {
            int[] d = direction(caster.position(), target);
            ctx.push(caster, victim, d[0], d[1], push);
        }
    }
}
