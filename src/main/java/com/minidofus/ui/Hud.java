package com.minidofus.ui;

import com.minidofus.engine.Assets;
import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.view.FighterView;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.awt.LinearGradientPaint;

/**
 * Interface de combat dessinée en Java2D : fiches des combattants, bandeau de tour, barre d'actions,
 * info-bulles, bannières et écran de fin. Toutes les zones cliquables sont calculées par {@link #layout}.
 */
public final class Hud {

    private static final int BUTTON_W = 74;
    private static final int BUTTON_H = 84;
    private static final int END_W = 118;
    private static final int GAP = 10;

    private final Assets assets;
    private final Icons icons;
    private final List<Rectangle> spellButtons = new ArrayList<>();
    private Rectangle moveButton = new Rectangle();
    private Rectangle endButton = new Rectangle();
    private Rectangle muteButton = new Rectangle();
    private Rectangle replayButton = new Rectangle();
    private Rectangle newMapButton = new Rectangle();
    private Rectangle bar = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    private String bannerTitle;
    private String bannerSubtitle;
    private Color bannerColor = UiTheme.PARCHMENT;
    private double bannerTime;
    private double bannerDuration;
    private double endScreenTime;
    private double fps;

    public Hud(Assets assets) {
        this.assets = assets;
        this.icons = new Icons(assets);
    }

    public void layout(int w, int h, int spellCount) {
        width = w;
        height = h;
        int total = BUTTON_W * (spellCount + 1) + GAP * spellCount + 22 + END_W;
        int x = (w - total) / 2;
        int y = h - BUTTON_H - 18;
        bar = new Rectangle(x - 14, y - 10, total + 28, BUTTON_H + 20);
        moveButton = new Rectangle(x, y, BUTTON_W, BUTTON_H);
        spellButtons.clear();
        for (int i = 0; i < spellCount; i++) {
            spellButtons.add(new Rectangle(x + (BUTTON_W + GAP) * (i + 1), y, BUTTON_W, BUTTON_H));
        }
        endButton = new Rectangle(x + total - END_W, y, END_W, BUTTON_H);
        muteButton = new Rectangle(w - 46, h - 44, 30, 30);
        replayButton = new Rectangle(w / 2 - 170, h / 2 + 22, 160, 40);
        newMapButton = new Rectangle(w / 2 + 10, h / 2 + 22, 160, 40);
    }

