package com.minidofus.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Historique du chat (salon et combat), avec retour à la ligne et estompage des vieux messages en combat. */
public final class ChatLog {

    private static final int MAX_LINES = 120;
    /** En combat, un message reste lisible ce temps-là puis s'estompe. */
    private static final double VISIBLE_SECONDS = 12;

    private record Line(String author, String text, long time) { }

    private final List<Line> lines = new ArrayList<>();

    /** {@code author} vide = message du serveur (affiché en italique doré). */
    public synchronized void add(String author, String text) {
        lines.add(new Line(author, text, System.currentTimeMillis()));
        if (lines.size() > MAX_LINES) lines.remove(0);
    }

    public synchronized void clear() {
        lines.clear();
    }

    /** Dessine les derniers messages de bas en haut, à partir de la ligne de base {@code bottom}. */
    public void draw(Graphics2D g, int x, int bottom, int width, int maxRows, double opacity) {
        drawInternal(g, x, bottom, width, maxRows, opacity, false);
    }

    /** Variante pour le combat : les messages anciens disparaissent (sauf pendant la saisie). */
    public void drawFading(Graphics2D g, int x, int bottom, int width, int maxRows, boolean typing) {
        drawInternal(g, x, bottom, width, maxRows, 1.0, !typing);
    }

    private synchronized void drawInternal(Graphics2D g, int x, int bottom, int width, int maxRows, double opacity, boolean fade) {
        Font body = UiTheme.BODY.deriveFont(12.5f);
        Font bold = UiTheme.BODY_BOLD.deriveFont(12.5f);
        long now = System.currentTimeMillis();
        int y = bottom;
        int rows = 0;
        for (int i = lines.size() - 1; i >= 0 && rows < maxRows; i--) {
            Line l = lines.get(i);
            double age = (now - l.time()) / 1000.0;
            double alpha = opacity;
            if (fade) {
                if (age > VISIBLE_SECONDS + 1.5) break;
                if (age > VISIBLE_SECONDS) alpha *= 1 - (age - VISIBLE_SECONDS) / 1.5;
            }
            boolean system = l.author().isEmpty();
            String prefix = system ? "" : l.author() + " : ";
            List<String> wrapped = wrap(g.getFontMetrics(body), prefix + l.text(), width);
            for (int k = wrapped.size() - 1; k >= 0 && rows < maxRows; k--) {
                String row = wrapped.get(k);
                int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
                Color outline = new Color(0, 0, 0, (int) (a * 0.75));
                if (k == 0 && !system) {
                    String name = prefix.length() <= row.length() ? prefix : row;
                    UiTheme.drawOutlinedLeft(g, name, bold, x, y, UiTheme.withAlpha(UiTheme.GOLD_LIGHT, a), outline);
                    int nw = g.getFontMetrics(bold).stringWidth(name);
                    UiTheme.drawOutlinedLeft(g, row.substring(name.length()), body, x + nw, y, UiTheme.withAlpha(UiTheme.PARCHMENT, a), outline);
                } else {
                    UiTheme.drawOutlinedLeft(g, row, system ? body.deriveFont(Font.ITALIC) : body, x, y,
                            UiTheme.withAlpha(system ? UiTheme.TEXT_DIM.brighter() : UiTheme.PARCHMENT, a), outline);
                }
                y -= 17;
                rows++;
            }
        }
    }

    private static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
