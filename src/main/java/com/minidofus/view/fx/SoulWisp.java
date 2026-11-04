package com.minidofus.view.fx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;

/** Âme qui s'élève en ondulant au-dessus d'un combattant tombé. */
public final class SoulWisp implements Effect {

    private static final double DURATION = 1.6;
    private final double x;
    private final double y;
    private final Color color;
    private double elapsed;

    public SoulWisp(double x, double y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    @Override
    public boolean update(double dt) {
        elapsed += dt;
        return elapsed >= DURATION;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        double t = elapsed / DURATION;
        for (int i = 0; i < 3; i++) {
            double k = Math.max(0, t - i * 0.08);
            double px = x + Math.sin(k * 9 + i) * 8;
            double py = y - 30 - k * 90;
            float r = (float) (9 - i * 2.5);
            int alpha = (int) (200 * (1 - k) * (i == 0 ? 1 : 0.6));
            g.setPaint(new RadialGradientPaint((float) px, (float) py, r * 2, new float[] {0f, 1f},
                    new Color[] {new Color(255, 255, 255, Math.max(0, alpha)), new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)}));
            g.fill(new Ellipse2D.Double(px - r * 2, py - r * 2, r * 4, r * 4));
        }
    }
}
