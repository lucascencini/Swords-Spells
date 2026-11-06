package com.minidofus.ui.account;

import com.minidofus.engine.Assets;
import com.minidofus.model.CastStyle;
import com.minidofus.model.catalog.SpellBook;
import com.minidofus.model.progression.Progression;
import com.minidofus.model.progression.TalentCatalog;
import com.minidofus.model.progression.TalentDef;
import com.minidofus.model.progression.TalentEffect;
import com.minidofus.model.progression.TalentValidator;
import com.minidofus.ui.Icons;
import com.minidofus.ui.UiTheme;
import com.minidofus.ui.menu.MenuButton;
import com.minidofus.ui.menu.Screen;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Arbre de talents d'un personnage : trois branches en colonnes, quatre paliers, liens vers les talents parents.
 * Clic gauche : +1 rang ; clic droit : -1 rang. Chaque changement est vérifié avec les mêmes règles que le serveur
 * (points, niveau, prérequis) : un retrait qui casserait un prérequis est refusé. Réinitialisation gratuite.
 */
public final class TalentScreen implements Screen {

    private static final int NODE = 58;
    private static final Color[] BRANCH_COLORS = {new Color(0xE8763A), new Color(0x6AB8F0), new Color(0xB08CF0)};

    private final Assets assets;
    private final Icons icons;
    private final Consumer<Graphics2D> background;
    private final BiConsumer<AccountView.Hero, Map<String, Integer>> onSave;
    private final MenuButton save;
    private final MenuButton reset;
    private final MenuButton revert;
    private final MenuButton back;
    private final Map<TalentDef, Rectangle> nodes = new LinkedHashMap<>();
    private AccountView.Hero hero;
    private TalentCatalog.Tree tree;
    private Map<String, Integer> ranks = new TreeMap<>();
    private String message = "";
    private double messageTime;
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public TalentScreen(Assets assets, Consumer<Graphics2D> background, BiConsumer<AccountView.Hero, Map<String, Integer>> onSave,
                        Runnable onBack) {
        this.assets = assets;
        this.icons = new Icons(assets);
        this.background = background;
        this.onSave = onSave;
        save = new MenuButton("Enregistrer", null, true, () -> {
            if (hero != null) onSave.accept(hero, new TreeMap<>(ranks));
        });
        reset = new MenuButton("Tout réinitialiser", null, false, () -> {
            ranks.clear();
            flash("Talents réinitialisés (gratuit) : enregistrez pour confirmer.");
        });
        revert = new MenuButton("Annuler les changements", null, false, () -> {
            if (hero != null) ranks = new TreeMap<>(hero.talents());
        });
        back = new MenuButton("Retour", null, false, onBack);
    }

    public void open(AccountView.Hero hero) {
        this.hero = hero;
        this.tree = TalentCatalog.of(hero.classId());
        this.ranks = new TreeMap<>(hero.talents());
        message = "";
    }

    /** Après enregistrement : le serveur a renvoyé le personnage à jour. */
    public void refresh(AccountView.Hero updated) {
        if (hero != null && updated != null && updated.id().equals(hero.id())) {
            boolean dirty = !ranks.equals(hero.talents());
            hero = updated;
            if (!dirty || ranks.equals(updated.talents())) {
                ranks = new TreeMap<>(updated.talents());
                flash("Talents enregistrés.");
            }
        }
    }

    public void flash(String text) {
        message = text;
        messageTime = 5;
    }

