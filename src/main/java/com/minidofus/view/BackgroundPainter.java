package com.minidofus.view;

import com.minidofus.engine.Assets;
import com.minidofus.model.map.Biome;
import com.minidofus.ui.UiTheme;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Fond d'écran propre à chaque biome, peint une seule fois par taille de fenêtre. */
public final class BackgroundPainter {

    private BufferedImage cache;
    private long cacheKey;

    public BufferedImage get(int w, int h, long seed, Biome biome) {
        long key = seed * 31 + biome.ordinal();
        if (cache == null || cache.getWidth() != w || cache.getHeight() != h || cacheKey != key) {
            cache = paint(w, h, seed, biome);
            cacheKey = key;
        }
        return cache;
    }

    private static BufferedImage paint(int w, int h, long seed, Biome biome) {
        BufferedImage img = Assets.createImage(w, h);
        Graphics2D g = img.createGraphics();
        UiTheme.enableQuality(g);
        Random rng = new Random(seed);
        switch (biome) {
            case MEADOW -> meadow(g, rng, w, h);
            case SNOW -> snow(g, rng, w, h);
            case DESERT -> desert(g, rng, w, h);
            case DUNGEON -> dungeon(g, rng, w, h);
        }
        float r = Math.max(w, h) * 0.75f;
        g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, r, new float[] {0.55f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, biome == Biome.DUNGEON ? 170 : 110)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void sky(Graphics2D g, int w, int h, Color... stops) {
        float[] fractions = new float[stops.length];
        for (int i = 0; i < stops.length; i++) fractions[i] = i / (float) (stops.length - 1);
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, fractions, stops));
        g.fillRect(0, 0, w, h);
    }

    private static void clouds(Graphics2D g, Random rng, int w, int h, Color color, int count) {
        for (int i = 0; i < count; i++) {
            double cx = rng.nextDouble() * w;
            double cy = 30 + rng.nextDouble() * h * 0.22;
            double rw = 60 + rng.nextDouble() * 70;
            g.setColor(UiTheme.withAlpha(color, color.getAlpha() - rng.nextInt(20)));
            g.fill(new Ellipse2D.Double(cx - rw, cy - 16, rw * 2, 32));
            g.fill(new Ellipse2D.Double(cx - rw * 0.3, cy - 30, rw * 1.1, 34));
            g.fill(new Ellipse2D.Double(cx - rw * 0.8, cy - 8, rw * 0.9, 24));
        }
    }

    private static void meadow(Graphics2D g, Random rng, int w, int h) {
        sky(g, w, h, new Color(0xA8C8E0), new Color(0xC2DAD0), new Color(0x8FBC7A), new Color(0x5A9A4A), new Color(0x335C2B));
        clouds(g, rng, w, h, new Color(255, 255, 255, 65), 7);
        double horizon = h * 0.5;
        pines(g, rng, w, h, horizon, 18, 34, 46, new Color(45, 90, 40, 140));
        pines(g, rng, w, h, horizon + 26, 22, 24, 34, new Color(30, 64, 32, 120));
        g.setPaint(new GradientPaint(0, (float) horizon, new Color(0, 0, 0, 0), 0, h, new Color(10, 30, 10, 70)));
        g.fillRect(0, (int) horizon, w, h);
    }

    private static void snow(Graphics2D g, Random rng, int w, int h) {
        sky(g, w, h, new Color(0xB8C7D6), new Color(0xDCE5EE), new Color(0xE9EFF5), new Color(0xC9D5E2), new Color(0x93A6BB));
        clouds(g, rng, w, h, new Color(255, 255, 255, 90), 6);
        double horizon = h * 0.5;
        mountains(g, rng, w, horizon - 10, new Color(0x9DB0C6), new Color(0xF2F6FA));
        pines(g, rng, w, h, horizon + 10, 16, 30, 40, new Color(70, 96, 112, 170));
        pines(g, rng, w, h, horizon + 34, 20, 22, 30, new Color(52, 74, 92, 150));
    }

    private static void desert(Graphics2D g, Random rng, int w, int h) {
        sky(g, w, h, new Color(0xF0B97A), new Color(0xF7DDB0), new Color(0xE9C990), new Color(0xD3A867), new Color(0xA67A45));
        float sx = w * 0.78f;
        float sy = h * 0.2f;
        g.setPaint(new RadialGradientPaint(sx, sy, 160, new float[] {0f, 0.25f, 1f},
                new Color[] {new Color(255, 250, 225, 255), new Color(255, 230, 170, 160), new Color(255, 220, 150, 0)}));
        g.fill(new Ellipse2D.Double(sx - 160, sy - 160, 320, 320));
        double horizon = h * 0.5;
        dunes(g, rng, w, h, horizon, 28, new Color(0xD9B276));
        dunes(g, rng, w, h, horizon + 30, 20, new Color(0xC79B5E));
    }

    private static void dungeon(Graphics2D g, Random rng, int w, int h) {
        sky(g, w, h, new Color(0x16120F), new Color(0x231C17), new Color(0x1C1612), new Color(0x0D0B09));
        // colonnes et arches lointaines
        for (int i = 0; i < 9; i++) {
            double x = (i + 0.5) * w / 9.0 + (rng.nextDouble() - 0.5) * 40;
            double cw = 40 + rng.nextDouble() * 26;
            g.setColor(new Color(42, 35, 30, 200));
            g.fillRect((int) (x - cw / 2), (int) (h * 0.12), (int) cw, (int) (h * 0.7));
            g.setColor(new Color(58, 49, 42, 200));
            g.fillRect((int) (x - cw / 2 - 6), (int) (h * 0.12), (int) cw + 12, 14);
        }
        g.setPaint(new RadialGradientPaint(w / 2f, h * 0.55f, Math.max(w, h) * 0.5f, new float[] {0f, 1f},
                new Color[] {new Color(120, 70, 30, 90), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
    }

    private static void pines(Graphics2D g, Random rng, int w, int h, double base, int step, double minH, double varH, Color color) {
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

    private static void mountains(Graphics2D g, Random rng, int w, double base, Color rock, Color snowCap) {
        double x = -80;
        while (x < w + 80) {
            double width = 180 + rng.nextDouble() * 160;
            double height = 90 + rng.nextDouble() * 90;
            Path2D.Double m = new Path2D.Double();
            m.moveTo(x, base);
            m.lineTo(x + width / 2, base - height);
            m.lineTo(x + width, base);
            m.closePath();
            g.setColor(rock);
            g.fill(m);
            Path2D.Double cap = new Path2D.Double();
            cap.moveTo(x + width / 2, base - height);
            cap.lineTo(x + width / 2 - width * 0.14, base - height * 0.7);
            cap.lineTo(x + width / 2, base - height * 0.76);
            cap.lineTo(x + width / 2 + width * 0.14, base - height * 0.7);
            cap.closePath();
            g.setColor(snowCap);
            g.fill(cap);
            x += width * 0.6;
        }
    }

    private static void dunes(Graphics2D g, Random rng, int w, int h, double base, double amplitude, Color color) {
        double phase = rng.nextDouble() * 10;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(0, h);
        for (int x = 0; x <= w; x += 8) {
            double y = base - amplitude * (0.6 * Math.sin(x * 0.006 + phase) + 0.4 * Math.sin(x * 0.017 + phase * 2));
            p.lineTo(x, y);
        }
        p.lineTo(w, h);
        p.closePath();
        g.setColor(color);
        g.fill(p);
    }
}
