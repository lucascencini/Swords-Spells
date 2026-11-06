package com.minidofus.ui.account;

import com.minidofus.engine.Assets;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.progression.CharacterBuild;
import com.minidofus.ui.UiTheme;
import com.minidofus.ui.menu.MenuButton;
import com.minidofus.ui.menu.Screen;
import com.minidofus.ui.menu.TextField;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Création d'un personnage : un nom et une classe (illustrations haute définition, rôle, caractéristiques). */
public final class CreateHeroScreen implements Screen {

    private static final Map<String, String> ROLES = Map.of(
            "warrior", "Corps à corps et contrôle : il encaisse, assomme, repousse et attire.",
            "mage", "Distance et contrôle : feu, glace et arcanes ; il évolue avec de nombreux talents.",
            "archer", "Longue portée et pièges : il frappe de loin et contrôle le terrain.",
            "druid", "Soutien et contrôle de zone : nuées, tornades, éclairs et soins.");

    private final Assets assets;
    private final Consumer<Graphics2D> background;
    private final TextField name = new TextField("nom du personnage", 16, false);
    private final MenuButton create;
    private final MenuButton cancel;
    private final List<Rectangle> cards = new ArrayList<>();
    private int selected;
    private String error = "";
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public CreateHeroScreen(Assets assets, Consumer<Graphics2D> background, BiConsumer<String, String> onCreate, Runnable onCancel) {
        this.assets = assets;
        this.background = background;
        create = new MenuButton("Créer le personnage", null, true, () -> {
            String n = CharacterBuild.cleanName(name.text());
            if (n.isEmpty()) {
                error = "Donnez un nom à votre personnage (lettres, chiffres, espaces, tirets).";
                return;
            }
            onCreate.accept(n, FighterCatalog.ALL.get(selected).id());
        });
        cancel = new MenuButton("Annuler", null, false, onCancel);
        name.setFocused(true);
    }

    /** Réinitialise l'écran avant de l'afficher. */
    public void reset() {
        name.setText("");
        name.setFocused(true);
        error = "";
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 30, 1060);
        int ph = Math.min(h - 30, 640);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        name.setBounds(panel.x + (pw - 400) / 2, panel.y + 92, 400, 44);
        create.setBounds(panel.x + pw - 330, panel.y + ph - 78, 300, 54);
        cancel.setBounds(panel.x + 30, panel.y + ph - 72, 180, 46);
    }

    @Override
    public void update(double dt) {
        name.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        Ui.dim(g, width, height);
        UiTheme.panel(g, panel.x, panel.y, panel.width, panel.height, 22, true);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        UiTheme.drawCentered(g, "Nouveau personnage", UiTheme.TITLE.deriveFont(26f), panel.getCenterX(), panel.y + 48, UiTheme.GOLD_LIGHT);
        Ui.text(g, "Nom", UiTheme.BODY_BOLD.deriveFont(13f), name.bounds().x + 2, name.bounds().y - 8, UiTheme.PARCHMENT);
        name.draw(g, mouseX, mouseY);
        cards.clear();
        int n = FighterCatalog.ALL.size();
        int cw = (panel.width - 60 - (n - 1) * 16) / n;
        for (int i = 0; i < n; i++) {
            FighterClass c = FighterCatalog.ALL.get(i);
            Rectangle r = new Rectangle(panel.x + 30 + i * (cw + 16), panel.y + 160, cw, 340);
            cards.add(r);
            Ui.frame(g, r, i == selected || r.contains(mouseX, mouseY));
            int art = Math.min(cw - 30, 190);
            g.drawImage(assets.image("ui/art_" + c.id()), (int) (r.getCenterX() - art / 2.0), r.y + 12, art, art, null);
            UiTheme.drawCentered(g, c.displayName(), UiTheme.TITLE.deriveFont(18f), r.getCenterX(), r.y + art + 40,
                    i == selected ? UiTheme.GOLD_LIGHT : UiTheme.PARCHMENT);
            UiTheme.drawCentered(g, c.maxHp() + " PV · " + c.maxPa() + " PA · " + c.maxPm() + " PM", UiTheme.BODY.deriveFont(12f),
                    r.getCenterX(), r.y + art + 60, UiTheme.TEXT_DIM);
            int ly = r.y + art + 82;
            for (String line : Ui.wrap(g, ROLES.getOrDefault(c.id(), ""), UiTheme.BODY.deriveFont(12f), cw - 24)) {
                UiTheme.drawCentered(g, line, UiTheme.BODY.deriveFont(12f), r.getCenterX(), ly, UiTheme.PARCHMENT);
                ly += 16;
            }
        }
        FighterClass c = FighterCatalog.ALL.get(selected);
        StringBuilder spells = new StringBuilder("Sorts de départ : ");
        for (Spell s : c.spells()) spells.append(s.name()).append(s == c.spells().get(c.spells().size() - 1) ? "" : ", ");
        if ("mage".equals(c.id())) spells.append(" — et 6 sorts à débloquer, 26 talents");
        UiTheme.drawCentered(g, spells.toString(), UiTheme.BODY.deriveFont(12.5f), panel.getCenterX(), panel.y + 528, UiTheme.TEXT_DIM);
        if (!error.isEmpty()) {
            UiTheme.drawCentered(g, error, UiTheme.BODY_BOLD.deriveFont(13f), panel.getCenterX(), name.bounds().y + 64, UiTheme.DAMAGE);
        }
        create.draw(g, mouseX, mouseY);
        cancel.draw(g, mouseX, mouseY);
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        name.setFocused(name.bounds().contains(x, y) || name.isFocused());
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).contains(x, y)) {
                selected = i;
                return;
            }
        }
        if (create.press(x, y)) return;
        cancel.press(x, y);
    }

    @Override
    public void key(String key) {
        switch (key) {
            case "ENTER" -> create.press(create.bounds().x + 1, create.bounds().y + 1);
            case "ESCAPE" -> cancel.press(cancel.bounds().x + 1, cancel.bounds().y + 1);
            case "BACK_SPACE" -> name.backspace();
            case "LEFT" -> selected = Math.floorMod(selected - 1, FighterCatalog.ALL.size());
            case "RIGHT" -> selected = Math.floorMod(selected + 1, FighterCatalog.ALL.size());
            default -> { }
        }
    }

    @Override
    public boolean wantsText() {
        return true;
    }

    @Override
    public void text(String typed) {
        name.insert(typed);
    }
}
