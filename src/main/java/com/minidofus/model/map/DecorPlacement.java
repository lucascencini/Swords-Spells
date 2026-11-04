package com.minidofus.model.map;

/** Un élément de décor posé à une position (éventuellement fractionnaire) de la grille. */
public record DecorPlacement(DecorKind kind, double x, double y, double scale, boolean mirrored) {

    /** Échelle finale = échelle de base du type × variation aléatoire. */
    public double finalScale() {
        return kind.baseScale() * scale;
    }
}
