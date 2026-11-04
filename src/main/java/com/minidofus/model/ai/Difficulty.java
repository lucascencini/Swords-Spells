package com.minidofus.model.ai;

/** Niveaux de l'ordinateur : poids de l'évaluation et part de hasard dans le choix. */
public enum Difficulty {
    /** Joue souvent un coup correct mais pas le meilleur, ignore les menaces. */
    EASY("Facile", 0.6, 0.0, 12.0, 4),
    /** Prend le meilleur coup immédiat et se place selon sa classe. */
    NORMAL("Normal", 1.5, 0.15, 1.0, 1),
    /** Anticipe la riposte adverse et protège ses combattants fragiles. */
    HARD("Difficile", 2.0, 0.4, 0.0, 1);

    private final String displayName;
    final double positionWeight;
    final double threatWeight;
    final double noise;
    final int topChoices;

    Difficulty(String displayName, double positionWeight, double threatWeight, double noise, int topChoices) {
        this.displayName = displayName;
        this.positionWeight = positionWeight;
        this.threatWeight = threatWeight;
        this.noise = noise;
        this.topChoices = topChoices;
    }

    public String displayName() {
        return displayName;
    }
}
