package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/** Grappin : petits dégâts puis attire l'ennemi vers le lanceur, en ligne droite. */
public final class GrappleSpell extends AbstractSpell {

    private final IntRange damage;
    private final int pull;

    public GrappleSpell(Base base, IntRange damage, int pull) {
        super(base.inLine(), TargetMode.ENEMY, CastStyle.GRAPPLE);
        this.damage = damage;
        this.pull = pull;
    }

    public IntRange damage() { return damage; }

    @Override
    public String effectSummary() {
        return damage + " dégâts, attire de " + pull + " cases";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        Fighter f = ctx.fighterAt(target);
        return reachable(ctx, caster, target) && f != null && f.isEnemyOf(caster);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>(List.of(target));
        int[] d = direction(target, caster.position());
        Cell c = target;
        for (int i = 0; i < pull; i++) {
            c = c.plus(d[0], d[1]);
            if (!isFreeCell(ctx, c)) break;
            cells.add(c);
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        Fighter victim = ctx.fighterAt(target);
        ctx.dealDamage(caster, victim, ctx.roll(damage), style());
        if (victim.isAlive()) ctx.pull(caster, victim, pull);
    }
}
