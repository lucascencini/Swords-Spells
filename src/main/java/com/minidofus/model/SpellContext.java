package com.minidofus.model;

import java.util.List;

/** Ce qu'un sort a le droit de faire sur le combat. Implémenté par {@link Battle}. */
public interface SpellContext {

    Board board();

    /** Combattant vivant sur la case, ou {@code null}. */
    Fighter fighterAt(Cell cell);

    Trap trapAt(Cell cell);

    /** Ligne de vue : bloquée par les obstacles et par les combattants (sauf aux extrémités). */
    boolean hasLineOfSight(Cell from, Cell to);

    /** Tirage d'une valeur (moyenne exacte pendant les simulations de l'IA). */
    int roll(IntRange range);

    /**
     * Déplace un combattant le long d'un chemin. Le mouvement s'arrête sur un piège ennemi.
     *
     * @return nombre de cases réellement parcourues
     */
    int moveFighter(Fighter fighter, List<Cell> path, MoveStyle style);

    /** Place directement un combattant sur une case libre (téléportation, bond). */
    void teleport(Fighter fighter, Cell to, MoveStyle style);

    /** Pousse {@code target} de {@code distance} cases ; un choc inflige des dégâts. */
    void push(Fighter source, Fighter target, int dx, int dy, int distance);

    /** Attire {@code target} vers {@code source} (ligne droite) d'au plus {@code distance} cases. */
    void pull(Fighter source, Fighter target, int distance);

    void dealDamage(Fighter source, Fighter target, int raw, CastStyle style);

    void damageObstacle(Fighter source, Cell cell, int raw, CastStyle style);

    void heal(Fighter source, Fighter target, int amount);

    void addStatus(Fighter source, Fighter target, StatusKind kind, int turns, int value);

    void placeTrap(Fighter owner, Cell cell, IntRange damage, int maxPerOwner);
}
