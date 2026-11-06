package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Soigne le lanceur ou un allié à portée (sans dépasser ses PV max). */
public final class HealSpell extends AbstractSpell {

    private final IntRange amount;

    public HealSpell(Base base, IntRange amount) {
        this(base, amount, CastStyle.HEAL);
    }

    public HealSpell(Base base, IntRange amount, CastStyle style) {
        super(base, TargetMode.ALLY, style);
        this.amount = amount;
    }

    public IntRange amount() { return amount; }

    @Override
    public String effectSummary() {
        return "+" + amount.scaled(powerMult()) + " PV";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        Fighter f = ctx.fighterAt(target);
        boolean self = target.equals(caster.position());
        return f != null && !f.isEnemyOf(caster) && (self || reachable(ctx, caster, target));
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        return single(target);
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        ctx.heal(caster, ctx.fighterAt(target), rollDamage(ctx, amount));
    }
}
