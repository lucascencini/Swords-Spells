package com.minidofus.view.fx;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Suite d'images jouée à un endroit du monde (tornade, éclair, potion…). L'image est ancrée par le bas
 * (posée au sol) ou par son centre ; elle peut boucler sur une partie des images, s'élever et s'estomper.
 */
public final class FrameEffect implements Effect {

    private final List<BufferedImage> frames;
    private final double x;
    private final double y;
    private final double height;
    private final double fps;
    private final double duration;
    private final boolean bottomAnchor;
    private final int loopFrom;
    private final int loopTo;
    private double rise;
    private double fadeIn = 0.08;
    private double fadeOut = 0.2;
    private double delay;
    private double elapsed;

    /**
     * @param height   hauteur affichée (unités du monde)
     * @param loopFrom images [loopFrom, loopTo) répétées tant que la durée n'est pas atteinte ; les suivantes
     *                 sont jouées une fois à la fin (dissipation)
     */
    public FrameEffect(List<BufferedImage> frames, double x, double y, double height, double fps, double duration,
                       boolean bottomAnchor, int loopFrom, int loopTo) {
        this.frames = frames;
        this.x = x;
        this.y = y;
        this.height = height;
        this.fps = fps;
        this.duration = duration;
        this.bottomAnchor = bottomAnchor;
        this.loopFrom = loopFrom;
        this.loopTo = loopTo;
    }

    /** Joue toutes les images une fois, sans boucle. */
    public static FrameEffect once(List<BufferedImage> frames, double x, double y, double height, double fps, boolean bottom) {
        return new FrameEffect(frames, x, y, height, fps, frames.size() / fps, bottom, 0, 0);
    }

    public FrameEffect rise(double distance) {
        this.rise = distance;
        return this;
    }

    public FrameEffect fades(double in, double out) {
        this.fadeIn = in;
        this.fadeOut = out;
        return this;
    }

    public FrameEffect delay(double seconds) {
        this.delay = seconds;
        return this;
    }

    public double duration() {
        return duration;
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

    private BufferedImage current() {
        int tail = frames.size() - loopTo;
        int n = (int) (elapsed * fps);
        if (loopTo <= loopFrom) return frames.get(Math.min(frames.size() - 1, n));
        double tailStart = duration - tail / fps;
        if (tail > 0 && elapsed >= tailStart) {
            return frames.get(Math.min(frames.size() - 1, loopTo + (int) ((elapsed - tailStart) * fps)));
        }
        if (n < loopFrom) return frames.get(n);
        return frames.get(loopFrom + (n - loopFrom) % (loopTo - loopFrom));
    }

    @Override
    public void drawWorld(Graphics2D g) {
        if (delay > 0) return;
        BufferedImage img = current();
        double t = Math.min(1, elapsed / duration);
        double a = Math.min(1, Math.min(fadeIn > 0 ? elapsed / fadeIn : 1, fadeOut > 0 ? (duration - elapsed) / fadeOut : 1));
        double h = height;
        double w = h * img.getWidth() / img.getHeight();
        double top = (bottomAnchor ? y - h : y - h / 2) - rise * t;
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, a)));
        g.drawImage(img, (int) Math.round(x - w / 2), (int) Math.round(top), (int) Math.round(w), (int) Math.round(h), null);
        g.setComposite(saved);
    }
}
