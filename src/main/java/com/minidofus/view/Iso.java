package com.minidofus.view;

import java.awt.geom.Point2D;

/**
 * Projection isométrique. Repère « monde » : origine au centre de la case (0, 0), +x de la grille
 * vers le bas-droite de l'écran, +y vers le bas-gauche. Les valeurs correspondent aux tuiles
 * normalisées par {@code tools/build_assets.py} (72 px de large, centre du losange à y = 24).
 */
public final class Iso {

    /** Demi-pas horizontal et vertical entre deux cases (légèrement inférieur à la tuile pour éviter les jours). */
    public static final double HALF_W = 35;
    public static final double HALF_H = 21;
    /** Point de la tuile qui correspond au centre de la case. */
    public static final int TILE_ANCHOR_X = 36;
    public static final int TILE_ANCHOR_Y = 24;

    private Iso() { }

    public static double worldX(double gx, double gy) {
        return (gx - gy) * HALF_W;
    }

    public static double worldY(double gx, double gy) {
        return (gx + gy) * HALF_H;
    }

    /** Coordonnées de grille fractionnaires correspondant à un point du monde. */
    public static Point2D.Double toGrid(double wx, double wy) {
        double a = wx / HALF_W;
        double b = wy / HALF_H;
        return new Point2D.Double((a + b) / 2, (b - a) / 2);
    }
}
