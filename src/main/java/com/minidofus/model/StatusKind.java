package com.minidofus.model;

/**
 * Effets durables posés sur un combattant. {@code expiresAtTurnEnd} indique quand le compteur de
 * tours du statut diminue : au début du tour du porteur (bouclier, poison) ou à la fin (silence,
 * qui doit couvrir tout le tour suivant du porteur).
 */
public enum StatusKind {
    /** Réduit les dégâts subis de {@code value} %. */
    SHIELD("Bouclier", false),
    /** Inflige {@code value} dégâts au début de chaque tour du porteur. */
    POISON("Poison", false),
    /** Nuée d'abeilles : inflige {@code value} dégâts au début de chaque tour du porteur (cumulable avec le poison). */
    BEES("Nuée d'abeilles", false),
    /** Interdit de lancer des sorts (le déplacement reste possible). */
    SILENCE("Silence", true),
    /** Empêche d'être de nouveau réduit au silence juste après l'avoir été. */
    SILENCE_IMMUNITY("Immunité au silence", true);

    private final String displayName;
    private final boolean expiresAtTurnEnd;

    StatusKind(String displayName, boolean expiresAtTurnEnd) {
        this.displayName = displayName;
        this.expiresAtTurnEnd = expiresAtTurnEnd;
    }

    public String displayName() { return displayName; }
    public boolean expiresAtTurnEnd() { return expiresAtTurnEnd; }
}
