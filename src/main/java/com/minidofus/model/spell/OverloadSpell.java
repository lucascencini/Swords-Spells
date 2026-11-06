package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.SpellContext;
import com.minidofus.model.StatusKind;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Surcharge : le prochain sort de dégâts du lanceur est amplifié (dans les 2 tours). */
public final class OverloadSpell extends AbstractSpell {

    private final int percent;

    public OverloadSpell(Base base, int percent) {
        super(base.noLineOfSight(), TargetMode.SELF, CastStyle.OVERLOAD);
        this.percent = percent;
    }

    private int bonus() {
        return percent + param("pct");
    }

    @Override
    public String effectSummary() {
        return "votre prochain sort de dégâts inflige +" + bonus() + " %";
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
        ctx.addStatus(caster, caster, StatusKind.EMPOWER, 2, bonus());
    }
}
