package com.minidofus.view.fx;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Sprite en vol selon une trajectoire en cloche. Sa progression est pilotée de l'extérieur. */
public final class Projectile implements Effect {

    private final BufferedImage image;
    private final double fromX;
    private final double fromY;
    private final double toX;
    private final double toY;
    private final double arc;
    private final double scale;
    private final boolean orientAlongPath;
    private double t;
    private boolean finished;

    public Projectile(BufferedImage image, double fromX, double fromY, double toX, double toY,
                      double arc, double scale, boolean orientAlongPath) {
        this.image = image;
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.arc = arc;
        this.scale = scale;
        this.orientAlongPath = orientAlongPath;
    }

    public void setProgress(double progress) {
        this.t = progress;
        if (progress >= 1) finished = true;
    }

    @Override
    public boolean update(double dt) {
        return finished;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        double x = fromX + (toX - fromX) * t;
        double y = fromY + (toY - fromY) * t - Math.sin(t * Math.PI) * arc;
        // dérivée de la trajectoire pour orienter la flamme dans le sens du vol
        double vx = toX - fromX;
        double vy = (toY - fromY) - Math.cos(t * Math.PI) * Math.PI * arc;
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        if (orientAlongPath) g.rotate(Math.atan2(vy, vx) - Math.PI); // la flamme du sprite pointe vers la gauche
        g.scale(scale, scale);
        g.drawImage(image, -image.getWidth() / 2, -image.getHeight() / 2, null);
        g.setTransform(saved);
    }
}
