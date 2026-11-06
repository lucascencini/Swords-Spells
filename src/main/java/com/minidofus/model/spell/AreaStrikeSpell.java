package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.IntRange;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sort de zone lancé sur une case à portée (ligne de vue vers le centre). Il touche tout le monde
 * dans la zone, alliés compris (sauf le lanceur), ainsi que les obstacles destructibles.
 */
public final class AreaStrikeSpell extends AbstractSpell {

    /** Forme de la zone. */
    public enum Shape { CROSS, SQUARE, LARGE }

    private final IntRange damage;
    private final Shape shape;
    private final int pushOut;

    public AreaStrikeSpell(Base base, IntRange damage, Shape shape, CastStyle style) {
        this(base, damage, shape, style, 0);
    }

    /** {@code pushOut} : les survivants sont ensuite repoussés de ce nombre de cases hors de la zone. */
    public AreaStrikeSpell(Base base, IntRange damage, Shape shape, CastStyle style, int pushOut) {
        super(base, TargetMode.CELL, style);
        this.damage = damage;
        this.shape = shape;
        this.pushOut = pushOut;
    }

    public int pushOut() { return pushOut; }

    public IntRange damage() { return damage; }

    @Override
    public String effectSummary() {
        String zone = switch (shape()) {
            case CROSS -> "croix";
            case SQUARE -> "carré 3×3";
            case LARGE -> "grande zone (rayon 2)";
        };
        return damage.scaled(powerMult()) + " dégâts en " + zone + " (alliés compris)"
                + (pushOut > 0 ? ", repousse hors de la zone" : "") + onHitSummary();
    }

    /** Forme effective : un talent peut élargir la croix en carré, et le carré en grande zone. */
    private Shape shape() {
        int up = param("area");
        if (up <= 0) return shape;
        return Shape.values()[Math.min(Shape.values().length - 1, shape.ordinal() + up)];
    }

    @Override
    public boolean canTarget(SpellContext ctx, Fighter caster, Cell target) {
        return reachable(ctx, caster, target);
    }

    @Override
    public List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target) {
        List<Cell> cells = new ArrayList<>(13);
        Shape sh = shape();
        int r = sh == Shape.LARGE ? 2 : 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (sh == Shape.CROSS && dx != 0 && dy != 0) continue;
                if (sh == Shape.LARGE && Math.abs(dx) + Math.abs(dy) > 2) continue;
                Cell c = target.plus(dx, dy);
                if (ctx.board().inBounds(c)) cells.add(c);
            }
        }
        return cells;
    }

    @Override
    public void apply(SpellContext ctx, Fighter caster, Cell target) {
        List<Fighter> hit = new ArrayList<>();
        for (Cell cell : impactCells(ctx, caster, target)) {
            Fighter victim = ctx.fighterAt(cell);
            if (victim != null && victim != caster) {
                hit(ctx, caster, victim, damage);
                if (victim.isAlive()) hit.add(victim);
            } else if (victim == null && ctx.board().isDestructible(cell)) {
                ctx.damageObstacle(caster, cell, rollDamage(ctx, damage), style());
            }
        }
        if (pushOut <= 0) return;
        // éjection : d'abord le pourtour (vers l'extérieur, en diagonale depuis les coins), puis le centre,
        // chassé à l'opposé du lanceur ; l'ordre est fixe (déterministe pour le jeu en ligne)
        hit.sort(Comparator.comparingInt((Fighter f) -> f.position().equals(target) ? 1 : 0)
                .thenComparingInt(f -> f.position().x()).thenComparingInt(f -> f.position().y()));
        for (Fighter f : hit) {
            if (!f.isAlive()) continue;
            int dx = Integer.signum(f.position().x() - target.x());
            int dy = Integer.signum(f.position().y() - target.y());
            if (dx == 0 && dy == 0) {
                int[] d = direction(caster.position(), target);
                dx = d[0];
                dy = d[1];
            }
            if (dx != 0 || dy != 0) ctx.push(caster, f, dx, dy, pushOut);
        }
    }
}
