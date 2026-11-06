package com.minidofus.ui.menu;

import com.minidofus.engine.Assets;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.Team;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;
import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Configuration d'une partie : mode, difficulté, taille et composition des équipes, carte.
 * L'écran garde ses choix d'une partie à l'autre.
 */
public final class SetupScreen implements Screen {

    /** Paramètres choisis ({@code theme} nul = carte au hasard ; {@code difficulty} nulle = deux joueurs). */
    public record Result(Difficulty difficulty, List<FighterClass> red, List<FighterClass> blue, MapTheme theme) { }

    private static final String[] ROLES = {"Corps à corps et contrôle", "Distance, soin et mobilité", "Longue portée et pièges"};
    private static final int SLOT_W = 104;
    private static final int SLOT_H = 128;

    private final Assets assets;
    private final Function<MapTheme, BufferedImage> preview;
    private final Consumer<Result> onStart;
    private final Runnable onBack;
    private final Random random = new Random();
    private final Map<MapTheme, BufferedImage> previews = new EnumMap<>(MapTheme.class);

    private boolean versusComputer = true;
    private int difficulty = 1;
    private int teamSize = 2;
    private final int[][] classes = {{0, 2, 1}, {1, 0, 2}};
    private int mapIndex; // 0 = au hasard, puis MapTheme.values()

    private final Choice modeChoice;
    private final Choice difficultyChoice;
    private final Choice sizeChoice;
    private final MenuButton back;
    private final MenuButton start;
    private final MenuButton[] shuffle = new MenuButton[2];
    private final List<Rectangle> slots = new ArrayList<>();
    private Rectangle mapPrev = new Rectangle();
    private Rectangle mapNext = new Rectangle();
    private Rectangle mapBox = new Rectangle();
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;
    private Consumer<Graphics2D> background = g -> { };

    public SetupScreen(Assets assets, Function<MapTheme, BufferedImage> preview, Consumer<Result> onStart, Runnable onBack) {
        this.assets = assets;
        this.preview = preview;
        this.onStart = onStart;
        this.onBack = onBack;
        modeChoice = new Choice(List.of("Contre l'ordinateur", "Deux joueurs"), 0, i -> versusComputer = i == 0);
        difficultyChoice = new Choice(List.of("Facile", "Normal", "Difficile"), difficulty, i -> difficulty = i);
        sizeChoice = new Choice(List.of("1 contre 1", "2 contre 2", "3 contre 3"), teamSize - 1, i -> teamSize = i + 1);
        back = new MenuButton("Retour", null, false, () -> onBack.run());
        start = new MenuButton("Lancer le combat", null, true, this::launch);
        for (int t = 0; t < 2; t++) {
            int team = t;
            shuffle[t] = new MenuButton("Au hasard", null, false, () -> {
                for (int i = 0; i < 3; i++) classes[team][i] = random.nextInt(FighterCatalog.ALL.size());
            });
        }
    }

    /** Choisit le mode avant d'afficher l'écran (depuis l'écran titre). */
    public void setVersusComputer(boolean value) {
        versusComputer = value;
        modeChoice.select(value ? 0 : 1);
    }

    public void setBackground(Consumer<Graphics2D> background) {
        this.background = background;
    }

    private void launch() {
        List<FighterClass> red = new ArrayList<>();
        List<FighterClass> blue = new ArrayList<>();
        for (int i = 0; i < teamSize; i++) {
            red.add(FighterCatalog.ALL.get(classes[0][i]));
            blue.add(FighterCatalog.ALL.get(classes[1][i]));
        }
        MapTheme theme = mapIndex == 0 ? null : MapTheme.values()[mapIndex - 1];
        onStart.accept(new Result(versusComputer ? Difficulty.values()[difficulty] : null, red, blue, theme));
    }

