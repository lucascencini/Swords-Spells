package com.minidofus.ui.menu;

import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.IntConsumer;

/** Sélecteur segmenté : une rangée d'options exclusives (mode, difficulté, taille d'équipe…). */
public final class Choice {

    private final List<String> options;
    private final IntConsumer onChange;
    private int selected;
    private Rectangle bounds = new Rectangle();

    public Choice(List<String> options, int selected, IntConsumer onChange) {
        this.options = options;
        this.selected = selected;
        this.onChange = onChange;
    }

    public void setBounds(int x, int y, int w, int h) {
        bounds = new Rectangle(x, y, w, h);
    }

    public int selected() {
        return selected;
    }

    public boolean press(int x, int y) {
        if (!bounds.contains(x, y)) return false;
        int i = (x - bounds.x) * options.size() / bounds.width;
        select(Math.max(0, Math.min(options.size() - 1, i)));
        return true;
    }

    public void select(int index) {
        if (index == selected) return;
        selected = index;
        onChange.accept(index);
    }

    public void draw(Graphics2D g, int mouseX, int mouseY) {
        Rectangle r = bounds;
        g.setColor(new Color(10, 8, 5, 170));
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        double cell = r.width / (double) options.size();
        for (int i = 0; i < options.size(); i++) {
            double x = r.x + i * cell;
            boolean on = i == selected;
            boolean hover = !on && mouseX >= x && mouseX < x + cell && r.contains(mouseX, mouseY);
            RoundRectangle2D seg = new RoundRectangle2D.Double(x + 3, r.y + 3, cell - 6, r.height - 6, 11, 11);
            if (on) {
                g.setPaint(new GradientPaint(0, r.y, new Color(0xC9A227), 0, r.y + r.height, new Color(0x7A5E14)));
                g.fill(seg);
            } else if (hover) {
                g.setColor(new Color(255, 255, 255, 25));
                g.fill(seg);
            }
            UiTheme.drawCentered(g, options.get(i), UiTheme.BODY_BOLD.deriveFont(13.5f), x + cell / 2, r.getCenterY() + 5,
                    on ? new Color(0x1A1208) : UiTheme.PARCHMENT);
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        g.setStroke(new BasicStroke(1f));
    }
}
