package com.minidofus.view.fx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

/** Anneau au sol qui s'élargit (soin, bouclier, début de tour). */
public final class RingPulse implements Effect {

    private final double x;
    private final double y;
    private final Color color;
    private final double duration;
    private double elapsed;

    public RingPulse(double x, double y, Color color, double duration) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.duration = duration;
    }

    @Override
    public boolean update(double dt) {
        elapsed += dt;
        return elapsed >= duration;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        double t = Math.min(1, elapsed / duration);
        double rx = 14 + 30 * t;
        double ry = rx * 0.55;
        Stroke saved = g.getStroke();
        g.setStroke(new BasicStroke((float) (4 * (1 - t) + 1)));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (220 * (1 - t))));
        g.draw(new Ellipse2D.Double(x - rx, y - ry, rx * 2, ry * 2));
        g.setStroke(saved);
    }
}
