package com.minidofus.model.ai;

import com.minidofus.model.Cell;
import com.minidofus.model.Spell;

/** Action choisie par l'ordinateur pour le combattant actif. */
public sealed interface AiAction {

    record Move(Cell target) implements AiAction { }

    record Cast(Spell spell, Cell target) implements AiAction { }

    record EndTurn() implements AiAction { }
}