    public void setMouse(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public void setFps(double fps) {
        this.fps = fps;
    }

    /** Le point est-il sur une partie opaque du HUD (les clics n'atteignent alors pas la carte) ? */
    public boolean covers(int x, int y, boolean over) {
        if (over) return true;
        return bar.contains(x, y) || muteButton.contains(x, y) || y < 100 && (x < 290 || x > width - 290 || Math.abs(x - width / 2) < 150);
    }

    public HudAction hit(int x, int y, HudModel m) {
        if (m.isOver()) {
            if (endScreenTime < 0.6) return null;
            if (replayButton.contains(x, y)) return HudAction.of(HudAction.Type.REPLAY);
            if (newMapButton.contains(x, y)) return HudAction.of(HudAction.Type.NEW_MAP);
            return null;
        }
        if (muteButton.contains(x, y)) return HudAction.of(HudAction.Type.MUTE);
        if (moveButton.contains(x, y)) return HudAction.of(HudAction.Type.MOVE);
        if (endButton.contains(x, y)) return HudAction.of(HudAction.Type.END_TURN);
        for (int i = 0; i < spellButtons.size(); i++) {
            if (spellButtons.get(i).contains(x, y)) return new HudAction(HudAction.Type.SPELL, i);
        }
        return null;
    }

    public void showBanner(String title, String subtitle, Color color, double seconds) {
        bannerTitle = title;
        bannerSubtitle = subtitle;
        bannerColor = color;
        bannerDuration = seconds;
        bannerTime = 0;
    }

    public void update(double dt, HudModel m) {
        if (bannerTitle != null) {
            bannerTime += dt;
            if (bannerTime >= bannerDuration) bannerTitle = null;
        }
        endScreenTime = m.isOver() && !m.isBusy() ? endScreenTime + dt : 0;
    }

    // ------------------------------------------------------------------ rendu

    public void draw(Graphics2D g, HudModel m) {
        List<FighterView> views = m.fighterViews();
        drawCard(g, views.get(0), 16, 14, false, m.active() == views.get(0).fighter() && !m.isOver());
        drawCard(g, views.get(1), width - 16 - 262, 14, true, m.active() == views.get(1).fighter() && !m.isOver());
        drawTurnPanel(g, m);
        if (!m.isOver()) {
            drawActionBar(g, m);
            drawHint(g, m);
        }
        drawFooter(g, m);
        drawBanner(g);
        if (m.isOver() && !m.isBusy()) drawEndScreen(g, m);
    }

    private void drawCard(Graphics2D g, FighterView v, int x, int y, boolean mirrored, boolean active) {
        Fighter f = v.fighter();
        int w = 262;
        int h = 82;
        Composite saved = g.getComposite();
        if (!active) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
        UiTheme.panel(g, x, y, w, h, 16, active);

        BufferedImage portrait = assets.image("ui/portrait_" + f.id());
        int ph = 66;
        int pw = (int) Math.round(portrait.getWidth() * (ph / (double) portrait.getHeight()));
        int px = mirrored ? x + w - 8 - pw : x + 8;
        g.drawImage(portrait, px, y + 8, pw, ph, null);

        int tx = mirrored ? x + 12 : x + 16 + pw;
        int textW = w - pw - 30;
        g.setFont(UiTheme.TITLE.deriveFont(16f));
        g.setColor(UiTheme.teamTextColor(f.id()));
        g.drawString(f.name(), tx, y + 24);
        if (v.isShielded() && v.shownHp() > 0) {
            BufferedImage shield = assets.image("ui/icon_shield");
            g.drawImage(shield, tx + textW - 18, y + 8, 18, 21, null);
        }

        // barre de vie (valeur lissée) et texte
        int barY = y + 32;
        g.setColor(UiTheme.HP_BACK);
        g.fill(new RoundRectangle2D.Double(tx, barY, textW, 13, 7, 7));
        double ratio = Math.max(0, v.hpBar() / f.maxHp());
        Color team = UiTheme.teamColor(f.id());
        g.setPaint(new GradientPaint(tx, 0, team.darker().darker(), tx + textW, 0, team));
        g.fill(new RoundRectangle2D.Double(tx, barY, textW * ratio, 13, 7, 7));
        g.setColor(UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(tx, barY, textW, 13, 7, 7));
        UiTheme.drawCentered(g, Math.max(0, v.shownHp()) + " / " + f.maxHp(), UiTheme.BODY_BOLD.deriveFont(11f),
                tx + textW / 2.0, barY + 11, Color.WHITE);

        drawPips(g, "PA", f.pa(), f.fighterClass().maxPa(), UiTheme.PA_COLOR, tx, y + 58);
        drawPips(g, "PM", f.pm(), f.fighterClass().maxPm(), UiTheme.PM_COLOR, tx, y + 74);
        g.setComposite(saved);
    }

    private void drawPips(Graphics2D g, String label, int value, int max, Color color, int x, int baseline) {
        g.setFont(UiTheme.BODY_BOLD.deriveFont(11f));
        g.setColor(UiTheme.TEXT_DIM);
        g.drawString(label, x, baseline);
        for (int i = 0; i < max; i++) {
            Ellipse2D pip = new Ellipse2D.Double(x + 24 + i * 14, baseline - 10, 10, 10);
            g.setColor(i < value ? color : new Color(60, 50, 40));
            g.fill(pip);
            g.setColor(i < value ? color.brighter() : new Color(90, 75, 55));
            g.draw(pip);
        }
        g.setColor(UiTheme.PARCHMENT);
        g.drawString(String.valueOf(value), x + 28 + max * 14, baseline);
    }

    private void drawTurnPanel(Graphics2D g, HudModel m) {
        int w = 260;
        int x = (width - w) / 2;
        UiTheme.panel(g, x, 14, w, 58, 14, false);
        Fighter a = m.isOver() ? m.winner() : m.active();
        String title = m.isOver() ? "Combat terminé" : "Tour du " + a.name();
        UiTheme.drawCentered(g, title, UiTheme.TITLE.deriveFont(17f),
                width / 2.0, 40, m.isOver() ? UiTheme.GOLD_LIGHT : UiTheme.teamTextColor(a.id()));
        UiTheme.drawCentered(g, "Tour " + ((m.turn() + 1) / 2) + "  ·  " + m.mapName(), UiTheme.BODY.deriveFont(11f),
                width / 2.0, 60, UiTheme.TEXT_DIM);
    }

    private void drawActionBar(Graphics2D g, HudModel m) {
        Fighter f = m.active();
        boolean busy = m.isBusy();
        g.setColor(new Color(10, 8, 5, 120));
        g.fill(new RoundRectangle2D.Double(bar.x, bar.y, bar.width, bar.height, 20, 20));

        drawButton(g, moveButton, icons.move(), "Déplacement", f.pm() + " PM", null,
                m.selectedSpell() == null, busy || f.pm() == 0, "D");
        List<Spell> spells = f.fighterClass().spells();
        for (int i = 0; i < spellButtons.size() && i < spells.size(); i++) {
            Spell s = spells.get(i);
            boolean affordable = f.pa() >= s.cost();
            drawButton(g, spellButtons.get(i), icons.spell(s.style()), s.name(), null, s.cost() + "",
                    m.selectedSpell() == s, busy || !affordable, String.valueOf(i + 1));
        }
        drawEndButton(g, busy);

        for (int i = 0; i < spellButtons.size() && i < spells.size(); i++) {
            if (spellButtons.get(i).contains(mouseX, mouseY)) drawTooltip(g, spellButtons.get(i), spells.get(i), f);
        }
    }

    private void drawButton(Graphics2D g, Rectangle r, BufferedImage icon, String name, String sub, String paCost,
                            boolean selected, boolean disabled, String key) {
        boolean hover = r.contains(mouseX, mouseY) && !disabled;
        Composite saved = g.getComposite();
        if (disabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        int lift = hover ? -2 : 0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y + lift, r.width, r.height, 14, 14);
        g.setPaint(selected ? new GradientPaint(0, r.y, new Color(0xC9A227), 0, r.y + r.height, new Color(0x7A5E14))
                : new GradientPaint(0, r.y, new Color(0x3A3020), 0, r.y + r.height, new Color(0x241C12)));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(selected ? UiTheme.GOLD_LIGHT : hover ? UiTheme.GOLD : UiTheme.BRONZE);
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));

