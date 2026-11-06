package com.minidofus.ui.menu;

import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.awt.RenderingHints;
import java.awt.RadialGradientPaint;
import java.awt.Composite;
import java.awt.AlphaComposite;
import com.minidofus.engine.Assets;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

/** Bouton de menu : libellé, sous-titre facultatif, style principal (doré) ou secondaire. */
public final class MenuButton {

    private String label;
    private final String subtitle;
    private final boolean primary;
    private final Runnable action;
    private Rectangle bounds = new Rectangle();
    private boolean enabled = true;

    public MenuButton(String label, String subtitle, boolean primary, Runnable action) {
        this.label = label;
        this.subtitle = subtitle;
        this.primary = primary;
        this.action = action;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setBounds(int x, int y, int w, int h) {
        bounds = new Rectangle(x, y, w, h);
    }

    public Rectangle bounds() {
        return bounds;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Déclenche l'action si le point est sur le bouton. */
    public boolean press(int x, int y) {
        if (!enabled || !bounds.contains(x, y)) return false;
        action.run();
        return true;
    }

    /** Images des boutons de pierre (partagées par tous les boutons). */
    private static final Assets STONE = new Assets();

    public void draw(Graphics2D g, int mouseX, int mouseY) {
        boolean hover = enabled && bounds.contains(mouseX, mouseY);
        Rectangle r = bounds;
        int lift = hover ? -2 : 0;
        Composite saved = g.getComposite();
        // halo doré : bouton principal, ou survol
        if (enabled && (primary || hover)) {
            float glow = hover ? 0.55f : 0.32f;
            // halo elliptique : un dégradé circulaire aplati, qui s'éteint avant les bords (aucune arête visible)
            java.awt.geom.AffineTransform saved0 = g.getTransform();
            double ry = r.height * 1.1 / (r.width * 0.62);
            g.translate(r.getCenterX(), r.getCenterY() + lift);
            g.scale(1, ry);
            float rad = (float) (r.width * 0.62);
            g.setPaint(new RadialGradientPaint(0f, 0f, rad, new float[] {0f, 1f},
                    new Color[] {UiTheme.withAlpha(UiTheme.GOLD_LIGHT, (int) (glow * 255)), UiTheme.withAlpha(UiTheme.GOLD_LIGHT, 0)}));
            g.fill(new java.awt.geom.Ellipse2D.Double(-rad, -rad, rad * 2, rad * 2));
            g.setTransform(saved0);
        }
        if (!enabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        BufferedImage stone = STONE.image("ui/stone_button_" + (1 + Math.floorMod(label.hashCode(), 3)));
        drawStretched(g, stone, r.x, r.y + lift, r.width, r.height);
        if (hover) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            drawStretched(g, STONE.tinted(stone, "stone_hover", new Color(255, 230, 170)), r.x, r.y + lift, r.width, r.height);
        }
        g.setComposite(saved);

        Color text = !enabled ? UiTheme.TEXT_DIM : primary || hover ? UiTheme.GOLD_LIGHT : new Color(0xF0DDB4);
        float size = Math.min(19f, r.height * 0.34f);
        double baseline = r.y + lift + (subtitle == null ? r.height / 2.0 + size / 2.8 : r.height / 2.0 - 1);
        UiTheme.drawOutlinedCentered(g, label, UiTheme.TITLE.deriveFont(size), r.getCenterX(), baseline, text,
                new Color(20, 14, 8, 220));
        if (subtitle != null) {
            UiTheme.drawOutlinedCentered(g, subtitle, UiTheme.BODY.deriveFont(Math.min(12f, r.height * 0.2f)),
                    r.getCenterX(), baseline + Math.min(17, r.height * 0.3), new Color(0xD8CCB4), new Color(20, 14, 8, 200));
        }
    }

    /** Flèche de pierre (« ‹ » ou « › ») dans le rectangle donné, éclairée au survol. */
    public static void drawArrow(Graphics2D g, Rectangle r, boolean left, boolean hover) {
        BufferedImage img = left ? STONE.image("ui/stone_round_left") : STONE.mirrored("ui/stone_round_left");
        double h = Math.min(r.height, r.width * img.getHeight() / (double) img.getWidth());
        double w = h * img.getWidth() / img.getHeight();
        double x = r.getCenterX() - w / 2;
        double y = r.getCenterY() - h / 2 - (hover ? 1 : 0);
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        if (hover) {
            Composite c = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g.drawImage(STONE.tinted(img, "arrow_hover", new Color(255, 230, 170)), (int) Math.round(x), (int) Math.round(y),
                    (int) Math.round(w), (int) Math.round(h), null);
            g.setComposite(c);
        }
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    /** Image de l'interface (lierre, décorations). */
    public static BufferedImage uiImage(String name) {
        return STONE.image("ui/" + name);
    }

    /**
     * Image de bouton étirée à la taille voulue sans déformer ses extrémités ornées : les deux bouts gardent
     * leurs proportions, seul le milieu s'allonge.
     */
    public static void drawStretched(Graphics2D g, BufferedImage img, double x, double y, double w, double h) {
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int sw = img.getWidth();
        int sh = img.getHeight();
        double k = h / sh;
        int srcEnd = (int) Math.min(sh * 0.95, sw * 0.3);
        double end = Math.min(srcEnd * k, w * 0.4);
        int xi = (int) Math.round(x);
        int yi = (int) Math.round(y);
        int wi = (int) Math.round(w);
        int hi = (int) Math.round(h);
        int ei = (int) Math.round(end);
        g.drawImage(img, xi, yi, xi + ei, yi + hi, 0, 0, srcEnd, sh, null);
        g.drawImage(img, xi + ei, yi, xi + wi - ei, yi + hi, srcEnd, 0, sw - srcEnd, sh, null);
        g.drawImage(img, xi + wi - ei, yi, xi + wi, yi + hi, sw - srcEnd, 0, sw, sh, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }
}
