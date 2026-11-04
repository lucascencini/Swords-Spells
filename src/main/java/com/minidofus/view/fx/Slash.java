package com.minidofus.view.fx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.QuadCurve2D;

/** Entaille blanche d'une attaque au corps à corps. */
public final class Slash implements Effect {

    private static final double DURATION = 0.25;
    private final double x;
    private final double y;
    private final boolean flip;
    private double elapsed;

    public Slash(double x, double y, boolean flip) {
        this.x = x;
        this.y = y;
        this.flip = flip;
    }

    @Override
    public boolean update(double dt) {
        elapsed += dt;
        return elapsed >= DURATION;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        double t = Math.min(1, elapsed / DURATION);
        double s = 1 + t * 0.6;
        double dir = flip ? -1 : 1;
        Stroke saved = g.getStroke();
        int alpha = (int) (255 * (1 - t));
        QuadCurve2D curve = new QuadCurve2D.Double(
                x - 18 * s * dir, y - 30 * s, x + 2 * dir, y - 46 * s, x + 18 * s * dir, y - 18 * s);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 200, 120, alpha / 3));
        g.draw(curve);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, alpha));
        g.draw(curve);
        g.setStroke(saved);
    }
}
