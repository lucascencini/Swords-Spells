package com.minidofus.model;

/** Comment un sort désigne sa cible. Sert aussi à l'interface pour colorer les cases et rédiger l'aide. */
public enum TargetMode {
    /** Un ennemi, ou un obstacle destructible. */
    ENEMY("un ennemi ou une caisse"),
    /** Soi-même ou un allié. */
    ALLY("soi-même ou un allié"),
    /** N'importe quelle case à portée (sorts de zone). */
    CELL("une case (zone)"),
    /** Le lanceur lui-même. */
    SELF("le lanceur"),
    /** Une case alignée avec le lanceur. */
    LINE("en ligne droite"),
    /** Une case libre (téléportation, piège). */
    FREE_CELL("une case libre");

    private final String description;

    TargetMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
