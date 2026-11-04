package com.minidofus.game;

import com.minidofus.model.FighterClass;
import com.minidofus.model.Team;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;

import java.util.List;

/**
 * Paramètres d'une partie : carte, composition des deux équipes et, pour chaque équipe, le niveau de
 * l'ordinateur qui la contrôle ({@code null} = joueur humain).
 */
public record MatchSetup(MapTheme theme, long seed, List<FighterClass> red, List<FighterClass> blue,
                         Difficulty redAi, Difficulty blueAi) {

    public MatchSetup {
        if (red.isEmpty() || blue.isEmpty() || red.size() > 3 || blue.size() > 3) {
            throw new IllegalArgumentException("Chaque équipe compte de 1 à 3 combattants");
        }
        red = List.copyOf(red);
        blue = List.copyOf(blue);
    }

    /** Partie par défaut : duel Guerrier contre Mage, deux joueurs sur le même écran. */
    public static MatchSetup duel(MapTheme theme, long seed) {
        return new MatchSetup(theme, seed, List.of(FighterCatalog.WARRIOR), List.of(FighterCatalog.MAGE), null, null);
    }

    public List<FighterClass> team(Team team) {
        return team == Team.RED ? red : blue;
    }

    public Difficulty ai(Team team) {
        return team == Team.RED ? redAi : blueAi;
    }

    public MatchSetup withMap(MapTheme newTheme, long newSeed) {
        return new MatchSetup(newTheme, newSeed, red, blue, redAi, blueAi);
    }

    /** L'équipe humaine quand une seule équipe est humaine, sinon {@code null}. */
    public Team soloHumanTeam() {
        if (redAi == null && blueAi != null) return Team.RED;
        if (blueAi == null && redAi != null) return Team.BLUE;
        return null;
    }
}
