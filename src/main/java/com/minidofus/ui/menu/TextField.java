package com.minidofus.ui.menu;

import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/** Champ de saisie d'une ligne dessiné en Java2D (pseudo, adresse, mot de passe, chat). */
public final class TextField {

    private final String placeholder;
    private final int maxLength;
    private final boolean masked;
    private final StringBuilder value = new StringBuilder();
    private Rectangle bounds = new Rectangle();
    private boolean focused;
    private double clock;

    public TextField(String placeholder, int maxLength, boolean masked) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.masked = masked;
    }

    public void setBounds(int x, int y, int w, int h) {
        bounds = new Rectangle(x, y, w, h);
    }

    public Rectangle bounds() { return bounds; }
    public String text() { return value.toString(); }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) { this.focused = focused; }

    public void setText(String text) {
        value.setLength(0);
        insert(text == null ? "" : text);
    }

    /** Ajoute du texte au curseur (frappe ou collage), en ignorant les caractères de contrôle. */
    public void insert(String text) {
        for (char c : text.toCharArray()) {
            if (value.length() >= maxLength) break;
            if (!Character.isISOControl(c)) value.append(c);
        }
    }

    public void backspace() {
        if (value.length() > 0) value.setLength(value.length() - 1);
    }

    public void update(double dt) {
        clock += dt;
    }

    public void draw(Graphics2D g, int mouseX, int mouseY) {
        Rectangle r = bounds;
        Shape shape = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12);
        g.setColor(new Color(10, 8, 5, 210));
        g.fill(shape);
        g.setStroke(new BasicStroke(focused ? 2f : 1.5f));
        g.setColor(focused ? UiTheme.GOLD_LIGHT : r.contains(mouseX, mouseY) ? UiTheme.GOLD : UiTheme.BRONZE);
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        g.setFont(UiTheme.BODY.deriveFont(15f));
        FontMetrics fm = g.getFontMetrics();
        int baseline = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
        String shown = masked ? "•".repeat(value.length()) : value.toString();
        // le texte trop long défile : on montre la fin
        while (fm.stringWidth(shown) > r.width - 24 && shown.length() > 1) shown = shown.substring(1);
        if (shown.isEmpty() && !focused) {
            g.setColor(UiTheme.TEXT_DIM);
            g.drawString(placeholder, r.x + 12, baseline);
        } else {
            g.setColor(UiTheme.PARCHMENT);
            g.drawString(shown, r.x + 12, baseline);
        }
        if (focused && (clock % 1.0) < 0.55) {
            int cx = r.x + 12 + fm.stringWidth(shown) + 1;
            g.setColor(UiTheme.GOLD_LIGHT);
            g.fillRect(cx, r.y + 9, 2, r.height - 18);
        }
    }
}
