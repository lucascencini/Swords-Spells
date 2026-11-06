package com.minidofus.model.spell;

import com.minidofus.model.IntRange;
import com.minidofus.model.SpellTuning;
import com.minidofus.model.StatusKind;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.model.SpellContext;
import com.minidofus.model.TargetMode;

import java.util.List;

/** Données et outils communs à tous les sorts. */
public abstract class AbstractSpell implements Spell, Cloneable {

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

    private Base base;
    private final TargetMode mode;
    private final CastStyle style;
    /** Améliorations (talents) : multiplicateur de dégâts/soins, effets à l'impact, paramètres propres au sort. */
    private double powerMult = 1;
    private List<OnHit> onHit = List.of();
    private Map<String, Integer> params = Map.of();

    protected AbstractSpell(Base base, TargetMode mode, CastStyle style) {
        this.base = base;
        this.mode = mode;
        this.style = style;
    }

    /** Effet ajouté à chaque ennemi touché par le sort : statut, durée en tours, valeur. */
    public record OnHit(StatusKind kind, int turns, int value) { }

    /**
     * Copie du sort avec des améliorations : coût, portée et relance modifiés (bornés), dégâts multipliés,
     * effets à l'impact et paramètres propres ajoutés. Le sort d'origine (catalogue) n'est jamais modifié.
     */
    @Override
    public Spell tuned(SpellTuning t) {
        if (t == null || t.isEmpty()) return this;
        try {
            AbstractSpell c = (AbstractSpell) super.clone();
            int min = base.minRange();
            int max = Math.max(min, base.maxRange() + (base.maxRange() > 0 ? t.range() : 0));
            c.base = new Base(base.id(), base.name(), Math.max(1, base.cost() + t.cost()), min, max,
                    Math.max(0, base.cooldown() + t.cooldown()), base.lineOfSight() && !t.ignoresLineOfSight(), base.lineOnly());
            c.powerMult = powerMult * (1 + t.powerPct() / 100.0);
            List<OnHit> hits = new ArrayList<>(onHit);
            hits.addAll(t.onHit());
            c.onHit = List.copyOf(hits);
            Map<String, Integer> p = new TreeMap<>(params);
            t.params().forEach((k, v) -> p.merge(k, v, Integer::sum));
            c.params = Map.copyOf(p);
            return c;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Bonus d'un paramètre propre au sort (0 si aucun talent ne le modifie). */
    protected final int param(String key) {
        return params.getOrDefault(key, 0);
    }

    /** Multiplicateur de dégâts/soins dû aux améliorations du sort. */
    public final double powerMult() { return powerMult; }

    /** Tirage de dégâts du sort, améliorations comprises (puissance du lanceur appliquée ensuite par le combat). */
    protected final int rollDamage(SpellContext ctx, IntRange range) {
        return (int) Math.round(ctx.roll(range) * powerMult);
    }

    /** Inflige les dégâts du sort à un combattant puis, s'il survit et s'il est ennemi, les effets à l'impact. */
    protected final void hit(SpellContext ctx, Fighter caster, Fighter victim, IntRange range) {
        ctx.dealDamage(caster, victim, rollDamage(ctx, range), style);
        applyOnHit(ctx, caster, victim);
    }

    protected final void applyOnHit(SpellContext ctx, Fighter caster, Fighter victim) {
        if (!victim.isAlive() || !victim.isEnemyOf(caster)) return;
        for (OnHit h : onHit) ctx.addStatus(caster, victim, h.kind(), h.turns(), h.value());
    }

    /** Texte des effets à l'impact, pour les info-bulles. */
    protected final String onHitSummary() {
        StringBuilder sb = new StringBuilder();
        for (OnHit h : onHit) {
            sb.append(switch (h.kind()) {
                case BURN -> ", brûlure " + h.value() + "/tour (" + h.turns() + " t.)";
                case CHILL -> ", -" + h.value() + " PM au tour suivant";
                case ROOT -> ", immobilisé au tour suivant";
                case POISON -> ", poison " + h.value() + "/tour";
                case SILENCE -> ", silence";
                default -> "";
            });
        }
        return sb.toString();
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
