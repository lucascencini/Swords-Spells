package com.minidofus.view;

import com.minidofus.engine.Assets;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Premier plan d'une carte illustrée, dessiné par-dessus la scène dans les coins bas de l'écran : éléments
 * flous (hors de la mise au point) qui donnent de la profondeur, avec un léger décalage quand la caméra se
 * déplace (parallaxe), un balancement pour le feuillage et, au désert, un voile de poussière qui dérive.
 */
final class ForegroundLayer {

    /** Hauteur des éléments de coin, en part de la hauteur de l'écran. */
    private static final double CORNER_HEIGHT = 0.32;
    /** Parallaxe : le premier plan bouge plus vite que la scène. */
    private static final double PARALLAX = 0.35;

    private final BufferedImage left;
    private final BufferedImage right;
    private final BufferedImage dust;
    private final boolean sway;
    private double clock;
    /** Copies mises à l'échelle (redimensionner à chaque image coûte cher en rendu logiciel). */
    private BufferedImage leftScaled;
    private BufferedImage rightScaled;
    private BufferedImage dustScaled;

    ForegroundLayer(Assets assets, String name, boolean dust, boolean sway) {
        this.left = assets.image("backdrops/" + name + "_left");
        this.right = assets.image("backdrops/" + name + "_right");
        this.dust = dust ? assets.image("backdrops/dust") : null;
        this.sway = sway;
    }

    void update(double dt) {
        clock += dt;
    }

    /**
     * @param panX  déplacement de la scène à l'écran par rapport au cadrage initial (pixels)
     * @param zoom  zoom courant / zoom du cadrage initial
     */
    void draw(Graphics2D g, int width, int height, double panX, double panY, double zoom) {
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (dust != null) drawDust(g, width, height, panX);
        double grow = Math.max(0.85, Math.min(1.3, 1 + (zoom - 1) * 0.25));
        double h = height * CORNER_HEIGHT * grow;
        double dx = panX * PARALLAX;
        double dy = Math.max(-h * 0.2, panY * PARALLAX * 0.5);
        int hh = (int) Math.round(h / 4) * 4;                          // paliers : peu de recalculs en zoomant
        if (leftScaled == null || leftScaled.getHeight() != hh) {
            leftScaled = scaled(left, hh);
            rightScaled = scaled(right, hh);
        }
        drawCorner(g, leftScaled, -h * 0.12 + dx, height + h * 0.06 + dy, false);
        drawCorner(g, rightScaled, width + h * 0.12 + dx, height + h * 0.06 + dy, true);
        if (interpolation != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
    }

    /** Élément ancré par son coin bas (gauche ou droit) en ({@code x}, {@code bottom}). */
    private void drawCorner(Graphics2D g, BufferedImage img, double x, double bottom, boolean fromRight) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (!sway) {
            g.drawImage(img, (int) Math.round(fromRight ? x - w : x), (int) Math.round(bottom - h), null);
            return;
        }
        AffineTransform saved = g.getTransform();
        g.translate(x, bottom);
        g.rotate((fromRight ? -1 : 1) * Math.sin(clock * 0.8 + (fromRight ? 1.7 : 0)) * 0.018);
        g.drawImage(img, fromRight ? -w : 0, -h, null);
        g.setTransform(saved);
    }

    private static BufferedImage scaled(BufferedImage img, int h) {
        int w = Math.max(1, (int) Math.round((double) h * img.getWidth() / img.getHeight()));
        BufferedImage out = Assets.createImage(w, Math.max(1, h));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Voile de poussière sur la moitié basse de l'écran, qui dérive lentement vers la gauche. */
    private void drawDust(Graphics2D g, int width, int height, double panX) {
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        int h = (int) Math.round(height * 0.55);
        if (dustScaled == null || dustScaled.getHeight() != h) dustScaled = scaled(dust, h);
        int w = dustScaled.getWidth();
        double offset = ((clock * 22 - panX * 0.6) % w + w) % w;
        int y = height - h;
        for (int x = (int) Math.round(-offset); x < width; x += w) g.drawImage(dustScaled, x, y, null);
        g.setComposite(saved);
    }
}