        int iconH = 44;
        int iconW = (int) Math.round(icon.getWidth() * (iconH / (double) icon.getHeight()));
        g.drawImage(icon, r.x + (r.width - iconW) / 2, r.y + 6 + lift, iconW, iconH, null);

        Font small = UiTheme.BODY_BOLD.deriveFont(10.5f);
        Color text = selected ? new Color(0x1A1208) : UiTheme.PARCHMENT;
        drawFitted(g, name, small, r.x + r.width / 2.0, r.y + 64 + lift, r.width - 6, text);
        if (sub != null) {
            UiTheme.drawCentered(g, sub, UiTheme.BODY.deriveFont(10f), r.x + r.width / 2.0, r.y + 77 + lift,
                    selected ? new Color(0x3A2A08) : UiTheme.TEXT_DIM);
        }
        if (paCost != null) {
            Ellipse2D badge = new Ellipse2D.Double(r.x + r.width - 22, r.y - 6 + lift, 24, 24);
            g.setColor(new Color(0x1A4A7A));
            g.fill(badge);
            g.setColor(UiTheme.PA_COLOR.brighter());
            g.draw(badge);
            UiTheme.drawCentered(g, paCost, UiTheme.BODY_BOLD.deriveFont(12f), r.x + r.width - 10, r.y + 10 + lift, Color.WHITE);
        }
        g.setFont(UiTheme.BODY_BOLD.deriveFont(10f));
        g.setColor(selected ? new Color(0x3A2A08) : new Color(255, 255, 255, 110));
        g.drawString(key, r.x + 6, r.y + 14 + lift);
        g.setComposite(saved);
    }

    private void drawEndButton(Graphics2D g, boolean busy) {
        Rectangle r = endButton;
        boolean hover = r.contains(mouseX, mouseY) && !busy;
        Composite saved = g.getComposite();
        if (busy) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        int lift = hover ? -2 : 0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y + lift, r.width, r.height, 14, 14);
        g.setPaint(new GradientPaint(0, r.y, new Color(0x2D7A40), 0, r.y + r.height, new Color(0x184A26)));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(hover ? new Color(0x9CF0AE) : new Color(0x4A9C5A));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        UiTheme.drawCentered(g, "Fin du tour", UiTheme.TITLE.deriveFont(15f), r.x + r.width / 2.0, r.y + 44 + lift, new Color(0xE8FFE8));
        UiTheme.drawCentered(g, "Espace", UiTheme.BODY.deriveFont(10.5f), r.x + r.width / 2.0, r.y + 62 + lift, new Color(0xA8D8B0));
        g.setComposite(saved);
    }

    private void drawTooltip(Graphics2D g, Rectangle anchor, Spell s, Fighter caster) {
        String range = s.maxRange() == 0 ? "Sur soi" : s.minRange() == s.maxRange() ? "Portée " + s.maxRange()
                : "Portée " + s.minRange() + " à " + s.maxRange();
        String target = switch (s.targetMode()) {
            case ENEMY -> "Cible : un ennemi";
            case CELL -> "Cible : une case (zone en croix)";
            case SELF -> "Cible : le lanceur";
            case LINE -> "Cible : en ligne droite";
        };
        String[] lines = {s.cost() + " PA  ·  " + range, s.effectSummary(), target};
        Font body = UiTheme.BODY.deriveFont(12f);
        FontMetrics fm = g.getFontMetrics(body);
        int w = 30 + fm.stringWidth(s.name()) + 20;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l) + 28);
        int h = 32 + lines.length * 17;
        int x = Math.max(8, Math.min(width - w - 8, anchor.x + anchor.width / 2 - w / 2));
        int y = anchor.y - h - 14;
        UiTheme.panel(g, x, y, w, h, 10, false);
        g.setFont(UiTheme.TITLE.deriveFont(14f));
        g.setColor(UiTheme.GOLD_LIGHT);
        g.drawString(s.name(), x + 14, y + 22);
        g.setFont(body);
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 && caster.pa() < s.cost() ? UiTheme.DAMAGE : UiTheme.PARCHMENT);
            g.drawString(lines[i], x + 14, y + 42 + i * 17);
        }
    }

    private void drawHint(Graphics2D g, HudModel m) {
        String hint = m.hint();
        if (hint == null || hint.isEmpty()) return;
        UiTheme.drawOutlinedCentered(g, hint, UiTheme.BODY_BOLD.deriveFont(13f), width / 2.0, bar.y - 12,
                UiTheme.PARCHMENT, new Color(0, 0, 0, 170));
    }

    private void drawFooter(Graphics2D g, HudModel m) {
        g.setFont(UiTheme.BODY.deriveFont(11f));
        String[] help = {"Molette : zoom", "Clic droit glissé : déplacer la vue", "C : recentrer  ·  M : son",
                "Échap ou clic droit : annuler"};
        FontMetrics fm = g.getFontMetrics();
        int helpW = 0;
        for (String line : help) helpW = Math.max(helpW, fm.stringWidth(line));
        if (m.isOver() || helpW + 32 < bar.x) {
            for (int i = 0; i < help.length; i++) {
                UiTheme.drawOutlinedLeft(g, help[i], g.getFont(), 16, height - 14 - (help.length - 1 - i) * 15,
                        new Color(255, 255, 255, 200), new Color(0, 0, 0, 120));
            }
        }
        if (fps > 0) {
            UiTheme.drawOutlinedLeft(g, String.format("%.0f img/s", fps), g.getFont(), width - 120, height - 20,
                    Color.WHITE, new Color(0, 0, 0, 160));
        }

        Rectangle r = muteButton;
        boolean hover = r.contains(mouseX, mouseY);
        g.setColor(new Color(20, 15, 10, hover ? 220 : 160));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        g.setColor(hover ? UiTheme.GOLD : UiTheme.BRONZE);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        drawSpeaker(g, r.x + 8, r.y + 9, m.isMuted());
    }

    private static void drawSpeaker(Graphics2D g, int x, int y, boolean muted) {
        g.setColor(UiTheme.PARCHMENT);
        int[] px = {x, x + 4, x + 9, x + 9, x + 4, x};
        int[] py = {y + 4, y + 4, y, y + 12, y + 8, y + 8};
        g.fillPolygon(px, py, 6);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (muted) {
            g.setColor(UiTheme.DAMAGE);
            g.drawLine(x + 11, y + 3, x + 16, y + 9);
            g.drawLine(x + 16, y + 3, x + 11, y + 9);
        } else {
            g.drawArc(x + 7, y + 2, 6, 8, -60, 120);
            g.drawArc(x + 7, y - 1, 10, 14, -60, 120);
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** Bandeau translucide en travers de l'écran : lisible sans masquer les personnages. */
    private void drawBanner(Graphics2D g) {
        if (bannerTitle == null) return;
        double t = bannerTime / bannerDuration;
        double alpha = t < 0.12 ? t / 0.12 : t > 0.75 ? (1 - t) / 0.25 : 1;
        double slide = (1 - Math.min(1, t / 0.12)) * 40;
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        int h = bannerSubtitle == null ? 46 : 68;
        int y = (int) (height * 0.40 - h / 2.0);
        float cx = width / 2f;
        Color dark = new Color(20, 14, 8, 175);
        Color clear = new Color(20, 14, 8, 0);
        g.setPaint(new LinearGradientPaint(0, 0, width, 0, new float[] {0f, 0.3f, 0.7f, 1f},
                new Color[] {clear, dark, dark, clear}));
        g.fillRect(0, y, width, h);
        Color gold = UiTheme.withAlpha(UiTheme.GOLD, 200);
        Color goldClear = UiTheme.withAlpha(UiTheme.GOLD, 0);
        g.setPaint(new LinearGradientPaint(0, 0, width, 0, new float[] {0f, 0.35f, 0.65f, 1f},
                new Color[] {goldClear, gold, gold, goldClear}));
        g.fillRect(0, y, width, 2);
        g.fillRect(0, y + h - 2, width, 2);
        UiTheme.drawOutlinedCentered(g, bannerTitle, UiTheme.TITLE.deriveFont(24f), cx + slide, y + 32,
                bannerColor, new Color(0, 0, 0, 160));
        if (bannerSubtitle != null) {
            UiTheme.drawCentered(g, bannerSubtitle, UiTheme.BODY_BOLD.deriveFont(14f), cx - slide, y + 55, UiTheme.PARCHMENT);
        }
        g.setComposite(saved);
    }

    private void drawEndScreen(Graphics2D g, HudModel m) {
        float a = (float) Math.min(1, endScreenTime / 0.6);
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(5, 10, 5, 150));
        g.fillRect(0, 0, width, height);
        int w = 420;
        int h = 180;
        int x = (width - w) / 2;
        int y = height / 2 - 110;
        UiTheme.panel(g, x, y, w, h, 18, true);
        Fighter winner = m.winner();
        UiTheme.drawCentered(g, "Victoire !", UiTheme.TITLE.deriveFont(30f), width / 2.0, y + 50, UiTheme.GOLD_LIGHT);
        UiTheme.drawCentered(g, "Le " + winner.name() + " remporte le combat",
                UiTheme.BODY_BOLD.deriveFont(15f), width / 2.0, y + 82, UiTheme.teamTextColor(winner.id()));
        int rounds = (m.turn() + 1) / 2;
        int hpLeft = 0;
        for (FighterView v : m.fighterViews()) if (v.fighter() == winner) hpLeft = Math.max(0, v.shownHp());
        UiTheme.drawCentered(g, "en " + rounds + (rounds > 1 ? " tours" : " tour") + "  ·  " + hpLeft + " PV restants",
                UiTheme.BODY.deriveFont(13f), width / 2.0, y + 106, UiTheme.TEXT_DIM);
        drawPlainButton(g, replayButton, "Rejouer (R)", UiTheme.GOLD);
        drawPlainButton(g, newMapButton, "Nouvelle carte (N)", new Color(0x4A9C5A));
        g.setComposite(saved);
    }

    private void drawPlainButton(Graphics2D g, Rectangle r, String label, Color accent) {
        boolean hover = r.contains(mouseX, mouseY);
        g.setPaint(new GradientPaint(0, r.y, hover ? accent : accent.darker(), 0, r.y + r.height, accent.darker().darker()));
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
        g.setColor(hover ? Color.WHITE : accent.brighter());
        g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
        UiTheme.drawCentered(g, label, UiTheme.BODY_BOLD.deriveFont(14f), r.x + r.width / 2.0, r.y + 26, Color.WHITE);
    }

    /** Texte centré, réduit si nécessaire pour tenir dans {@code maxW}. */
    private static void drawFitted(Graphics2D g, String text, Font font, double cx, double baseline, int maxW, Color color) {
        Font f = font;
        while (g.getFontMetrics(f).stringWidth(text) > maxW && f.getSize2D() > 8) f = f.deriveFont(f.getSize2D() - 0.5f);
        UiTheme.drawCentered(g, text, f, cx, baseline, color);
    }
}
