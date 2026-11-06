package com.minidofus.net;

import com.minidofus.model.Battle;
import com.minidofus.model.progression.CharacterBuild;
import com.minidofus.model.map.MapTheme;

import java.util.Arrays;
import java.util.List;

/**
 * En-tête d'un combat en ligne (message MATCH) : de quoi recréer exactement le même combat, plus le joueur
 * qui contrôle chaque combattant ({@code -1} = ordinateur) et la durée du chrono.
 */
public record MatchInfo(long seed, MapTheme theme, List<CharacterBuild> red, List<CharacterBuild> blue,
                        int[] controllers, int turnSeconds, int levelOverride) {

    public static MatchInfo parse(Message m) {
        return new MatchInfo(Long.parseLong(m.arg(0)), MapTheme.valueOf(m.arg(1)), builds(m.arg(2)), builds(m.arg(3)),
                Arrays.stream(m.arg(4).split(",")).mapToInt(Integer::parseInt).toArray(), m.intArg(5, 0), m.intArg(6, 0));
    }

    private static List<CharacterBuild> builds(String text) {
        return Arrays.stream(text.split("\\|")).map(CharacterBuild::decode).toList();
    }

    public Battle createBattle() {
        return GameServer.createBattleFromBuilds(theme, seed, red, blue, levelOverride);
    }
}
