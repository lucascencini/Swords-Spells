package com.minidofus.view;

import com.minidofus.engine.Assets;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Ciel animé d'une carte illustrée, dessiné derrière l'illustration (visible là où elle est transparente).
 * Les images du ciel sont empilées verticalement dans un seul fichier ; on passe de l'une à l'autre par un
 * fondu très lent, en aller-retour pour éviter tout saut. L'image fondue n'est recalculée que quelques fois
 * par seconde : les nuages bougent trop lentement pour que cela se voie.
 */
final class SkyLayer {

    /** Durée du fondu d'une image du ciel à la suivante, en secondes. */
    private static final double SECONDS_PER_FRAME = 2.6;
    /** Recalcul de l'image fondue (secondes). */
    private static final double REFRESH = 0.1;

    private final BufferedImage[] frames;
    private final BufferedImage blended;
    private final Color topColor;
    private double clock;
    private double sinceRefresh = REFRESH;

    SkyLayer(Assets assets, String name, int frameCount) {
        BufferedImage strip = assets.image("backdrops/" + name);
        int n = Math.max(1, frameCount);
        int h = strip.getHeight() / n;
        frames = new BufferedImage[n];
        for (int i = 0; i < n; i++) frames[i] = strip.getSubimage(0, i * h, strip.getWidth(), h);
        blended = Assets.createImage(strip.getWidth(), h);
        topColor = averageTopRow(frames[0]);
    }

    /** Couleur du haut du ciel, pour remplir l'écran au-dessus de l'image. */
    Color topColor() { return topColor; }

    void update(double dt) {
        clock += dt;
        sinceRefresh += dt;
    }

    /**
     * Dessine le ciel en coordonnées du monde : largeur {@code width}, bas posé sur {@code bottomY}, centré
     * en x sur {@code centerX}.
     */
    void draw(Graphics2D g, double centerX, double bottomY, double width) {
        if (sinceRefresh >= REFRESH) {
            sinceRefresh = 0;
            refresh();
        }
        double height = width * blended.getHeight() / blended.getWidth();
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(blended, (int) Math.round(centerX - width / 2), (int) Math.round(bottomY - height),
                (int) Math.round(width), (int) Math.round(height), null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    /** Rectangle occupé par le ciel (pour savoir où remplir au-dessus). */
    Rectangle2D bounds(double centerX, double bottomY, double width) {
        double height = width * blended.getHeight() / blended.getWidth();
        return new Rectangle2D.Double(centerX - width / 2, bottomY - height, width, height);
    }

    private void refresh() {
        int n = frames.length;
        if (n == 1) {
            draw(frames[0], 0, 1f);
            return;
        }
        double cycle = 2.0 * (n - 1);                    // aller-retour : 0 → n-1 → 0
        double pos = (clock / SECONDS_PER_FRAME) % cycle;
        if (pos > n - 1) pos = cycle - pos;
        int a = (int) Math.floor(pos);
        int b = Math.min(n - 1, a + 1);
        float t = (float) (pos - a);
        t = t * t * (3 - 2 * t);                          // fondu adouci
        draw(frames[a], b, t);
    }

    private void draw(BufferedImage base, int next, float t) {
        Graphics2D g = blended.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(base, 0, 0, null);
        if (t > 0) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
            g.drawImage(frames[next], 0, 0, null);
        }
        g.dispose();
    }

    private static Color averageTopRow(BufferedImage img) {
        long r = 0;
        long gr = 0;
        long b = 0;
        int count = 0;
        for (int x = 0; x < img.getWidth(); x += 4) {
            for (int y = 0; y < 6; y++) {
                int c = img.getRGB(x, y);
                r += (c >> 16) & 255;
                gr += (c >> 8) & 255;
                b += c & 255;
                count++;
            }
        }
        return new Color((int) (r / count), (int) (gr / count), (int) (b / count));
    }
}
