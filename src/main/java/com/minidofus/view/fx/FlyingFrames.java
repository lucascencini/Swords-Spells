package com.minidofus.view.fx;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Créature animée qui vole d'un point à un autre en arc (corbeau) : images de battement d'ailes en boucle,
 * retournée quand elle part vers la gauche. La progression est pilotée de l'extérieur ({@link #setProgress}).
 */
public final class FlyingFrames implements Effect {

    private final List<BufferedImage> frames;
    private final double x0;
    private final double y0;
    private final double x1;
    private final double y1;
    private final double arc;
    private final double height;
    private final double fps;
    private double progress;
    private double clock;

    /** Les images regardent vers la droite. */
    public FlyingFrames(List<BufferedImage> frames, double x0, double y0, double x1, double y1, double arc,
                        double height, double fps) {
        this.frames = frames;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.arc = arc;
        this.height = height;
        this.fps = fps;
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
        BufferedImage img = frames.get((int) (clock * fps) % frames.size());
        double x = x0 + (x1 - x0) * progress;
        double y = y0 + (y1 - y0) * progress - Math.sin(progress * Math.PI) * arc;
        double h = height;
        double w = h * img.getWidth() / img.getHeight();
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        if (x1 < x0) g.scale(-1, 1);
        // léger piqué sur la fin : la tête s'incline vers la cible
        g.rotate((progress - 0.5) * 0.5 * Math.signum(y1 - y0 + 1e-9));
        g.drawImage(img, (int) Math.round(-w / 2), (int) Math.round(-h / 2), (int) Math.round(w), (int) Math.round(h), null);
        g.setTransform(saved);
    }
}
