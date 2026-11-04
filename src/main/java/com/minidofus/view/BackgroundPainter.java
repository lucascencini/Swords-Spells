package com.minidofus.view;

import com.minidofus.engine.Assets;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Ciel, nuages et lisière de forêt, peints une seule fois par taille de fenêtre. */
public final class BackgroundPainter {

    private BufferedImage cache;
    private long seed;

    public BufferedImage get(int w, int h, long seed) {
        if (cache == null || cache.getWidth() != w || cache.getHeight() != h || this.seed != seed) {
            cache = paint(w, h, seed);
            this.seed = seed;
        }
        return cache;
    }

    private static BufferedImage paint(int w, int h, long seed) {
        BufferedImage img = Assets.createImage(w, h);
        Graphics2D g = img.createGraphics();
        com.minidofus.ui.UiTheme.enableQuality(g);
        Random rng = new Random(seed);

        g.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[] {0f, 0.32f, 0.5f, 0.72f, 1f},
                new Color[] {new Color(0xA8C8E0), new Color(0xC2DAD0), new Color(0x8FBC7A), new Color(0x5A9A4A), new Color(0x335C2B)}));
        g.fillRect(0, 0, w, h);

        for (int i = 0; i < 7; i++) {
            double cx = rng.nextDouble() * w;
            double cy = 30 + rng.nextDouble() * h * 0.22;
            double rw = 60 + rng.nextDouble() * 70;
            g.setColor(new Color(255, 255, 255, 40 + rng.nextInt(30)));
            g.fill(new Ellipse2D.Double(cx - rw, cy - 16, rw * 2, 32));
            g.fill(new Ellipse2D.Double(cx - rw * 0.3, cy - 30, rw * 1.1, 34));
            g.fill(new Ellipse2D.Double(cx - rw * 0.8, cy - 8, rw * 0.9, 24));
        }

        double horizon = h * 0.5;
        silhouette(g, rng, w, h, horizon, 18, 34, 46, new Color(45, 90, 40, 140));
        silhouette(g, rng, w, h, horizon + 26, 22, 24, 34, new Color(30, 64, 32, 120));

        g.setPaint(new GradientPaint(0, (float) horizon, new Color(0, 0, 0, 0), 0, h, new Color(10, 30, 10, 70)));
        g.fillRect(0, (int) horizon, w, h);

        float r = Math.max(w, h) * 0.75f;
        g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, r, new float[] {0.55f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 110)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void silhouette(Graphics2D g, Random rng, int w, int h, double base, int step, double minH, double varH, Color color) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(0, h);
        p.lineTo(0, base);
        for (double x = 0; x <= w + step; x += step) {
            double treeH = minH + Math.sin(x * 0.03) * 10 + rng.nextDouble() * varH;
            p.lineTo(x, base - treeH);
            p.lineTo(x + step * 0.45, base - treeH * 0.55);
        }
        p.lineTo(w, h);
        p.closePath();
        g.setColor(color);
        g.fill(p);
    }
}
