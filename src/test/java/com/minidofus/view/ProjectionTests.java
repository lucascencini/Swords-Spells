package com.minidofus.view;

import com.minidofus.Check;

import java.awt.geom.Point2D;
import java.util.Properties;

/** Projection grille → monde : isométrie identique à l'ancienne, perspective calée sur les coins, inverse exact. */
public final class ProjectionTests {

    private ProjectionTests() { }

    public static void run() {
        Check.suite("Projection (isométrie et perspective)");
        Projection iso = Projection.ISO;
        boolean same = true;
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                same &= Math.abs(iso.x(x, y) - Iso.worldX(x, y)) < 1e-9 && Math.abs(iso.y(x, y) - Iso.worldY(x, y)) < 1e-9;
                same &= Math.abs(iso.scaleAt(x, y) - 1) < 1e-9;
            }
        }
        Check.that(same, "l'isométrie donne les mêmes positions qu'avant, sans réduction");

        Point2D top = new Point2D.Double(10, -40);
        Point2D right = new Point2D.Double(560, 250);
        Point2D bottom = new Point2D.Double(-15, 640);
        Point2D left = new Point2D.Double(-540, 260);
        Projection p = Projection.fromCorners(top, right, bottom, left, 15);
        Check.that(close(p.point(-0.5, -0.5), top) && close(p.point(14.5, -0.5), right)
                && close(p.point(14.5, 14.5), bottom) && close(p.point(-0.5, 14.5), left),
                "les coins de la grille tombent sur les coins donnés");
        double worst = 0;
        for (double gx = -0.4; gx < 14.5; gx += 0.37) {
            for (double gy = -0.4; gy < 14.5; gy += 0.41) {
                Point2D.Double back = p.toGrid(p.x(gx, gy), p.y(gx, gy));
                worst = Math.max(worst, Math.hypot(back.x - gx, back.y - gy));
            }
        }
        Check.that(worst < 1e-6, "monde → grille est l'inverse exact (sélection des cases à la souris)");
        Check.that(p.scaleAt(0, 0) < p.scaleAt(14, 14), "les cases du fond sont plus petites que celles de devant");

        // les cartes illustrées fournissent bien leurs coins
        for (String map : new String[] {"forest", "desert"}) {
            Properties props = new com.minidofus.engine.Assets().properties("backdrops/" + map + ".properties");
            Check.that(props.getProperty("grid.top") != null && props.getProperty("grid.left") != null,
                    map + " : coins de la grille en perspective");
        }
    }

    private static boolean close(Point2D a, Point2D b) {
        return a.distance(b) < 1e-6;
    }
}
