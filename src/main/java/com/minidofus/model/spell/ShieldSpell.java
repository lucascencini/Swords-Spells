package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/**
 * Réduit les dégâts subis. {@code durationTurns = 1} protège pendant le prochain tour adverse puis
 * expire au début du tour du lanceur (dans le prototype, la durée réelle était de 2 tours adverses).
 */
public final class ShieldSpell extends AbstractSpell {

    private final int reductionPercent;
    private final int durationTurns;

    public ShieldSpell(String id, String name, int cost, int reductionPercent, int durationTurns) {
        super(id, name, cost, 0, 0, TargetMode.SELF, CastStyle.SHIELD);
        this.reductionPercent = reductionPercent;
        this.durationTurns = durationTurns;
    }

    @Override
    public String effectSummary() {
        return "-" + reductionPercent + "% dégâts";
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
        ctx.applyShield(caster, reductionPercent, durationTurns);
    }
}
