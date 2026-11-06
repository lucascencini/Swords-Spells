package com.minidofus.model.map;

import com.minidofus.model.Board;
import com.minidofus.model.Cell;
import com.minidofus.model.Team;

import java.util.List;

/** Résultat de la génération : terrain, décor et points d'apparition de chaque équipe (3 par camp). */
public record Arena(MapTheme theme, long seed, Board board, List<DecorPlacement> decor,
                    List<Cell> redSpawns, List<Cell> blueSpawns) {

    public List<Cell> spawns(Team team) {
        return team == Team.RED ? redSpawns : blueSpawns;
    }
}
