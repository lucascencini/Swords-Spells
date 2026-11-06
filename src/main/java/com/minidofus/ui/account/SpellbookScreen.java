package com.minidofus.ui.account;

import com.minidofus.engine.Assets;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.catalog.SpellBook;
import com.minidofus.model.progression.CharacterBuild;
import com.minidofus.ui.Icons;
import com.minidofus.ui.UiTheme;
import com.minidofus.ui.menu.MenuButton;
import com.minidofus.ui.menu.Screen;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Grimoire : tous les sorts de la classe, ceux appris (selon le niveau) et ceux à venir. Un clic équipe ou retire un sort ;
 * 8 sorts au plus, plus un sort ultime. Les valeurs affichées tiennent compte des talents du personnage.
 */
public final class SpellbookScreen implements Screen {

    private final Icons icons;
    private final Consumer<Graphics2D> background;
    private final MenuButton save;
    private final MenuButton defaults;
    private final MenuButton back;
    private final Map<SpellBook.Entry, Rectangle> cards = new LinkedHashMap<>();
    private AccountView.Hero hero;
    private List<String> equipped = new ArrayList<>();
    private FighterClass tunedView;
    private String message = "";
    private double messageTime;
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public SpellbookScreen(Assets assets, Consumer<Graphics2D> background, BiConsumer<AccountView.Hero, List<String>> onSave,
                           Runnable onBack) {
        this.icons = new Icons(assets);
        this.background = background;
        save = new MenuButton("Enregistrer", null, true, () -> {
            if (hero != null) onSave.accept(hero, List.copyOf(equipped));
        });
        defaults = new MenuButton("Sorts conseillés", null, false, () -> {
            if (hero != null) equipped = new ArrayList<>(SpellBook.defaultLoadout(hero.classId(), hero.level()));
        });
        back = new MenuButton("Retour", null, false, onBack);
    }

    public void open(AccountView.Hero hero) {
        this.hero = hero;
        equipped = new ArrayList<>(current(hero));
        // vue « tous les sorts appris équipés » : pour afficher les valeurs améliorées par les talents
        List<String> all = new ArrayList<>();
        for (SpellBook.Entry e : SpellBook.of(hero.classId())) all.add(e.id());
        tunedView = new CharacterBuild(hero.name(), hero.classId(), Math.max(hero.level(), 50), hero.talents(), all).toFighterClass(hero.level());
        message = "";
    }

    public void refresh(AccountView.Hero updated) {
        if (hero != null && updated != null && updated.id().equals(hero.id())) {
            boolean saved = current(updated).equals(equipped);
            hero = updated;
            if (saved) flash("Grimoire enregistré.");
        }
    }

    private static List<String> current(AccountView.Hero h) {
        return h.spells().isEmpty() ? SpellBook.defaultLoadout(h.classId(), h.level()) : h.spells();
    }

    public void flash(String text) {
        message = text;
        messageTime = 5;
    }

    private Spell tuned(SpellBook.Entry e) {
        if (tunedView != null) for (Spell s : tunedView.spells()) if (s.id().equals(e.id())) return s;
        return e.spell();
    }

