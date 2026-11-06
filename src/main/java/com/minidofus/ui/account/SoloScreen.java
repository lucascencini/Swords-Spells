package com.minidofus.ui.account;

import com.minidofus.engine.Assets;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.map.MapTheme;
import com.minidofus.ui.UiTheme;
import com.minidofus.ui.menu.MenuButton;
import com.minidofus.ui.menu.Screen;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Préparation d'un combat contre l'ordinateur avec ses personnages : taille de l'équipe, difficulté, carte et
 * personnage de chaque place. L'ordinateur aligne autant d'adversaires, au niveau moyen de l'équipe.
 */
public final class SoloScreen implements Screen {

    /** Choix validés : taille, carte (nom d'enum ou « random »), difficulté, identifiants des personnages. */
    public record Choice(int size, String map, Difficulty difficulty, List<String> characterIds) { }

    private final Assets assets;
    private final Consumer<Graphics2D> background;
    private final MenuButton start;
    private final MenuButton back;
    private final Rectangle[] arrows = new Rectangle[6];
    private final Rectangle[] slotArrows = new Rectangle[6];
    private final Rectangle[] slots = {new Rectangle(), new Rectangle(), new Rectangle()};
    private AccountView account = new AccountView("", List.of());
    private int size = 1;
    private int mapIndex;                       // 0 = au hasard
    private Difficulty difficulty = Difficulty.NORMAL;
    private final int[] pick = {0, 1, 2};
    private String message = "";
    private double messageTime;
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public SoloScreen(Assets assets, Consumer<Graphics2D> background, Consumer<Choice> onStart, Runnable onBack) {
        this.assets = assets;
        this.background = background;
        for (int i = 0; i < 6; i++) {
            arrows[i] = new Rectangle();
            slotArrows[i] = new Rectangle();
        }
        start = new MenuButton("Lancer le combat", null, true, () -> {
            String why = problem();
            if (why != null) {
                flash(why);
                return;
            }
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < size; i++) ids.add(account.characters().get(pick[i]).id());
            onStart.accept(new Choice(size, mapIndex == 0 ? "random" : MapTheme.values()[mapIndex - 1].name(), difficulty, ids));
        });
        back = new MenuButton("Retour", null, false, onBack);
    }

    /** Ouvre l'écran ; le personnage sélectionné dans le hub occupe la première place. */
    public void open(AccountView account, String selectedId) {
        this.account = account;
        int n = account.characters().size();
        size = Math.max(1, Math.min(size, Math.min(3, n)));
        int first = 0;
        for (int i = 0; i < n; i++) if (account.characters().get(i).id().equals(selectedId)) first = i;
        pick[0] = first;
        int k = 1;
        for (int i = 0; i < n && k < 3; i++) if (i != first) pick[k++] = i;
        for (; k < 3; k++) pick[k] = 0;
        message = "";
    }

    public void flash(String text) {
        message = text;
        messageTime = 5;
    }

    private String problem() {
        int n = account.characters().size();
        if (n == 0) return "Créez d'abord un personnage.";
        if (size > n) return "Il faut " + size + " personnages pour cette équipe.";
        for (int i = 0; i < size; i++) for (int j = i + 1; j < size; j++) {
            if (pick[i] == pick[j]) return "Un personnage ne peut occuper qu'une place.";
        }
        return null;
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 20, 980);
        int ph = Math.min(h - 20, 640);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        int rowX = panel.x + 260;
        for (int i = 0; i < 3; i++) {
            int y = panel.y + 88 + i * 52;
            arrows[i * 2] = new Rectangle(rowX, y, 36, 36);
            arrows[i * 2 + 1] = new Rectangle(rowX + 300, y, 36, 36);
        }
        int sw = (pw - 48 - 2 * 16) / 3;
        for (int i = 0; i < 3; i++) {
            slots[i] = new Rectangle(panel.x + 24 + i * (sw + 16), panel.y + 260, sw, ph - 260 - 90);
            slotArrows[i * 2] = new Rectangle(slots[i].x + 8, slots[i].y + slots[i].height / 2 - 18, 36, 36);
            slotArrows[i * 2 + 1] = new Rectangle((int) slots[i].getMaxX() - 44, slots[i].y + slots[i].height / 2 - 18, 36, 36);
        }
        back.setBounds(panel.x + 24, panel.y + ph - 66, 150, 46);
        start.setBounds(panel.x + pw - 284, panel.y + ph - 70, 260, 54);
    }

    @Override
    public void update(double dt) {
        messageTime = Math.max(0, messageTime - dt);
    }

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        Ui.dim(g, width, height);
        UiTheme.panel(g, panel.x, panel.y, panel.width, panel.height, 22, true);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Ui.text(g, "Combat contre l'ordinateur", UiTheme.TITLE.deriveFont(24f), panel.x + 28, panel.y + 44, UiTheme.GOLD_LIGHT);
        Ui.text(g, "Expérience sur chaque combat terminé, bonus à la victoire. Adversaires au niveau moyen de votre équipe.",
                UiTheme.BODY.deriveFont(13f), panel.x + 30, panel.y + 66, UiTheme.TEXT_DIM);
        String[] labels = {"Équipe", "Difficulté", "Carte"};
        String[] values = {size + " contre " + size, difficulty.displayName(),
                mapIndex == 0 ? "Au hasard" : MapTheme.values()[mapIndex - 1].displayName()};
        for (int i = 0; i < 3; i++) {
            Rectangle l = arrows[i * 2];
            Rectangle r = arrows[i * 2 + 1];
            Ui.text(g, labels[i], UiTheme.TITLE.deriveFont(17f), panel.x + 40, l.y + 25, UiTheme.PARCHMENT);
            MenuButton.drawArrow(g, l, true, l.contains(mouseX, mouseY));
            MenuButton.drawArrow(g, r, false, r.contains(mouseX, mouseY));
            UiTheme.drawCentered(g, values[i], UiTheme.TITLE.deriveFont(17f), (l.getMaxX() + r.x) / 2.0, l.y + 25, UiTheme.GOLD_LIGHT);
        }
        int n = account.characters().size();
        for (int i = 0; i < 3; i++) {
            Rectangle s = slots[i];
            boolean used = i < size;
            Ui.frame(g, s, false);
            if (!used) {
                UiTheme.drawCentered(g, "—", UiTheme.TITLE.deriveFont(22f), s.getCenterX(), s.getCenterY() + 8, UiTheme.withAlpha(UiTheme.TEXT_DIM, 120));
                continue;
            }
            UiTheme.drawCentered(g, "Place " + (i + 1), UiTheme.BODY_BOLD.deriveFont(12f), s.getCenterX(), s.y + 20, UiTheme.TEXT_DIM);
            if (n == 0) continue;
            AccountView.Hero h = account.characters().get(pick[i] % n);
            BufferedImage p = assets.image("ui/portrait_" + h.classId());
            int ps = Math.min(s.width - 110, s.height - 90);
            if (p != null) g.drawImage(p, (int) (s.getCenterX() - ps / 2.0), s.y + 30, ps, ps, null);
            UiTheme.drawCentered(g, h.name(), UiTheme.TITLE.deriveFont(17f), s.getCenterX(), s.y + 52 + ps, UiTheme.GOLD_LIGHT);
            UiTheme.drawCentered(g, h.fighter().className() + "  ·  niveau " + h.level(), UiTheme.BODY.deriveFont(12.5f),
                    s.getCenterX(), s.y + 70 + ps, UiTheme.PARCHMENT);
            if (n > 1) {
                MenuButton.drawArrow(g, slotArrows[i * 2], true, slotArrows[i * 2].contains(mouseX, mouseY));
                MenuButton.drawArrow(g, slotArrows[i * 2 + 1], false, slotArrows[i * 2 + 1].contains(mouseX, mouseY));
            }
        }
        back.draw(g, mouseX, mouseY);
        start.setEnabled(problem() == null);
        start.draw(g, mouseX, mouseY);
        String why = problem();
        String line = messageTime > 0 ? message : why == null ? "" : why;
        if (!line.isEmpty()) {
            UiTheme.drawOutlinedCentered(g, line, UiTheme.BODY_BOLD.deriveFont(13.5f), panel.getCenterX(), panel.y + panel.height - 38,
                    why != null ? UiTheme.DAMAGE : UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 200));
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        int maps = MapTheme.values().length + 1;
        Difficulty[] ds = Difficulty.values();
        for (int i = 0; i < 6; i++) {
            if (!arrows[i].contains(x, y)) continue;
            int d = i % 2 == 0 ? -1 : 1;
            switch (i / 2) {
                case 0 -> size = 1 + Math.floorMod(size - 1 + d, 3);
                case 1 -> difficulty = ds[Math.floorMod(difficulty.ordinal() + d, ds.length)];
                default -> mapIndex = Math.floorMod(mapIndex + d, maps);
            }
            return;
        }
        int n = account.characters().size();
        for (int i = 0; i < 6 && n > 1; i++) {
            if (i / 2 >= size || !slotArrows[i].contains(x, y)) continue;
            int slot = i / 2;
            pick[slot] = Math.floorMod(pick[slot] + (i % 2 == 0 ? -1 : 1), n);
            return;
        }
        if (start.press(x, y)) return;
        back.press(x, y);
    }

    @Override
    public void key(String key) {
        if ("ESCAPE".equals(key)) back.press(back.bounds().x + 1, back.bounds().y + 1);
    }
}
