package com.minidofus.view;

import com.minidofus.model.map.Biome;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.util.Random;

/** Particules d'ambiance en avant-plan : flocons, poussière de sable, braises, pollen. */
public final class AmbientParticles {

    private final Biome biome;
    private final double[] x;
    private final double[] y;
    private final double[] speed;
    private final double[] size;
    private final double[] phase;
    private final Random rng = new Random(7);
    private double clock;

    public AmbientParticles(Biome biome) {
        this.biome = biome;
        int count = switch (biome) {
            case SNOW -> 140;
            case DESERT -> 45;
            case DUSK -> 36;
            case MEADOW -> 22;
        };
        x = new double[count];
        y = new double[count];
        speed = new double[count];
        size = new double[count];
        phase = new double[count];
        for (int i = 0; i < count; i++) {
            x[i] = rng.nextDouble();
            y[i] = rng.nextDouble();
            speed[i] = 0.5 + rng.nextDouble();
            size[i] = 0.5 + rng.nextDouble();
            phase[i] = rng.nextDouble() * Math.PI * 2;
        }
    }

    public void update(double dt) {
        clock += dt;
        for (int i = 0; i < x.length; i++) {
            switch (biome) {
                case SNOW -> {
                    y[i] += dt * 0.06 * speed[i];
                    x[i] += dt * 0.012 * Math.sin(clock * 0.8 + phase[i]);
                }
                case DESERT -> {
                    x[i] += dt * 0.08 * speed[i];
                    y[i] += dt * 0.01 * Math.sin(clock + phase[i]);
                }
                case DUSK -> {
                    y[i] -= dt * 0.03 * speed[i];
                    x[i] += dt * 0.01 * Math.sin(clock * 1.3 + phase[i]);
                }
                case MEADOW -> {
                    x[i] += dt * 0.012 * Math.sin(clock * 0.5 + phase[i]);
                    y[i] += dt * 0.008 * Math.cos(clock * 0.4 + phase[i]);
                }
            }
            if (y[i] > 1.02) y[i] -= 1.04;
            if (y[i] < -0.02) y[i] += 1.04;
            if (x[i] > 1.02) x[i] -= 1.04;
            if (x[i] < -0.02) x[i] += 1.04;
        }
    }

    public void draw(Graphics2D g, int w, int h) {
        for (int i = 0; i < x.length; i++) {
            double px = x[i] * w;
            double py = y[i] * h;
            switch (biome) {
                case SNOW -> {
                    double r = 1.2 + size[i] * 1.8;
                    g.setColor(new Color(255, 255, 255, (int) (120 + 80 * size[i] / 1.5)));
                    g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
                }
                case DESERT -> {
                    double r = 0.8 + size[i];
                    g.setColor(new Color(250, 225, 170, 90));
                    g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
                }
                case DUSK -> {
                    float flicker = (float) (0.6 + 0.4 * Math.sin(clock * 6 + phase[i] * 3));
                    float r = (float) (2 + size[i] * 2.5);
                    g.setPaint(new RadialGradientPaint((float) px, (float) py, r * 2.5f, new float[] {0f, 0.3f, 1f},
                            new Color[] {new Color(255, 200, 120, (int) (220 * flicker)), new Color(255, 120, 40, (int) (120 * flicker)),
                                    new Color(255, 80, 20, 0)}));
                    g.fill(new Ellipse2D.Double(px - r * 2.5, py - r * 2.5, r * 5, r * 5));
                }
                case MEADOW -> {
                    float glow = (float) (0.5 + 0.5 * Math.sin(clock * 1.5 + phase[i]));
                    g.setColor(new Color(255, 250, 200, (int) (40 + 70 * glow)));
                    double r = 1 + size[i];
                    g.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
                }
            }
        }
    }
}
