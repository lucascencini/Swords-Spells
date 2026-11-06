package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.SpellContext;
import com.minidofus.model.StatusKind;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Empoisonne un ennemi : dégâts au début de chacun de ses prochains tours (un nouveau poison remplace l'ancien). */
public final class PoisonSpell extends AbstractSpell {

    private final int damagePerTurn;
    private final int turns;

    public PoisonSpell(Base base, int damagePerTurn, int turns) {
        super(base, TargetMode.ENEMY, CastStyle.POISON);
        this.damagePerTurn = damagePerTurn;
        this.turns = turns;
    }

    public int damagePerTurn() { return damagePerTurn; }
    public int turns() { return turns; }

    @Override
    public String effectSummary() {
        return damagePerTurn + " dégâts au début de ses " + turns + " prochains tours";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        Fighter f = ctx.fighterAt(target);
        return reachable(ctx, caster, target) && f != null && f.isEnemyOf(caster);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        return single(target);
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        ctx.addStatus(caster, ctx.fighterAt(target), StatusKind.POISON, turns, damagePerTurn);
    }
}
