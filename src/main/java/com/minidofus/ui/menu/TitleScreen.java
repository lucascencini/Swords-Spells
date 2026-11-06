package com.minidofus.ui.menu;

import com.minidofus.ui.UiTheme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.util.List;
import java.util.function.Consumer;

/**
 * Écran titre. En fond, une vraie partie ordinateur contre ordinateur se joue (fournie par la fenêtre),
 * légèrement assombrie ; au premier plan, le titre et les choix de mode.
 */
public final class TitleScreen implements Screen {

    private final Consumer<Graphics2D> background;
    private final List<MenuButton> buttons;
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;
    private double clock;

    public TitleScreen(Consumer<Graphics2D> background, Runnable versusComputer, Runnable twoPlayers, Runnable online,
                       Runnable quit) {
        this.background = background;
        this.buttons = List.of(
                new MenuButton("Contre l'ordinateur", "Facile, normal ou difficile", true, versusComputer),
                new MenuButton("Jouer en ligne", "Avec vos amis, sur un serveur", false, online),
                new MenuButton("Deux joueurs", "Sur le même PC, chacun son tour", false, twoPlayers),
                new MenuButton("Quitter", null, false, quit));
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int bw = 340;
        int y = (int) (h * 0.42);
        for (int i = 0; i < buttons.size(); i++) {
            int bh = i == buttons.size() - 1 ? 46 : 62;
            buttons.get(i).setBounds((w - bw) / 2, y, bw, bh);
            y += bh + 12;
        }
    }

    @Override
    public void update(double dt) {
        clock += dt;
    }

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        g.setPaint(new RadialGradientPaint(width / 2f, height * 0.45f, Math.max(width, height) * 0.7f,
                new float[] {0f, 0.55f, 1f}, new Color[] {new Color(10, 8, 5, 120), new Color(10, 8, 5, 170), new Color(5, 4, 2, 225)}));
        g.fillRect(0, 0, width, height);

        double drift = Math.sin(clock * 1.2) * 4;
        int titleY = (int) (height * 0.23 + drift);
        UiTheme.drawOutlinedCentered(g, "Spells & Swords", UiTheme.TITLE.deriveFont(64f), width / 2.0, titleY,
                UiTheme.GOLD_LIGHT, new Color(40, 25, 5, 230));
        UiTheme.drawOutlinedCentered(g, "Combat tactique au tour par tour", UiTheme.BODY_BOLD.deriveFont(18f),
                width / 2.0, titleY + 44, UiTheme.PARCHMENT, new Color(0, 0, 0, 200));
        g.setColor(UiTheme.withAlpha(UiTheme.GOLD, 160));
        g.fillRect(width / 2 - 120, titleY + 60, 240, 2);

        drawIvy(g);
        for (MenuButton b : buttons) b.draw(g, mouseX, mouseY);
        UiTheme.drawOutlinedCentered(g, com.minidofus.model.catalog.FighterCatalog.ALL.stream().map(c -> c.displayName())
                        .collect(java.util.stream.Collectors.joining(" · ")) + "  —  équipes de 1 à 3  —  "
                        + com.minidofus.model.map.MapTheme.values().length + " cartes",
                UiTheme.BODY.deriveFont(12.5f), width / 2.0, height - 24, new Color(255, 255, 255, 190), new Color(0, 0, 0, 160));
    }

    /** Lierre qui tombe des deux coins hauts (le gauche en miroir), avec un léger balancement. */
    private void drawIvy(Graphics2D g) {
        java.awt.image.BufferedImage ivy = MenuButton.uiImage("ivy_corner");
        double h = height * 0.46;
        double w = h * ivy.getWidth() / ivy.getHeight();
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.rotate(Math.sin(clock * 0.7) * 0.012, width, 0);
        g.drawImage(ivy, (int) Math.round(width - w), 0, (int) Math.round(w), (int) Math.round(h), null);
        g.setTransform(saved);
        g.rotate(-Math.sin(clock * 0.7 + 1.3) * 0.012, 0, 0);
        g.drawImage(ivy, (int) Math.round(w), 0, (int) Math.round(-w), (int) Math.round(h), null);
        g.setTransform(saved);
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        for (MenuButton b : buttons) if (b.press(x, y)) return;
    }

    @Override
    public void key(String key) {
        if ("ENTER".equals(key)) buttons.get(0).press(buttons.get(0).bounds().x + 1, buttons.get(0).bounds().y + 1);
    }
}
