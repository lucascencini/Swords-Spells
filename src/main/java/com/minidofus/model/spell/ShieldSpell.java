package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.SpellContext;
import com.minidofus.model.StatusKind;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Réduit les dégâts subis jusqu'au début du prochain tour du lanceur. */
public final class ShieldSpell extends AbstractSpell {

    private final int reductionPercent;

    public ShieldSpell(Base base, int reductionPercent) {
        super(base.noLineOfSight(), TargetMode.SELF, CastStyle.SHIELD);
        this.reductionPercent = reductionPercent;
    }

    @Override
    public String effectSummary() {
        return "-" + reductionPercent + " % de dégâts subis jusqu'à votre prochain tour";
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
        ctx.addStatus(caster, caster, StatusKind.SHIELD, 1, reductionPercent);
    }
}
