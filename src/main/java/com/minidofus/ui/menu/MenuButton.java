package com.minidofus.ui.menu;

import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

/** Bouton de menu : libellé, sous-titre facultatif, style principal (doré) ou secondaire. */
public final class MenuButton {

    private final String label;
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

    public void draw(Graphics2D g, int mouseX, int mouseY) {
        boolean hover = enabled && bounds.contains(mouseX, mouseY);
        Rectangle r = bounds;
        int lift = hover ? -2 : 0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y + lift, r.width, r.height, 16, 16);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(r.x + 1, r.y + 5, r.width, r.height, 16, 16));
        Color top = primary ? new Color(0xD4AE36) : new Color(0x3A3020);
        Color bottom = primary ? new Color(0x7A5E14) : new Color(0x221A10);
        if (!enabled) {
            top = new Color(0x2A2620);
            bottom = new Color(0x1A1612);
        }
        g.setPaint(new GradientPaint(0, r.y, hover ? top.brighter() : top, 0, r.y + r.height, bottom));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(hover ? UiTheme.GOLD_LIGHT : primary ? UiTheme.GOLD_LIGHT : UiTheme.BRONZE);
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        Color text = !enabled ? UiTheme.TEXT_DIM : primary ? new Color(0x1A1208) : UiTheme.PARCHMENT;
        float size = Math.min(19f, r.height * 0.36f);
        double baseline = r.y + lift + (subtitle == null ? r.height / 2.0 + size / 2.8 : r.height / 2.0 - 2);
        UiTheme.drawCentered(g, label, UiTheme.TITLE.deriveFont(size), r.getCenterX(), baseline, text);
        if (subtitle != null) {
            UiTheme.drawCentered(g, subtitle, UiTheme.BODY.deriveFont(12f), r.getCenterX(), baseline + 19,
                    primary ? new Color(0x3A2A08) : UiTheme.TEXT_DIM);
        }
    }
}
