package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Soigne le lanceur (sans dépasser ses PV max). */
public final class HealSpell extends AbstractSpell {

    private final IntRange amount;

    public HealSpell(String id, String name, int cost, IntRange amount) {
        super(id, name, cost, 0, 0, TargetMode.SELF, CastStyle.HEAL);
        this.amount = amount;
    }

    @Override
    public String effectSummary() {
        return "+" + amount + " PV";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return target.equals(caster.position());
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        return single(caster.position());
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        ctx.heal(caster, amount.roll(ctx.rng()));
    }
}
