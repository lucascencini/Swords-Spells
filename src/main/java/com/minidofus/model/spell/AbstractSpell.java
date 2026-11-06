package com.minidofus.model.spell;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Données et outils communs à tous les sorts. */
public abstract class AbstractSpell implements Spell {

    /** Caractéristiques communes, regroupées pour garder des constructeurs lisibles dans le catalogue. */
    public record Base(String id, String name, int cost, int minRange, int maxRange, int cooldown,
                       boolean lineOfSight, boolean lineOnly) {

        public static Base of(String id, String name, int cost, int minRange, int maxRange) {
            return new Base(id, name, cost, minRange, maxRange, 0, true, false);
        }

        public Base cooldown(int turns) {
            return new Base(id, name, cost, minRange, maxRange, turns, lineOfSight, lineOnly);
        }

        public Base noLineOfSight() {
            return new Base(id, name, cost, minRange, maxRange, cooldown, false, lineOnly);
        }

        public Base inLine() {
            return new Base(id, name, cost, minRange, maxRange, cooldown, lineOfSight, true);
        }
    }

    private final Base base;
    private final TargetMode mode;
    private final CastStyle style;

    protected AbstractSpell(Base base, TargetMode mode, CastStyle style) {
        this.base = base;
        this.mode = mode;
        this.style = style;
    }

    @Override public String id() { return base.id(); }
    @Override public String name() { return base.name(); }
    @Override public int cost() { return base.cost(); }
    @Override public int minRange() { return base.minRange(); }
    @Override public int maxRange() { return base.maxRange(); }
    @Override public int cooldown() { return base.cooldown(); }
    @Override public boolean needsLineOfSight() { return base.lineOfSight(); }
    @Override public boolean lineOnly() { return base.lineOnly(); }
    @Override public TargetMode targetMode() { return mode; }
    @Override public CastStyle style() { return style; }

    /** Portée, alignement et ligne de vue. */
    protected final boolean reachable(SpellContext ctx, Fighter caster, Cell target) {
        return ctx.board().inBounds(target)
                && isInRange(caster.position(), target)
                && (!needsLineOfSight() || ctx.hasLineOfSight(caster.position(), target));
    }

    protected static boolean isFreeCell(SpellContext ctx, Cell c) {
        return ctx.board().isWalkable(c) && ctx.fighterAt(c) == null;
    }

    protected static List<Cell> single(Cell cell) {
        return List.of(cell);
    }

    /** Direction unitaire (dx, dy) du lanceur vers la cible, alignée sur l'axe dominant. */
    protected static int[] direction(Cell from, Cell to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        return Math.abs(dx) >= Math.abs(dy) ? new int[] {Integer.signum(dx), 0} : new int[] {0, Integer.signum(dy)};
    }

    @Override
    public String toString() {
        return name();
    }
}
