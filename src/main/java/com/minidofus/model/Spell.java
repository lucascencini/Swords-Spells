package com.minidofus.model;

import java.util.List;

/** Contrat d'un sort. Chaque sort porte ses propres règles de ciblage et d'effet. */
public interface Spell {

    String id();

    String name();

    int cost();

    int minRange();

    int maxRange();

    TargetMode targetMode();

    CastStyle style();

    /** Tours d'attente après lancement : 0 = relançable, 1 = une fois par tour, 2 = un tour sur deux… */
    int cooldown();

    boolean needsLineOfSight();

    /** La cible doit-elle être alignée avec le lanceur ? */
    boolean lineOnly();

    /** Résumé court des effets, affiché sur les info-bulles (ex. « 12-18 dégâts »). */
    String effectSummary();

    /** Distance (et alignement) compatibles, sans tenir compte de la ligne de vue ni du contenu de la case. */
    default boolean isInRange(Cell from, Cell to) {
        int d = from.distanceTo(to);
        return d >= minRange() && d <= maxRange() && (!lineOnly() || from.isAlignedWith(to));
    }

    /** La case est-elle une cible valide pour ce lanceur (portée, ligne de vue, contenu de la case…) ? */
    boolean canTarget(SpellContext ctx, Fighter caster, Cell target);

    /** Cases touchées si le sort est lancé sur {@code target} (prévisualisation et mise en scène). */
    List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target);

    /** Applique les effets (les PA sont déjà dépensés par {@link Battle}). */
    void apply(SpellContext ctx, Fighter caster, Cell target);

    /** Copie améliorée par des talents (voir {@link SpellTuning}) ; le sort lui-même ne change jamais. */
    default Spell tuned(SpellTuning tuning) {
        return this;
    }
}
