package com.minidofus.ui;

import com.minidofus.model.Team;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Couleurs, polices et primitives de dessin partagées par toute l'interface. */
public final class UiTheme {

    private UiTheme() { }

    public static final Color GOLD = new Color(0xC9A227);
    public static final Color GOLD_LIGHT = new Color(0xF0D060);
    public static final Color BRONZE = new Color(0x8A6D3B);
    public static final Color PARCHMENT = new Color(0xF5F0E6);
    public static final Color TEXT_DIM = new Color(0xB8A88C);
    public static final Color PANEL_TOP = new Color(40, 31, 20, 238);
    public static final Color PANEL_BOTTOM = new Color(20, 15, 10, 244);
    public static final Color HP_BACK = new Color(0x1A120C);
    public static final Color PA_COLOR = new Color(0x3B9EFF);
    public static final Color PM_COLOR = new Color(0x4CC46A);
    public static final Color DAMAGE = new Color(0xFF4D4D);
    public static final Color DAMAGE_HEAVY = new Color(0xFFB020);
    public static final Color HEAL = new Color(0x2ECC71);
    public static final Color SHIELD = new Color(0x88CCFF);

    public static final Color POISON = new Color(0x7BD34A);
    public static final Color BEES = new Color(0xF2C94C);
    public static final Color SILENCE = new Color(0xC6B5E8);

    /** Couleur d'équipe (anneaux, cartes, frise). */
    public static Color teamColor(Team team) {
        return team == Team.RED ? new Color(0xE74C3C) : new Color(0x3498DB);
    }

    /** Variante claire, lisible sur fond sombre. */
    public static Color teamTextColor(Team team) {
        return team == Team.RED ? new Color(0xF0A090) : new Color(0x9CC4F0);
    }

    // Polices : on prend la première disponible sur la machine, avec repli sur les polices logiques Java.
    public static final Font TITLE = pick(Font.BOLD, 18, "Cinzel", "Georgia", "Book Antiqua", "Palatino Linotype", "Serif");
    public static final Font BODY = pick(Font.PLAIN, 13, "Segoe UI", "Nunito", "Helvetica Neue", "Arial", "SansSerif");
    public static final Font BODY_BOLD = BODY.deriveFont(Font.BOLD);

    private static Font pick(int style, int size, String... families) {
        Set<String> available = new HashSet<>();
        try {
            available.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        } catch (RuntimeException ignored) {
            // pas d'accès aux polices système : polices logiques
        }
        for (String f : families) {
            if (available.contains(f)) return new Font(f, style, size);
        }
        return new Font(families[families.length - 1], style, size);
    }

    public static void enableQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Panneau sombre arrondi à bordure dorée (bordure plus claire si {@code highlighted}). */
    public static void panel(Graphics2D g, double x, double y, double w, double h, double radius, boolean highlighted) {
        Shape shape = new RoundRectangle2D.Double(x, y, w, h, radius, radius);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(x + 1, y + 4, w, h, radius, radius));
        g.setPaint(new GradientPaint(0, (float) y, PANEL_TOP, 0, (float) (y + h), PANEL_BOTTOM));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(highlighted ? GOLD_LIGHT : BRONZE);
        g.draw(shape);
        if (highlighted) {
            g.setColor(new Color(232, 197, 71, 70));
            g.setStroke(new BasicStroke(5f));
            g.draw(new RoundRectangle2D.Double(x - 2, y - 2, w + 4, h + 4, radius + 4, radius + 4));
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** Texte avec contour, centré horizontalement sur x, ligne de base en y. */
    public static void drawOutlinedCentered(Graphics2D g, String text, Font font, double x, double y, Color fill, Color outline) {
        GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
        double w = gv.getLogicalBounds().getWidth();
        Shape shape = gv.getOutline((float) (x - w / 2), (float) y);
        g.setStroke(new BasicStroke(Math.max(2.5f, font.getSize2D() / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline);
        g.draw(shape);
        g.setColor(fill);
        g.fill(shape);
        g.setStroke(new BasicStroke(1f));
    }

    /** Texte avec contour, aligné à gauche sur x. */
    public static void drawOutlinedLeft(Graphics2D g, String text, Font font, double x, double y, Color fill, Color outline) {
        GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
        double w = gv.getLogicalBounds().getWidth();
        drawOutlinedCentered(g, text, font, x + w / 2, y, fill, outline);
    }

    public static void drawCentered(Graphics2D g, String text, Font font, double cx, double baseline, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baseline);
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
