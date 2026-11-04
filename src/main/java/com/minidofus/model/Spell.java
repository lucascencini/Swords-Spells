package com.minidofus.model;

import java.util.List;

/**
 * Contrat d'un sort. Chaque sort porte ses propres règles : plus de {@code if (spell.id == ...)}
 * dispersés dans le code comme dans le prototype.
 */
public interface Spell {

    String id();

    String name();

    int cost();

    int minRange();

    int maxRange();

    TargetMode targetMode();

    CastStyle style();

    /** Résumé court des effets, affiché sur le bouton (ex. « 12-18 dégâts »). */
    String effectSummary();

    /** La case est-elle une cible valide pour ce lanceur (portée, alignement, présence d'un ennemi...) ? */
    boolean canTarget(SpellContext ctx, Fighter caster, Cell target);

    /** Cases touchées si le sort est lancé sur {@code target} (prévisualisation et mise en scène). */
    List<Cell> impactCells(SpellContext ctx, Fighter caster, Cell target);

    /** Applique les effets (les PA sont déjà dépensés par {@link Battle}). */
    void apply(SpellContext ctx, Fighter caster, Cell target);
}
