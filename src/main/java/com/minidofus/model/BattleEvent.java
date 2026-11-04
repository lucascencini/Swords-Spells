package com.minidofus.model;

import java.util.List;

/**
 * Événements émis par le combat. Le modèle applique les règles instantanément puis publie ces
 * événements ; la vue les rejoue ensuite dans l'ordre avec ses animations. C'est ce qui sépare
 * la logique du rendu (dans le prototype, {@code applyDamage} créait des éléments DOM et jouait des sons).
 */
public sealed interface BattleEvent {

    record TurnStarted(Fighter fighter, int turn) implements BattleEvent { }

    record Moved(Fighter fighter, List<Cell> path, MoveStyle style) implements BattleEvent { }

    record SpellCast(Fighter caster, Spell spell, Cell target, List<Cell> impact) implements BattleEvent { }

    record Damaged(Fighter source, Fighter target, int amount, int hpAfter, boolean heavy, CastStyle style)
            implements BattleEvent { }

    record Healed(Fighter target, int amount, int hpAfter) implements BattleEvent { }

    record ShieldChanged(Fighter fighter, boolean active) implements BattleEvent { }

    record BattleEnded(Fighter winner) implements BattleEvent { }
}
