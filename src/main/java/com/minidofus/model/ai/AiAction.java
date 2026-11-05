package com.minidofus.model.ai;

import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Command;
import com.minidofus.model.Spell;

/** Action choisie par l'ordinateur pour le combattant actif. */
public sealed interface AiAction {

    /** Commande réseau équivalente. */
    default Command toCommand(Battle battle) {
        if (this instanceof Move m) return new Command.Move(m.target().x(), m.target().y());
        if (this instanceof Cast c) return battle.toCommand(c.spell(), c.target());
        return new Command.EndTurn();
    }

    record Move(Cell target) implements AiAction { }

    record Cast(Spell spell, Cell target) implements AiAction { }

    record EndTurn() implements AiAction { }
}
