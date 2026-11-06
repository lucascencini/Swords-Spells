package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Zone de glace 3×3 : dégâts immédiats aux ennemis dedans, puis le sol reste gelé quelques tours (dégâts et
 * perte de PM pour un ennemi qui commence son tour dedans). Une nouvelle zone remplace la précédente.
 */
public final class IceZoneSpell extends AbstractSpell {

    private final IntRange impact;
    private final int turns;
    private final int damagePerTurn;
    private final int pmLoss;

    public IceZoneSpell(Base base, IntRange impact, int turns, int damagePerTurn, int pmLoss) {
        super(base, TargetMode.CELL, CastStyle.ICE_ZONE);
        this.impact = impact;
        this.turns = turns;
        this.damagePerTurn = damagePerTurn;
        this.pmLoss = pmLoss;
    }

    public IntRange impact() { return impact; }

    @Override
    public String effectSummary() {
        return impact + " dégâts en 3×3 (ennemis), puis sol gelé " + turns + " tours : " + damagePerTurn
                + " dégâts et -" + pmLoss + " PM à qui commence son tour dedans";
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return reachable(ctx, caster, target);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Cell c = target.plus(dx, dy);
                if (ctx.board().inBounds(c)) cells.add(c);
            }
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        for (Cell c : impactCells(ctx, caster, target)) {
            Fighter f = ctx.fighterAt(c);
            if (f != null && f.isEnemyOf(caster)) ctx.dealDamage(caster, f, ctx.roll(impact), style());
        }
        ctx.placeIceZone(caster, target, turns, damagePerTurn, pmLoss);
    }
}
