package com.minidofus.view.fx;

import com.minidofus.ui.UiTheme;
import com.minidofus.view.Camera;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

/** Nombre qui s'élève au-dessus d'un combattant (dégâts en rouge, soins en vert). */
public final class FloatingText implements Effect {

    private static final double DURATION = 1.15;
    private final String text;
    private final double wx;
    private final double wy;
    private final Color color;
    private final float size;
    private double elapsed;

    public FloatingText(String text, double wx, double wy, Color color, float size) {
        this.text = text;
        this.wx = wx;
        this.wy = wy;
        this.color = color;
        this.size = size;
    }

    @Override
    public boolean update(double dt) {
        elapsed += dt;
        return elapsed >= DURATION;
    }

    @Override
    public void drawScreen(Graphics2D g, Camera camera) {
        double t = Math.min(1, elapsed / DURATION);
        double pop = t < 0.18 ? 0.5 + (t / 0.18) * 0.7 : t < 0.3 ? 1.2 - (t - 0.18) / 0.12 * 0.2 : 1.0;
        double rise = 18 + 60 * Math.min(1, t * 1.3);
        Point2D.Double p = camera.toScreen(wx, wy);
        Font font = UiTheme.BODY_BOLD.deriveFont((float) (size * pop));
        int alpha = t < 0.7 ? 255 : (int) (255 * (1 - (t - 0.7) / 0.3));
        UiTheme.drawOutlinedCentered(g, text, font, p.x, p.y - rise,
                new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha), new Color(0, 0, 0, alpha));
    }
}
