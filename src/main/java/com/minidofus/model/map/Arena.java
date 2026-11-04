package com.minidofus.model.map;

import com.minidofus.model.Board;
import com.minidofus.model.Cell;

import java.util.List;

/** Résultat de la génération : terrain, décor et points d'apparition des deux combattants. */
public record Arena(MapTheme theme, long seed, Board board, List<DecorPlacement> decor, Cell spawnA, Cell spawnB) { }
