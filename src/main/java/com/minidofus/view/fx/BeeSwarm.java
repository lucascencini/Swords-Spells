package com.minidofus.view.fx;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Nuée d'abeilles qui file du lanceur vers la cible en un nuage désordonné, puis tourbillonne autour d'elle
 * (la nuée qui reste ensuite autour du combattant est dessinée par sa vue tant que le statut dure).
 */
public final class BeeSwarm implements Effect {

    private static final int COUNT = 14;

    private final List<BufferedImage> bees;
    private final double x0;
    private final double y0;
    private final double x1;
    private final double y1;
    private final double[] offset = new double[COUNT];
    private final double[] speed = new double[COUNT];
    private final double[] lag = new double[COUNT];
    private double progress;
    private double clock;

    public BeeSwarm(List<BufferedImage> bees, double x0, double y0, double x1, double y1) {
        this.bees = bees;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        Random rng = new Random(11);
        for (int i = 0; i < COUNT; i++) {
            offset[i] = rng.nextDouble() * Math.PI * 2;
            speed[i] = 7 + rng.nextDouble() * 6;
            lag[i] = rng.nextDouble() * 0.25;
        }
    }

    public void setProgress(double p) {
        this.progress = p;
    }

    @Override
    public boolean update(double dt) {
        clock += dt;
        return progress >= 1;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        if (progress >= 1) return;
        for (int i = 0; i < COUNT; i++) {
            double p = Math.max(0, Math.min(1, (progress - lag[i]) / (1 - lag[i])));
            double cx = x0 + (x1 - x0) * p;
            double cy = y0 + (y1 - y0) * p - Math.sin(p * Math.PI) * 30;
            double spread = 10 + 14 * Math.sin(p * Math.PI) + 16 * Math.max(0, p - 0.7) / 0.3;
            double a = offset[i] + clock * speed[i];
            double bx = cx + Math.cos(a) * spread;
            double by = cy + Math.sin(a * 1.3) * spread * 0.55;
            BufferedImage img = bees.get(i % bees.size());
            double s = 0.55 + 0.1 * Math.sin(clock * 40 + i);                // battement d'ailes
            int w = (int) Math.round(img.getWidth() * s);
            int h = (int) Math.round(img.getHeight() * s);
            g.drawImage(img, (int) Math.round(bx - w / 2.0), (int) Math.round(by - h / 2.0), w, h, null);
        }
    }
}
