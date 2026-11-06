package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.SpellContext;
import com.minidofus.model.StatusKind;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Bouclier de pierre sur soi ou un allié : la prochaine attaque reçue est entièrement bloquée. */
public final class StoneShieldSpell extends AbstractSpell {

    private final int turns;

    public StoneShieldSpell(Base base, int turns) {
        super(base, TargetMode.ALLY, CastStyle.STONE_SHIELD);
        this.turns = turns;
    }

    @Override
    public String effectSummary() {
        return "bloque entièrement la prochaine attaque (pendant " + turns + " tours)";
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
        ctx.addStatus(caster, ctx.fighterAt(target), StatusKind.STONE_SKIN, turns, 0);
    }
}