    // ------------------------------------------------------------------ mise en page

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 40, 900);
        int ph = Math.min(h - 40, 640);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        int x = panel.x + 36;
        int y = panel.y + 78;
        modeChoice.setBounds(x + 130, y, 330, 38);
        difficultyChoice.setBounds(x + 130 + 350, y, pw - 72 - 480, 38);
        sizeChoice.setBounds(x + 130, y + 52, 380, 38);
        int bw = 200;
        back.setBounds(panel.x + 36, panel.y + ph - 64, 160, 46);
        start.setBounds(panel.x + pw - 36 - bw - 40, panel.y + ph - 66, bw + 40, 50);
        mapBox = new Rectangle(panel.x + 36 + 130, panel.y + ph - 154, 380, 70);
        mapPrev = new Rectangle(mapBox.x, mapBox.y + 16, 38, 38);
        mapNext = new Rectangle(mapBox.x + mapBox.width - 38, mapBox.y + 16, 38, 38);
    }

    /** Emplacements des cartes de classe, recalculés selon la taille d'équipe. */
    private void layoutSlots() {
        slots.clear();
        int top = panel.y + 196;
        int half = (panel.width - 72) / 2;
        for (int t = 0; t < 2; t++) {
            int left = panel.x + 36 + t * half;
            int total = teamSize * SLOT_W + (teamSize - 1) * 10;
            int sx = left + (half - total) / 2;
            for (int i = 0; i < teamSize; i++) slots.add(new Rectangle(sx + i * (SLOT_W + 10), top + 30, SLOT_W, SLOT_H));
            shuffle[t].setBounds(left + half / 2 - 60, top + 30 + SLOT_H + 12, 120, 30);
        }
    }

    @Override
    public void update(double dt) {
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        g.setColor(new Color(5, 4, 2, 175));
        g.fillRect(0, 0, width, height);
        layoutSlots();
        UiTheme.panel(g, panel.x, panel.y, panel.width, panel.height, 22, true);
        UiTheme.drawCentered(g, "Préparer le combat", UiTheme.TITLE.deriveFont(26f), panel.getCenterX(), panel.y + 46, UiTheme.GOLD_LIGHT);

        label(g, "Mode", panel.y + 78 + 25);
        modeChoice.draw(g, mouseX, mouseY);
        if (versusComputer) difficultyChoice.draw(g, mouseX, mouseY);
        label(g, "Équipes", panel.y + 130 + 25);
        sizeChoice.draw(g, mouseX, mouseY);

        int half = (panel.width - 72) / 2;
        for (int t = 0; t < 2; t++) {
            Team team = t == 0 ? Team.RED : Team.BLUE;
            int left = panel.x + 36 + t * half;
            String title = versusComputer ? (t == 0 ? "Votre équipe" : "Ordinateur") : (t == 0 ? "Joueur 1" : "Joueur 2");
            UiTheme.drawCentered(g, title + " · " + team.displayName(), UiTheme.TITLE.deriveFont(16f),
                    left + half / 2.0, panel.y + 214, UiTheme.teamTextColor(team));
            shuffle[t].draw(g, mouseX, mouseY);
        }
        for (int i = 0; i < slots.size(); i++) drawSlot(g, slots.get(i), i / teamSize, i % teamSize);

        label(g, "Carte", mapBox.y + 42);
        drawMapSelector(g);
        back.draw(g, mouseX, mouseY);
        start.draw(g, mouseX, mouseY);
        g.setFont(UiTheme.BODY.deriveFont(11.5f));
        g.setColor(UiTheme.TEXT_DIM);
        g.drawString("Cliquez sur un combattant pour changer sa classe · Entrée : lancer · Échap : retour",
                panel.x + 36, panel.y + panel.height - 76);

        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).contains(mouseX, mouseY)) drawClassTooltip(g, slots.get(i), FighterCatalog.ALL.get(classes[i / teamSize][i % teamSize]), classes[i / teamSize][i % teamSize]);
        }
    }

    private void label(Graphics2D g, String text, int baseline) {
        g.setFont(UiTheme.BODY_BOLD.deriveFont(14f));
        g.setColor(UiTheme.PARCHMENT);
        g.drawString(text, panel.x + 36, baseline);
    }

    private void drawSlot(Graphics2D g, Rectangle r, int team, int index) {
        FighterClass c = FighterCatalog.ALL.get(classes[team][index]);
        boolean hover = r.contains(mouseX, mouseY);
        Color teamColor = UiTheme.teamColor(team == 0 ? Team.RED : Team.BLUE);
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y - (hover ? 2 : 0), r.width, r.height, 14, 14);
        g.setPaint(new GradientPaint(0, r.y, new Color(0x3A3020), 0, r.y + r.height, new Color(0x1E1810)));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(hover ? UiTheme.GOLD_LIGHT : teamColor);
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        BufferedImage portrait = assets.image("ui/portrait_" + c.id());
        int ph = 66;
        int pw = (int) Math.round(portrait.getWidth() * (ph / (double) portrait.getHeight()));
        g.drawImage(portrait, r.x + (r.width - pw) / 2, r.y + 10 - (hover ? 2 : 0), pw, ph, null);
        UiTheme.drawCentered(g, c.displayName(), UiTheme.TITLE.deriveFont(14.5f), r.getCenterX(), r.y + 96, UiTheme.PARCHMENT);
        UiTheme.drawCentered(g, c.maxHp() + " PV · " + c.maxPa() + " PA · " + c.maxPm() + " PM", UiTheme.BODY.deriveFont(10.5f),
                r.getCenterX(), r.y + 114, UiTheme.TEXT_DIM);
        g.setColor(new Color(255, 255, 255, hover ? 200 : 90));
        g.setFont(UiTheme.BODY_BOLD.deriveFont(14f));
        g.drawString("‹", r.x + 7, r.y + 50);
        g.drawString("›", r.x + r.width - 13, r.y + 50);
    }

    private void drawClassTooltip(Graphics2D g, Rectangle anchor, FighterClass c, int classIndex) {
        List<String> lines = new ArrayList<>();
        lines.add(ROLES[classIndex]);
        for (Spell s : c.spells()) lines.add("• " + s.name() + " — " + s.effectSummary());
        FontMetrics fm = g.getFontMetrics(UiTheme.BODY.deriveFont(12f));
        int w = 0;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        w += 28;
        int h = 36 + lines.size() * 17;
        int x = Math.max(8, Math.min(width - w - 8, anchor.x + anchor.width / 2 - w / 2));
        int y = anchor.y + anchor.height + 8;
        if (y + h > height - 8) y = anchor.y - h - 8;
        UiTheme.panel(g, x, y, w, h, 10, false);
        g.setFont(UiTheme.TITLE.deriveFont(14f));
        g.setColor(UiTheme.GOLD_LIGHT);
        g.drawString(c.displayName(), x + 14, y + 22);
        g.setFont(UiTheme.BODY.deriveFont(12f));
        for (int i = 0; i < lines.size(); i++) {
            g.setColor(i == 0 ? UiTheme.TEXT_DIM : UiTheme.PARCHMENT);
            g.drawString(lines.get(i), x + 14, y + 40 + i * 17);
        }
    }

    private void drawMapSelector(Graphics2D g) {
        Rectangle r = mapBox;
        g.setColor(new Color(10, 8, 5, 170));
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        g.setColor(UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        arrow(g, mapPrev, "‹");
        arrow(g, mapNext, "›");
        String name = mapIndex == 0 ? "Au hasard" : MapTheme.values()[mapIndex - 1].displayName();
        UiTheme.drawCentered(g, name, UiTheme.TITLE.deriveFont(17f), r.getCenterX(), r.y + 33, UiTheme.PARCHMENT);
        UiTheme.drawCentered(g, (mapIndex == 0 ? "une des 6 cartes" : (mapIndex) + " / " + MapTheme.values().length),
                UiTheme.BODY.deriveFont(11.5f), r.getCenterX(), r.y + 53, UiTheme.TEXT_DIM);

        int px = r.x + r.width + 24;
        int pw = panel.x + panel.width - 36 - px;
        int pht = r.height + 60;
        int py = r.y - 64;
        if (pw < 120) return;
        RoundRectangle2D frame = new RoundRectangle2D.Double(px, py, pw, pht, 14, 14);
        g.setColor(new Color(10, 8, 5, 200));
        g.fill(frame);
        if (mapIndex > 0) {
            BufferedImage img = previews.computeIfAbsent(MapTheme.values()[mapIndex - 1], preview);
            double s = Math.min((pw - 16) / (double) img.getWidth(), (pht - 16) / (double) img.getHeight());
            int iw = (int) (img.getWidth() * s);
            int ih = (int) (img.getHeight() * s);
            g.drawImage(img, px + (pw - iw) / 2, py + (pht - ih) / 2, iw, ih, null);
        } else {
            UiTheme.drawCentered(g, "?", UiTheme.TITLE.deriveFont(48f), px + pw / 2.0, py + pht / 2.0 + 16, UiTheme.withAlpha(UiTheme.GOLD, 160));
        }
        g.setColor(UiTheme.BRONZE);
        g.draw(frame);
    }

    private void arrow(Graphics2D g, Rectangle r, String glyph) {
        boolean hover = r.contains(mouseX, mouseY);
        g.setColor(hover ? new Color(0x5A4A2A) : new Color(0x3A3020));
        g.fillRoundRect(r.x + 4, r.y, r.width - 8, r.height, 10, 10);
        UiTheme.drawCentered(g, glyph, UiTheme.TITLE.deriveFont(22f), r.getCenterX(), r.y + 27, UiTheme.GOLD_LIGHT);
    }

    // ------------------------------------------------------------------ entrées

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        if (modeChoice.press(x, y) || (versusComputer && difficultyChoice.press(x, y)) || sizeChoice.press(x, y)) return;
        if (back.press(x, y) || start.press(x, y) || shuffle[0].press(x, y) || shuffle[1].press(x, y)) return;
        if (mapPrev.contains(x, y)) cycleMap(-1);
        if (mapNext.contains(x, y)) cycleMap(1);
        layoutSlots();
        for (int i = 0; i < slots.size(); i++) {
            Rectangle r = slots.get(i);
            if (!r.contains(x, y)) continue;
            int team = i / teamSize;
            int index = i % teamSize;
            int step = x < r.x + r.width / 3 ? -1 : 1;
            classes[team][index] = Math.floorMod(classes[team][index] + step, FighterCatalog.ALL.size());
        }
    }

    private void cycleMap(int step) {
        mapIndex = Math.floorMod(mapIndex + step, MapTheme.values().length + 1);
    }

    @Override
    public void key(String key) {
        switch (key) {
            case "ENTER" -> launch();
            case "ESCAPE" -> onBack.run();
            case "LEFT" -> cycleMap(-1);
            case "RIGHT" -> cycleMap(1);
            default -> { }
        }
    }
}
