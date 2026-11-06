package com.minidofus.net;

import com.minidofus.model.Battle;
import com.minidofus.model.FighterClass;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;

import java.util.Arrays;
import java.util.List;

/**
 * En-tête d'un combat en ligne (message MATCH) : de quoi recréer exactement le même combat, plus le joueur
 * qui contrôle chaque combattant ({@code -1} = ordinateur) et la durée du chrono.
 */
public record MatchInfo(long seed, MapTheme theme, List<FighterClass> red, List<FighterClass> blue,
                        int[] controllers, int turnSeconds) {

    public static MatchInfo parse(Message m) {
        return new MatchInfo(Long.parseLong(m.arg(0)), MapTheme.valueOf(m.arg(1)), classes(m.arg(2)), classes(m.arg(3)),
                Arrays.stream(m.arg(4).split(",")).mapToInt(Integer::parseInt).toArray(), m.intArg(5, 0));
    }

    private static List<FighterClass> classes(String csv) {
        return Arrays.stream(csv.split(",")).map(FighterCatalog::byId).toList();
    }

    public Battle createBattle() {
        return GameServer.createBattle(theme, seed, red, blue);
    }
}
