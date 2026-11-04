package com.minidofus.model;

import java.util.List;
import java.util.Random;

/** Ce qu'un sort a le droit de faire sur le combat. Implémenté par {@link Battle}. */
public interface SpellContext {

    Board board();

    /** Combattant vivant sur la case, ou {@code null}. */
    Fighter fighterAt(Cell cell);

    Fighter opponentOf(Fighter fighter);

    Random rng();

    void moveFighter(Fighter fighter, List<Cell> path, MoveStyle style);

    void dealDamage(Fighter source, Fighter target, int raw, CastStyle style);

    void heal(Fighter target, int amount);

    void applyShield(Fighter target, int percent, int turns);
}
