package com.minidofus.ui.account;

import com.minidofus.engine.Assets;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.progression.Progression;
import com.minidofus.model.progression.TalentCatalog;
import com.minidofus.model.progression.TalentDef;
import com.minidofus.ui.Icons;
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
import java.util.Locale;
import java.util.function.Consumer;

/**
 * « Mes personnages » : liste des personnages du compte, fiche du personnage choisi (niveau, expérience,
 * caractéristiques, sorts équipés) et accès aux talents, au grimoire, au combat solo et au salon.
 */
public final class HubScreen implements Screen {

    /** Actions demandées par l'écran (exécutées par le contrôleur en ligne). */
    public interface Actions {
        void create();
        void talents(AccountView.Hero hero);
        void grimoire(AccountView.Hero hero);
        void delete(AccountView.Hero hero);
        void solo();
        void lobby();
        void logout();
    }

    private final Assets assets;
    private final Icons icons;
    private final Consumer<Graphics2D> background;
    private final Actions actions;
    private final MenuButton talents;
    private final MenuButton grimoire;
    private final MenuButton delete;
    private final MenuButton solo;
    private final MenuButton lobby;
    private final MenuButton logout;
    private final List<Rectangle> cards = new ArrayList<>();
    private Rectangle createCard = new Rectangle();
    private AccountView account = new AccountView("", List.of());
    private String selectedId;
    private String status = "";
    private double statusTime;
    private double deleteArmed;
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public HubScreen(Assets assets, Consumer<Graphics2D> background, Actions actions) {
        this.assets = assets;
        this.icons = new Icons(assets);
        this.background = background;
        this.actions = actions;
        talents = new MenuButton("Talents", null, false, () -> withHero(actions::talents));
        grimoire = new MenuButton("Grimoire", null, false, () -> withHero(actions::grimoire));
        delete = new MenuButton("Supprimer", null, false, this::deletePressed);
        solo = new MenuButton("Combat contre l'ordinateur", "Vos personnages gagnent de l'expérience", true, actions::solo);
        lobby = new MenuButton("Salon en ligne", "Avec vos amis", false, actions::lobby);
        logout = new MenuButton("Déconnexion", null, false, actions::logout);
    }

    public void setAccount(AccountView account) {
        this.account = account;
        if (selected() == null && !account.characters().isEmpty()) selectedId = account.characters().get(0).id();
    }

    public void select(String id) {
        selectedId = id;
    }

    public AccountView account() {
        return account;
    }

    public void setStatus(String text) {
        status = text;
        statusTime = 6;
    }

    public AccountView.Hero selected() {
        return selectedId == null ? null : account.hero(selectedId);
    }

    private void withHero(Consumer<AccountView.Hero> action) {
        AccountView.Hero h = selected();
        if (h != null) action.accept(h);
    }