    private long normalCount() {
        return equipped.stream().map(id -> SpellBook.entry(hero.classId(), id)).filter(e -> e != null && !e.ultimate()).count();
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 20, 1100);
        int ph = Math.min(h - 20, 760);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        int by = panel.y + ph - 62;
        back.setBounds(panel.x + 24, by, 140, 44);
        defaults.setBounds(panel.x + 176, by, 200, 44);
        save.setBounds(panel.x + pw - 244, by - 4, 220, 52);
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
        if (hero == null) return;
        Ui.text(g, "Grimoire — " + hero.name(), UiTheme.TITLE.deriveFont(24f), panel.x + 28, panel.y + 42, UiTheme.GOLD_LIGHT);
        Ui.text(g, hero.fighter().className() + " niveau " + hero.level() + "  ·  cliquez sur un sort appris pour l'équiper ou le retirer",
                UiTheme.BODY.deriveFont(13f), panel.x + 30, panel.y + 62, UiTheme.TEXT_DIM);
        boolean ult = equipped.stream().map(id -> SpellBook.entry(hero.classId(), id)).anyMatch(e -> e != null && e.ultimate());
        UiTheme.drawOutlinedCentered(g, "Équipés : " + normalCount() + " / " + SpellBook.MAX_EQUIPPED + "   ·   ultime : " + (ult ? 1 : 0) + " / 1",
                UiTheme.TITLE.deriveFont(17f), panel.x + panel.width - 200, panel.y + 46, UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 180));

        cards.clear();
        List<SpellBook.Entry> entries = SpellBook.of(hero.classId());
        int cols = 3;
        int gap = 14;
        int cw = (panel.width - 48 - (cols - 1) * gap) / cols;
        int rows = (entries.size() + cols - 1) / cols;
        int ch = Math.min(108, (panel.height - 170 - (rows - 1) * gap) / Math.max(1, rows));
        for (int i = 0; i < entries.size(); i++) {
            SpellBook.Entry e = entries.get(i);
            Rectangle r = new Rectangle(panel.x + 24 + (i % cols) * (cw + gap), panel.y + 84 + (i / cols) * (ch + gap), cw, ch);
            cards.put(e, r);
            drawCard(g, e, r);
        }
        back.draw(g, mouseX, mouseY);
        defaults.draw(g, mouseX, mouseY);
        save.setEnabled(!equipped.equals(current(hero)));
        save.draw(g, mouseX, mouseY);
        if (messageTime > 0) {
            UiTheme.drawOutlinedCentered(g, message, UiTheme.BODY_BOLD.deriveFont(13.5f), panel.getCenterX() + 60,
                    panel.y + panel.height - 36, UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 200));
        }
    }

    private void drawCard(Graphics2D g, SpellBook.Entry e, Rectangle r) {
        boolean learned = e.level() <= hero.level();
        boolean on = equipped.contains(e.id());
        boolean hover = r.contains(mouseX, mouseY) && learned;
        Ui.frame(g, r, on || hover);
        if (on) {
            g.setColor(UiTheme.withAlpha(UiTheme.GOLD, 40));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        }
        Composite saved = g.getComposite();
        if (!learned) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        Spell s = tuned(e);
        int icon = Math.min(64, r.height - 20);
        g.drawImage(icons.spell(s.style()), r.x + 10, r.y + (r.height - icon) / 2, icon, icon, null);
        int tx = r.x + 20 + icon;
        Ui.text(g, s.name() + (e.ultimate() ? "  ★ ultime" : ""), UiTheme.TITLE.deriveFont(15f), tx, r.y + 24,
                e.ultimate() ? new Color(0xFF9A4A) : UiTheme.GOLD_LIGHT);
        String range = s.maxRange() == 0 ? "sur soi" : "portée " + s.minRange() + "-" + s.maxRange();
        Ui.text(g, s.cost() + " PA  ·  " + range + (s.cooldown() > 0 ? "  ·  relance " + s.cooldown() : ""),
                UiTheme.BODY.deriveFont(11.5f), tx, r.y + 42, UiTheme.TEXT_DIM);
        int ly = r.y + 60;
        for (String line : Ui.wrap(g, s.effectSummary(), UiTheme.BODY.deriveFont(12f), r.width - (tx - r.x) - 10)) {
            if (ly > r.getMaxY() - 6) break;
            Ui.text(g, line, UiTheme.BODY.deriveFont(12f), tx, ly, UiTheme.PARCHMENT);
            ly += 15;
        }
        g.setComposite(saved);
        if (!learned) {
            UiTheme.drawOutlinedCentered(g, "Niveau " + e.level(), UiTheme.TITLE.deriveFont(15f), r.getMaxX() - 52, r.y + 24,
                    UiTheme.PARCHMENT, Color.BLACK);
        } else if (on) {
            UiTheme.drawOutlinedCentered(g, "Équipé", UiTheme.BODY_BOLD.deriveFont(12f), r.getMaxX() - 36, r.getMaxY() - 10,
                    UiTheme.HEAL, Color.BLACK);
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        for (Map.Entry<SpellBook.Entry, Rectangle> c : cards.entrySet()) {
            if (!c.getValue().contains(x, y)) continue;
            toggle(c.getKey());
            return;
        }
        if (save.press(x, y) || defaults.press(x, y)) return;
        back.press(x, y);
    }

    private void toggle(SpellBook.Entry e) {
        if (e.level() > hero.level()) {
            flash(e.spell().name() + " s'apprend au niveau " + e.level() + ".");
            return;
        }
        if (equipped.remove(e.id())) {
            if (equipped.isEmpty()) {
                equipped.add(e.id());
                flash("Il faut garder au moins un sort.");
            }
            return;
        }
        if (e.ultimate()) {
            equipped.removeIf(id -> {
                SpellBook.Entry x = SpellBook.entry(hero.classId(), id);
                return x != null && x.ultimate();
            });
        } else if (normalCount() >= SpellBook.MAX_EQUIPPED) {
            flash("8 sorts au maximum : retirez-en un d'abord.");
            return;
        }
        // garde l'ordre du grimoire (c'est l'ordre de la barre de sorts)
        equipped.add(e.id());
        List<String> ordered = new ArrayList<>();
        for (SpellBook.Entry x : SpellBook.of(hero.classId())) if (equipped.contains(x.id())) ordered.add(x.id());
        equipped = ordered;
    }

    @Override
    public void key(String key) {
        if ("ESCAPE".equals(key)) back.press(back.bounds().x + 1, back.bounds().y + 1);
    }
}
