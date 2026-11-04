package com.minidofus.view.fx;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Sprite qui grossit en s'estompant (explosion, aura arcanique). */
public final class Burst implements Effect {

    private final BufferedImage image;
    private final double x;
    private final double y;
    private final double startScale;
    private final double endScale;
    private final double duration;
    private double delay;
    private double elapsed;

    public Burst(BufferedImage image, double x, double y, double startScale, double endScale, double duration, double delay) {
        this.image = image;
        this.x = x;
        this.y = y;
        this.startScale = startScale;
        this.endScale = endScale;
        this.duration = duration;
        this.delay = delay;
    }

    @Override
    public boolean update(double dt) {
        if (delay > 0) {
            delay -= dt;
            return false;
        }
        elapsed += dt;
        return elapsed >= duration;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        if (delay > 0) return;
        double t = Math.min(1, elapsed / duration);
        double s = startScale + (endScale - startScale) * t;
        float alpha = (float) (t < 0.25 ? 1 : 1 - (t - 0.25) / 0.75);
        Composite savedC = g.getComposite();
        AffineTransform saved = g.getTransform();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        g.translate(x, y);
        g.scale(s, s);
        g.drawImage(image, -image.getWidth() / 2, -image.getHeight() / 2, null);
        g.setTransform(saved);
        g.setComposite(savedC);
    }
}
