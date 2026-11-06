package com.minidofus.ui.account;

import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Petits outils de dessin partagés par les écrans de compte et de progression. */
final class Ui {

    private Ui() { }

    static void dim(Graphics2D g, int w, int h) {
        g.setColor(new Color(5, 4, 2, 190));
        g.fillRect(0, 0, w, h);
    }

    static void frame(Graphics2D g, Rectangle r, boolean highlighted) {
        g.setColor(new Color(10, 8, 5, highlighted ? 215 : 170));
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        g.setStroke(new BasicStroke(highlighted ? 2.5f : 1.3f));
        g.setColor(highlighted ? UiTheme.GOLD_LIGHT : UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        g.setStroke(new BasicStroke(1f));
    }

    /** Barre de progression (expérience). */
    static void bar(Graphics2D g, double x, double y, double w, double h, double ratio, Color color, String text) {
        g.setColor(UiTheme.HP_BACK);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        g.setPaint(new GradientPaint((float) x, 0, color.darker().darker(), (float) (x + w), 0, color));
        g.fill(new RoundRectangle2D.Double(x, y, Math.max(h, w * Math.max(0, Math.min(1, ratio))), h, h, h));
        g.setColor(UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(x, y, w, h, h, h));
        if (text != null) UiTheme.drawCentered(g, text, UiTheme.BODY_BOLD.deriveFont((float) Math.min(12, h - 2)), x + w / 2, y + h - 3, Color.WHITE);
    }

    static void text(Graphics2D g, String s, Font f, double x, double baseline, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) baseline);
    }

    /** Coupe un texte en lignes de largeur {@code maxW}. */
    static List<String> wrap(Graphics2D g, String text, Font f, int maxW) {
        FontMetrics fm = g.getFontMetrics(f);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(test) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
