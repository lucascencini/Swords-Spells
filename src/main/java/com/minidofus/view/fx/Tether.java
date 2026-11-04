package com.minidofus.view.fx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

/** Chaîne du grappin : s'étire du lanceur vers la cible, puis disparaît. */
public final class Tether implements Effect {

    private final double fromX;
    private final double fromY;
    private final double toX;
    private final double toY;
    private double t;
    private boolean finished;

    public Tether(double fromX, double fromY, double toX, double toY) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
    }

    public void setProgress(double progress) {
        t = progress;
        if (progress >= 1) finished = true;
    }

    @Override
    public boolean update(double dt) {
        return finished;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        if (finished) return;
        double ex = fromX + (toX - fromX) * Math.min(1, t * 1.6);
        double ey = fromY + (toY - fromY) * Math.min(1, t * 1.6);
        double len = Math.hypot(ex - fromX, ey - fromY);
        int links = Math.max(1, (int) (len / 7));
        Stroke saved = g.getStroke();
        g.setStroke(new BasicStroke(1.6f));
        for (int i = 0; i <= links; i++) {
            double k = i / (double) links;
            double x = fromX + (ex - fromX) * k;
            double y = fromY + (ey - fromY) * k;
            g.setColor(new Color(60, 60, 70, 200));
            g.fill(new Ellipse2D.Double(x - 3, y - 2, 6, 4));
            g.setColor(new Color(200, 205, 215));
            g.draw(new Ellipse2D.Double(x - 3, y - 2, 6, 4));
        }
        g.setStroke(saved);
    }
}
