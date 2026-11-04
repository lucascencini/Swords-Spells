package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Attaque à cible unique (mêlée ou distance) : un ennemi doit se trouver sur la case visée. */
public final class StrikeSpell extends AbstractSpell {

    private final IntRange damage;

    public StrikeSpell(String id, String name, int cost, int minRange, int maxRange, IntRange damage, CastStyle style) {
        super(id, name, cost, minRange, maxRange, TargetMode.ENEMY, style);
        this.damage = damage;
    }

    @Override
    public String effectSummary() {
        return damage + " dégâts";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        Fighter victim = ctx.fighterAt(target);
        return inRange(caster, target) && victim != null && victim != caster;
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        return single(target);
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        ctx.dealDamage(caster, ctx.fighterAt(target), damage.roll(ctx.rng()), style());
    }
}
