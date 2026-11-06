package com.minidofus.model;

import java.util.List;

/**
 * Événements émis par le combat. Le modèle applique les règles instantanément puis publie ces
 * événements ; la vue les rejoue ensuite dans l'ordre avec ses animations.
 */
public sealed interface BattleEvent {

    /** Ordre de jeu fixé au début du combat (tri par initiative, équipes alternées). */
    record TurnOrderSet(List<Fighter> order) implements BattleEvent { }

    record TurnStarted(Fighter fighter, int round) implements BattleEvent { }

    record TurnEnded(Fighter fighter) implements BattleEvent { }

    record Moved(Fighter fighter, List<Cell> path, MoveStyle style) implements BattleEvent { }

    record SpellCast(Fighter caster, Spell spell, Cell target, List<Cell> impact) implements BattleEvent { }

    record Damaged(Fighter source, Fighter target, int amount, int hpAfter, boolean heavy, CastStyle style)
            implements BattleEvent { }

    record Healed(Fighter source, Fighter target, int amount, int hpAfter) implements BattleEvent { }

    record StatusChanged(Fighter fighter, StatusKind kind, boolean active, int turns) implements BattleEvent { }

    record Died(Fighter fighter, Fighter killer) implements BattleEvent { }

    record ObstacleDamaged(Cell cell, int amount, int hpLeft, CastStyle style) implements BattleEvent { }

    record ObstacleDestroyed(Cell cell) implements BattleEvent { }

    record TrapPlaced(Trap trap) implements BattleEvent { }

    record TrapTriggered(Trap trap, Fighter victim) implements BattleEvent { }

    record TrapRemoved(Trap trap) implements BattleEvent { }

    record BattleEnded(Team winner) implements BattleEvent { }

    /** Zone de glace posée (ou remplacée) ; {@code cells} : cases gelées. */
    record IceZonePlaced(IceZone zone, List<Cell> cells) implements BattleEvent { }

    record IceZoneMelted(IceZone zone) implements BattleEvent { }

    /** Début de tour sur le sol gelé : PM perdus (les dégâts suivent dans un {@link Damaged}). */
    record Chilled(Fighter fighter, int pmLost) implements BattleEvent { }

    /** Attaque entièrement bloquée par le bouclier de pierre, qui se brise. */
    record Blocked(Fighter target, Fighter source) implements BattleEvent { }
}
