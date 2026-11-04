package com.minidofus.view;

import com.minidofus.model.Cell;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cases à colorer sur le sol, reconstruites uniquement quand l'état ou le survol change. */
public final class Highlights {

    /** Styles de surbrillance, du plus discret au plus visible. */
    public enum Style {
        REACH(new Color(46, 204, 113), 0.30, 0.55),
        PATH(new Color(120, 255, 160), 0.55, 0.95),
        RANGE(new Color(255, 160, 30), 0.30, 0.62),
        /** À portée mais hors de la ligne de vue. */
        RANGE_BLOCKED(new Color(90, 90, 100), 0.30, 0.45),
        ALLY_TARGET(new Color(46, 204, 113), 0.30, 0.65),
        RANGE_ENEMY(new Color(255, 120, 40), 0.45, 0.85),
        IMPACT(new Color(255, 107, 53), 0.50, 0.95),
        IMPACT_ENEMY(new Color(231, 76, 60), 0.65, 1.0),
        SELF_HEAL(new Color(46, 204, 113), 0.55, 0.95),
        SELF_BUFF(new Color(52, 152, 219), 0.55, 0.95);

        final Color color;
        final double fill;
        final double border;

        Style(Color color, double fill, double border) {
            this.color = color;
            this.fill = fill;
            this.border = border;
        }
    }

    private final Map<Cell, Style> cells = new LinkedHashMap<>();
    private Cell hover;
    private String label;
    private Color labelColor = Color.WHITE;
    private Cell labelCell;

    public void clear() {
        cells.clear();
        label = null;
        labelCell = null;
    }

    public void put(Cell cell, Style style) {
        cells.put(cell, style);
    }

    public Map<Cell, Style> cells() {
        return cells;
    }

    public void setHover(Cell hover) {
        this.hover = hover;
    }

    public Cell hover() {
        return hover;
    }

    public void setLabel(Cell cell, String text) {
        setLabel(cell, text, Color.WHITE);
    }

    public void setLabel(Cell cell, String text, Color color) {
        this.labelCell = cell;
        this.label = text;
        this.labelColor = color;
    }

    public Color labelColor() {
        return labelColor;
    }

    public String label() {
        return label;
    }

    public Cell labelCell() {
        return labelCell;
    }
}