    private void deletePressed() {
        AccountView.Hero h = selected();
        if (h == null) return;
        if (deleteArmed > 0) {
            deleteArmed = 0;
            actions.delete(h);
            selectedId = null;
        } else {
            deleteArmed = 3;
            setStatus("Cliquez encore sur « Supprimer » pour effacer définitivement " + h.name() + ".");
        }
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 30, 1120);
        int ph = Math.min(h - 30, 740);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        int rightX = panel.x + 420;
        int rightW = panel.x + pw - 30 - rightX;
        int by = panel.y + ph - 150;
        talents.setBounds(rightX, by, (rightW - 24) / 3, 48);
        grimoire.setBounds(rightX + (rightW - 24) / 3 + 12, by, (rightW - 24) / 3, 48);
        delete.setBounds(rightX + 2 * ((rightW - 24) / 3 + 12), by, (rightW - 24) / 3, 48);
        solo.setBounds(rightX, panel.y + ph - 84, rightW - 230, 58);
        lobby.setBounds(rightX + rightW - 218, panel.y + ph - 84, 218, 58);
        logout.setBounds(panel.x + 30, panel.y + ph - 74, 170, 44);
    }

    @Override
    public void update(double dt) {
        statusTime = Math.max(0, statusTime - dt);
        deleteArmed = Math.max(0, deleteArmed - dt);
    }

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        Ui.dim(g, width, height);
        UiTheme.panel(g, panel.x, panel.y, panel.width, panel.height, 22, true);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        UiTheme.drawCentered(g, "Mes personnages", UiTheme.TITLE.deriveFont(24f), panel.x + 200, panel.y + 44, UiTheme.GOLD_LIGHT);
        UiTheme.drawCentered(g, "Compte : " + account.name(), UiTheme.BODY.deriveFont(12.5f), panel.x + 200, panel.y + 64, UiTheme.TEXT_DIM);
        drawList(g);
        AccountView.Hero h = selected();
        if (h != null) {
            drawHero(g, h);
            talents.draw(g, mouseX, mouseY);
            grimoire.draw(g, mouseX, mouseY);
            delete.draw(g, mouseX, mouseY);
        } else {
            UiTheme.drawCentered(g, account.characters().isEmpty() ? "Créez votre premier personnage pour commencer."
                    : "Choisissez un personnage.", UiTheme.TITLE.deriveFont(18f), panel.x + 420 + (panel.width - 450) / 2.0,
                    panel.y + panel.height / 2.0 - 40, UiTheme.PARCHMENT);
        }
        solo.setEnabled(!account.characters().isEmpty());
        solo.draw(g, mouseX, mouseY);
        lobby.draw(g, mouseX, mouseY);
        logout.draw(g, mouseX, mouseY);
        if (statusTime > 0 && !status.isEmpty()) {
            // messages (création, refus, expérience gagnée) : une ligne par élément, au-dessus des boutons
            String[] lines = status.split("\n");
            double cx = talents.bounds().x + (delete.bounds().getMaxX() - talents.bounds().x) / 2.0;
            int ly = talents.bounds().y - 34 - (lines.length - 1) * 20;
            for (String l : lines) {
                UiTheme.drawOutlinedCentered(g, l, UiTheme.BODY_BOLD.deriveFont(14f), cx, ly, UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 200));
                ly += 20;
            }
        }
    }

    private void drawList(Graphics2D g) {
        cards.clear();
        int x = panel.x + 30;
        int y = panel.y + 84;
        int w = 340;
        for (AccountView.Hero h : account.characters()) {
            Rectangle r = new Rectangle(x, y, w, 80);
            cards.add(r);
            boolean sel = h.id().equals(selectedId);
            Ui.frame(g, r, sel || r.contains(mouseX, mouseY));
            BufferedImage p = assets.image("ui/portrait_" + h.classId());
            g.drawImage(p, r.x + 8, r.y + 7, 60, 66, null);
            FighterClass base = FighterCatalog.byId(h.classId());
            Ui.text(g, h.name(), UiTheme.TITLE.deriveFont(16f), r.x + 80, r.y + 26, sel ? UiTheme.GOLD_LIGHT : UiTheme.PARCHMENT);
            Ui.text(g, base.displayName() + " · niveau " + h.level(), UiTheme.BODY.deriveFont(12.5f), r.x + 80, r.y + 45, UiTheme.TEXT_DIM);
            int next = h.xpToNext();
            Ui.bar(g, r.x + 80, r.y + 54, w - 96, 13, next == 0 ? 1 : h.xp() / (double) next, UiTheme.GOLD, null);
            if (h.freePoints() > 0) {
                UiTheme.drawOutlinedCentered(g, "+" + h.freePoints(), UiTheme.BODY_BOLD.deriveFont(12f), r.x + w - 20, r.y + 24,
                        UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 200));
            }
            y += 88;
        }
        createCard = account.characters().size() < 6 ? new Rectangle(x, y, w, 52) : new Rectangle();
        if (createCard.width > 0) {
            Ui.frame(g, createCard, createCard.contains(mouseX, mouseY));
            UiTheme.drawCentered(g, "+  Nouveau personnage", UiTheme.TITLE.deriveFont(15f), createCard.getCenterX(), createCard.y + 32,
                    UiTheme.GOLD_LIGHT);
        }
    }

    private void drawHero(Graphics2D g, AccountView.Hero h) {
        int x = panel.x + 420;
        int y = panel.y + 30;
        int w = panel.x + panel.width - 30 - x;
        FighterClass f = h.fighter();
        BufferedImage art = assets.image("ui/art_" + h.classId());
        g.drawImage(art, x, y, 200, 200, null);
        int tx = x + 220;
        Ui.text(g, h.name(), UiTheme.TITLE.deriveFont(28f), tx, y + 36, UiTheme.GOLD_LIGHT);
        Ui.text(g, f.className() + " · niveau " + h.level() + (h.level() >= Progression.MAX_LEVEL ? " (maximum)" : ""),
                UiTheme.BODY_BOLD.deriveFont(15f), tx, y + 62, UiTheme.PARCHMENT);
        int next = h.xpToNext();
        Ui.bar(g, tx, y + 74, w - 220, 18, next == 0 ? 1 : h.xp() / (double) next, UiTheme.GOLD,
                next == 0 ? "Niveau maximum" : h.xp() + " / " + next + " XP");
        String[][] stats = {
                {"PV", String.valueOf(f.maxHp())}, {"PA", String.valueOf(f.maxPa())}, {"PM", String.valueOf(f.maxPm())},
                {"Puissance", "+" + Math.round((f.power() - 1) * 100) + " %"}, {"Résistance", Math.round(f.resist() * 100) + " %"},
                {"Initiative", String.valueOf(f.initiative())}};
        for (int i = 0; i < stats.length; i++) {
            double sx = tx + (i % 3) * ((w - 220) / 3.0);
            double sy = y + 118 + (i / 3) * 40;
            Ui.text(g, stats[i][0], UiTheme.BODY.deriveFont(12f), sx, sy, UiTheme.TEXT_DIM);
            Ui.text(g, stats[i][1], UiTheme.TITLE.deriveFont(17f), sx, sy + 20, UiTheme.PARCHMENT);
        }
        int free = h.freePoints();
        Ui.text(g, "Points de talent : " + free + " à répartir  ·  Victoires " + h.wins() + "  ·  Défaites " + h.losses(),
                UiTheme.BODY_BOLD.deriveFont(13.5f), x, y + 236, free > 0 ? UiTheme.GOLD_LIGHT : UiTheme.PARCHMENT);
        Ui.text(g, "Sorts équipés", UiTheme.TITLE.deriveFont(15f), x, y + 272, UiTheme.GOLD_LIGHT);
        int ix = x;
        for (Spell s : f.spells()) {
            BufferedImage icon = icons.spell(s.style());
            g.drawImage(icon, ix, y + 284, 46, 51, null);
            if (new Rectangle(ix, y + 284, 46, 51).contains(mouseX, mouseY)) {
                Ui.text(g, s.name() + " : " + s.cost() + " PA, " + s.effectSummary(), UiTheme.BODY.deriveFont(12f), x, y + 356,
                        UiTheme.PARCHMENT);
            }
            ix += 52;
        }
        // talents choisis
        TalentCatalog.Tree tree = TalentCatalog.of(h.classId());
        List<String> chosen = new ArrayList<>();
        for (var e : h.talents().entrySet()) {
            TalentDef t = tree.talent(e.getKey());
            if (t != null && e.getValue() > 0) chosen.add(t.name() + (t.maxRank() > 1 ? " " + e.getValue() + "/" + t.maxRank() : ""));
        }
        int ty = y + 392;
        if (!tree.talents().isEmpty()) {
            Ui.text(g, "Talents", UiTheme.TITLE.deriveFont(15f), x, ty, UiTheme.GOLD_LIGHT);
            String text = chosen.isEmpty() ? "Aucun talent choisi pour l'instant." : String.join("  ·  ", chosen);
            ty += 22;
            for (String line : Ui.wrap(g, text, UiTheme.BODY.deriveFont(13f), w)) {
                if (ty > talents.bounds().y - 70) break;
                Ui.text(g, line, UiTheme.BODY.deriveFont(13f), x, ty, chosen.isEmpty() ? UiTheme.TEXT_DIM : UiTheme.PARCHMENT);
                ty += 18;
            }
        } else {
            Ui.text(g, "Les talents de cette classe arrivent bientôt (le Mage sert de classe pilote).", UiTheme.BODY.deriveFont(13f),
                    x, ty, UiTheme.TEXT_DIM);
        }
        if (free > 0 && !tree.talents().isEmpty()) {
            Rectangle b = talents.bounds();
            g.setColor(new Color(0xC0392B));
            g.fill(new java.awt.geom.Ellipse2D.Double(b.getMaxX() - 22, b.y - 8, 24, 24));
            UiTheme.drawCentered(g, String.valueOf(free), UiTheme.BODY_BOLD.deriveFont(12f), b.getMaxX() - 10, b.y + 9, Color.WHITE);
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        for (int i = 0; i < cards.size() && i < account.characters().size(); i++) {
            if (cards.get(i).contains(x, y)) {
                selectedId = account.characters().get(i).id();
                deleteArmed = 0;
                return;
            }
        }
        if (createCard.contains(x, y)) {
            actions.create();
            return;
        }
        if (selected() != null && (talents.press(x, y) || grimoire.press(x, y) || delete.press(x, y))) return;
        if (solo.press(x, y) || lobby.press(x, y)) return;
        logout.press(x, y);
    }

    @Override
    public void key(String key) {
        if ("ESCAPE".equals(key)) actions.logout();
        if (("UP".equals(key) || "DOWN".equals(key)) && !account.characters().isEmpty()) {
            int i = Math.max(0, account.characters().indexOf(selected()));
            i = Math.floorMod(i + ("UP".equals(key) ? -1 : 1), account.characters().size());
            selectedId = account.characters().get(i).id();
        }
    }

    static String percent(double v) {
        return String.format(Locale.ROOT, "%.0f %%", v * 100);
    }
}
