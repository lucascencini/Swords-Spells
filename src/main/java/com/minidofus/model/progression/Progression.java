package com.minidofus.model.progression;

import com.minidofus.model.ai.Difficulty;

/**
 * Règles chiffrées de la progression, toutes au même endroit : courbe d'expérience, caractéristiques par
 * niveau, gains de fin de combat. Les PA et PM ne montent pas avec le niveau (ils pèsent trop sur l'équilibre
 * tactique) : seuls les PV et la puissance (dégâts et soins) progressent automatiquement.
 */
public final class Progression {

    public static final int MAX_LEVEL = 50;
    /** PV : +2 % par niveau au-delà du premier. */
    public static final double HP_PER_LEVEL = 0.02;
    /** Puissance (dégâts et soins) : +1 % par niveau au-delà du premier. */
    public static final double POWER_PER_LEVEL = 0.01;
    /** Résistance maximale (fraction des dégâts annulée). */
    public static final double MAX_RESIST = 0.4;

    private Progression() { }

    /** Points de talent disponibles à ce niveau : 1 par niveau à partir du niveau 2. */
    public static int talentPoints(int level) {
        return Math.max(0, Math.min(level, MAX_LEVEL) - 1);
    }

    /** Expérience nécessaire pour passer du niveau {@code level} au suivant. */
    public static int xpToNext(int level) {
        if (level >= MAX_LEVEL) return 0;
        return (int) Math.round(40 * Math.pow(level, 1.4) + 60);
    }

    /**
     * Expérience d'un combat : base selon le niveau des adversaires, bonus de victoire, difficulté de l'ordinateur
     * ({@code null} contre des joueurs), et écart de niveau (combattre plus fort rapporte plus).
     */
    public static int reward(int myLevel, double enemyLevel, boolean won, Difficulty difficulty) {
        double xp = 30 + 6 * enemyLevel;
        xp *= won ? 1.5 : 0.6;
        if (difficulty != null) {
            xp *= switch (difficulty) {
                case EASY -> 0.6;
                case NORMAL -> 1.0;
                case HARD -> 1.4;
            };
        } else {
            xp *= 1.2;
        }
        xp *= Math.max(0.5, Math.min(1.5, 1 + 0.05 * (enemyLevel - myLevel)));
        return (int) Math.round(xp);
    }

    /** Ajoute de l'expérience ; renvoie {niveau, xp dans le niveau, niveaux gagnés}. */
    public static int[] addXp(int level, int xp, int gained) {
        int l = level;
        int x = xp + gained;
        int ups = 0;
        while (l < MAX_LEVEL && x >= xpToNext(l)) {
            x -= xpToNext(l);
            l++;
            ups++;
        }
        if (l >= MAX_LEVEL) x = 0;
        return new int[] {l, x, ups};
    }
}