    private int pointsLeft() {
        return Progression.talentPoints(hero.level()) - TalentValidator.spent(hero.classId(), ranks);
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 20, 1200);
        int ph = Math.min(h - 20, 790);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        int by = panel.y + ph - 62;
        back.setBounds(panel.x + 24, by, 140, 44);
        reset.setBounds(panel.x + 176, by, 210, 44);
        revert.setBounds(panel.x + 398, by, 250, 44);
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
        Ui.text(g, "Talents — " + hero.name(), UiTheme.TITLE.deriveFont(24f), panel.x + 28, panel.y + 42, UiTheme.GOLD_LIGHT);
        Ui.text(g, hero.fighter().className() + " niveau " + hero.level(), UiTheme.BODY.deriveFont(13f), panel.x + 30, panel.y + 62,
                UiTheme.TEXT_DIM);
        int left = pointsLeft();
        UiTheme.drawOutlinedCentered(g, "Points à répartir : " + left + " / " + Progression.talentPoints(hero.level()),
                UiTheme.TITLE.deriveFont(18f), panel.x + panel.width - 190, panel.y + 44, left > 0 ? UiTheme.GOLD_LIGHT : UiTheme.PARCHMENT,
                new Color(0, 0, 0, 180));
        UiTheme.drawCentered(g, "Clic gauche : +1 rang   ·   clic droit : -1 rang", UiTheme.BODY.deriveFont(12f),
                panel.x + panel.width - 190, panel.y + 64, UiTheme.TEXT_DIM);
        if (tree.talents().isEmpty()) {
            UiTheme.drawCentered(g, "Les talents de cette classe arrivent bientôt : le Mage sert de classe pilote.",
                    UiTheme.TITLE.deriveFont(18f), panel.getCenterX(), panel.getCenterY(), UiTheme.PARCHMENT);
        } else {
            drawTree(g);
        }
        back.draw(g, mouseX, mouseY);
        reset.draw(g, mouseX, mouseY);
        revert.setEnabled(!ranks.equals(hero.talents()));
        revert.draw(g, mouseX, mouseY);
        save.setEnabled(!ranks.equals(hero.talents()));
        save.draw(g, mouseX, mouseY);
        if (messageTime > 0) {
            UiTheme.drawOutlinedCentered(g, message, UiTheme.BODY_BOLD.deriveFont(13.5f), panel.x + panel.width / 2.0 + 120,
                    panel.y + panel.height - 74, UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 200));
        }
        drawTooltip(g);
    }

    private void drawTree(Graphics2D g) {
        nodes.clear();
        int top = panel.y + 84;
        int bottom = panel.y + panel.height - 80;
        int colW = (panel.width - 48) / tree.branches().size();
        int rowH = (bottom - top - 60) / 4;
        for (int b = 0; b < tree.branches().size(); b++) {
            TalentCatalog.Branch branch = tree.branches().get(b);
            int cx = panel.x + 24 + b * colW;
            Rectangle col = new Rectangle(cx + 6, top, colW - 12, bottom - top);
            g.setColor(new Color(10, 8, 5, 150));
            g.fill(new RoundRectangle2D.Double(col.x, col.y, col.width, col.height, 16, 16));
            Color bc = BRANCH_COLORS[b % BRANCH_COLORS.length];
            g.setColor(UiTheme.withAlpha(bc, 140));
            g.draw(new RoundRectangle2D.Double(col.x, col.y, col.width, col.height, 16, 16));
            int pts = TalentValidator.branchPoints(hero.classId(), branch.id(), ranks, null);
            UiTheme.drawCentered(g, branch.name() + "  (" + pts + ")", UiTheme.TITLE.deriveFont(17f), col.getCenterX(), top + 24, bc);
            UiTheme.drawCentered(g, branch.theme(), UiTheme.BODY.deriveFont(11.5f), col.getCenterX(), top + 42, UiTheme.TEXT_DIM);
            for (int tier = 1; tier <= 4; tier++) {
                List<TalentDef> row = new ArrayList<>();
                for (TalentDef t : tree.talents()) if (t.branch().equals(branch.id()) && t.tier() == tier) row.add(t);
                int y = top + 58 + (tier - 1) * rowH + (rowH - NODE) / 2 - 6;
                int gap = Math.min(30, (col.width - row.size() * NODE) / Math.max(1, row.size() + 1));
                int rowWidth = row.size() * NODE + (row.size() - 1) * gap;
                int x = (int) (col.getCenterX() - rowWidth / 2.0);
                for (TalentDef t : row) {
                    nodes.put(t, new Rectangle(x, y, NODE, NODE));
                    x += NODE + gap;
                }
                if (b == 0) {
                    Ui.text(g, tierName(tier), UiTheme.BODY.deriveFont(10.5f), col.x + 8, y - 4, UiTheme.withAlpha(UiTheme.TEXT_DIM, 160));
                }
            }
        }
        // liens parent → enfant
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Map.Entry<TalentDef, Rectangle> e : nodes.entrySet()) {
            for (String p : e.getKey().parents()) {
                TalentDef parent = tree.talent(p);
                Rectangle pr = nodes.get(parent);
                if (pr == null) continue;
                boolean on = ranks.getOrDefault(p, 0) > 0;
                g.setColor(on ? UiTheme.GOLD : new Color(90, 80, 60));
                Rectangle r = e.getValue();
                if (parent.tier() == e.getKey().tier()) {
                    // même palier : lien horizontal de côté à côté
                    boolean right = pr.x < r.x;
                    g.draw(new Line2D.Double(right ? pr.getMaxX() + 2 : pr.x - 2, pr.getCenterY(), right ? r.x - 2 : r.getMaxX() + 2, r.getCenterY()));
                } else {
                    g.draw(new Line2D.Double(pr.getCenterX(), pr.getMaxY(), r.getCenterX(), r.y));
                }
            }
        }
        g.setStroke(new BasicStroke(1f));
        for (Map.Entry<TalentDef, Rectangle> e : nodes.entrySet()) drawNode(g, e.getKey(), e.getValue());
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "Fondamentaux";
            case 2 -> "Spécialisation";
            case 3 -> "Maîtrise";
            default -> "Signature";
        };
    }

    private void drawNode(Graphics2D g, TalentDef t, Rectangle r) {
        int rank = ranks.getOrDefault(t.id(), 0);
        boolean canAdd = canChange(t, +1) == null;
        boolean locked = rank == 0 && TalentValidator.unmet(t, hero.level(), ranks) != null;
        int b = indexOfBranch(t.branch());
        Color bc = t.hybrid() ? new Color(0xE070E0) : BRANCH_COLORS[b % BRANCH_COLORS.length];
        Composite saved = g.getComposite();
        if (locked) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
        BufferedImage icon = iconOf(t);
        g.drawImage(icon, r.x, r.y, r.width, r.height, null);
        if (rank == 0) {
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
        }
        g.setComposite(saved);
        boolean hover = r.contains(mouseX, mouseY);
        g.setStroke(new BasicStroke(rank > 0 ? 3f : canAdd ? 2.2f : 1.4f));
        g.setColor(rank > 0 ? UiTheme.GOLD_LIGHT : canAdd ? (hover ? Color.WHITE : bc) : new Color(80, 70, 55));
        g.draw(new RoundRectangle2D.Double(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 16, 16));
        g.setStroke(new BasicStroke(1f));
        // rang
        String label = rank + "/" + t.maxRank();
        Font f = UiTheme.BODY_BOLD.deriveFont(11f);
        g.setColor(new Color(15, 10, 5, 230));
        g.fill(new RoundRectangle2D.Double(r.getCenterX() - 17, r.getMaxY() - 8, 34, 16, 8, 8));
        UiTheme.drawCentered(g, label, f, r.getCenterX(), r.getMaxY() + 4, rank > 0 ? UiTheme.GOLD_LIGHT : UiTheme.PARCHMENT);
        if (t.cost() > 1) {
            UiTheme.drawOutlinedCentered(g, t.cost() + " pts", UiTheme.BODY_BOLD.deriveFont(10f), r.getMaxX() - 4, r.y + 8,
                    UiTheme.GOLD_LIGHT, Color.BLACK);
        }
    }

    private int indexOfBranch(String id) {
        for (int i = 0; i < tree.branches().size(); i++) if (tree.branches().get(i).id().equals(id)) return i;
        return 0;
    }

    /** Icône : le sort visé par le talent, sinon le sort phare de la branche. */
    private BufferedImage iconOf(TalentDef t) {
        for (TalentEffect fx : t.effects()) {
            if (fx.target() != null && !fx.target().startsWith("tag:")) {
                SpellBook.Entry e = SpellBook.entry(t.classId(), fx.target());
                if (e != null) return icons.spell(e.spell().style());
            }
        }
        for (TalentEffect fx : t.effects()) {
            if ("tag:fire".equals(fx.target())) return icons.spell(CastStyle.FIREBALL);
            if ("tag:ice".equals(fx.target())) return icons.spell(CastStyle.ICE_ZONE);
            if ("tag:arcane".equals(fx.target())) return icons.spell(CastStyle.ARCANE_BOLT);
        }
        return switch (t.effects().get(0).type()) {
            case "hp_pct" -> icons.spell(CastStyle.HEAL);
            case "resist_pct" -> icons.spell(CastStyle.SHIELD);
            case "initiative" -> icons.spell(CastStyle.DASH);
            default -> icons.spell(CastStyle.OVERLOAD);
        };
    }

    /** Raison pour laquelle on ne peut pas ajouter / retirer un rang, ou {@code null}. */
    private String canChange(TalentDef t, int delta) {
        int rank = ranks.getOrDefault(t.id(), 0);
        if (delta > 0 && rank >= t.maxRank()) return "rang maximum atteint";
        if (delta < 0 && rank <= 0) return "aucun point investi";
        Map<String, Integer> next = new TreeMap<>(ranks);
        next.put(t.id(), rank + delta);
        if (delta > 0) {
            String why = TalentValidator.unmet(t, hero.level(), next);
            if (why != null) return why;
            if (pointsLeft() < t.cost()) return "plus assez de points";
        }
        String why = TalentValidator.validate(hero.classId(), hero.level(), next);
        return why == null ? null : delta < 0 ? "d'autres talents en dépendent" : why;
    }

    private void drawTooltip(Graphics2D g) {
        for (Map.Entry<TalentDef, Rectangle> e : nodes.entrySet()) {
            if (!e.getValue().contains(mouseX, mouseY)) continue;
            TalentDef t = e.getKey();
            int rank = ranks.getOrDefault(t.id(), 0);
            List<String> lines = new ArrayList<>(Ui.wrap(g, t.description(), UiTheme.BODY.deriveFont(12.5f), 300));
            List<String> reqs = new ArrayList<>();
            if (t.level() > 1) reqs.add("Niveau " + t.level());
            t.branchPoints().forEach((br, n) -> reqs.add(n + " points en " + tree.branches().get(indexOfBranch(br)).name()));
            for (String p : t.parents()) reqs.add("« " + tree.talent(p).name() + " »");
            String unmet = rank == 0 ? TalentValidator.unmet(t, hero.level(), ranks) : null;
            int h = 54 + lines.size() * 17 + (reqs.isEmpty() ? 0 : 20) + (unmet != null ? 18 : 0);
            int w = 330;
            Rectangle r = e.getValue();
            int x = Math.min(width - w - 10, r.x + r.width + 12);
            int y = Math.max(10, Math.min(height - h - 10, r.y - 10));
            UiTheme.panel(g, x, y, w, h, 10, false);
            Ui.text(g, t.name() + (t.hybrid() ? "  (hybride)" : ""), UiTheme.TITLE.deriveFont(15f), x + 12, y + 22, UiTheme.GOLD_LIGHT);
            Ui.text(g, "Rang " + rank + " / " + t.maxRank() + "  ·  " + t.cost() + " point" + (t.cost() > 1 ? "s" : "") + " par rang",
                    UiTheme.BODY.deriveFont(11.5f), x + 12, y + 40, UiTheme.TEXT_DIM);
            int ly = y + 60;
            for (String l : lines) {
                Ui.text(g, l, UiTheme.BODY.deriveFont(12.5f), x + 12, ly, UiTheme.PARCHMENT);
                ly += 17;
            }
            if (!reqs.isEmpty()) {
                Ui.text(g, "Prérequis : " + String.join(", ", reqs), UiTheme.BODY.deriveFont(11.5f), x + 12, ly + 2, UiTheme.TEXT_DIM);
                ly += 20;
            }
            if (unmet != null) Ui.text(g, "Bloqué : " + unmet, UiTheme.BODY_BOLD.deriveFont(11.5f), x + 12, ly, UiTheme.DAMAGE);
            return;
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        if (clickNode(x, y, +1)) return;
        if (save.press(x, y) || reset.press(x, y) || revert.press(x, y)) return;
        back.press(x, y);
    }

    @Override
    public void mouseRightPressed(int x, int y) {
        clickNode(x, y, -1);
    }

    private boolean clickNode(int x, int y, int delta) {
        for (Map.Entry<TalentDef, Rectangle> e : nodes.entrySet()) {
            if (!e.getValue().contains(x, y)) continue;
            TalentDef t = e.getKey();
            String why = canChange(t, delta);
            if (why != null) {
                flash(t.name() + " : " + why);
            } else {
                ranks.put(t.id(), ranks.getOrDefault(t.id(), 0) + delta);
                ranks.values().removeIf(v -> v <= 0);
            }
            return true;
        }
        return false;
    }

    @Override
    public void key(String key) {
        if ("ESCAPE".equals(key)) back.press(back.bounds().x + 1, back.bounds().y + 1);
    }
}
