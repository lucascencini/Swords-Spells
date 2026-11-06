package com.minidofus.ui;

import com.minidofus.engine.Assets;
import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.model.StatusKind;
import com.minidofus.model.TargetMode;
import com.minidofus.model.Team;
import com.minidofus.view.FighterView;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface de combat dessinée en Java2D : fiches des équipes, bandeau de tour et frise d'initiative,
 * barre d'actions, info-bulles, bannières et écran de fin. Les zones cliquables sont calculées par {@link #layout}.
 */
public final class Hud {

    private static final int BUTTON_W = 70;
    private static final int BUTTON_H = 82;
    private static final int END_W = 112;
    private static final int GAP = 8;
    private static final int CARD_W = 236;
    private static final int CARD_H = 64;
    private static final int CARD_GAP = 6;
    private static final int TIMELINE_SLOT = 40;

    private final Assets assets;
    private final Icons icons;
    private final List<Rectangle> spellButtons = new ArrayList<>();
    private Rectangle moveButton = new Rectangle();
    private Rectangle endButton = new Rectangle();
    private Rectangle muteButton = new Rectangle();
    private Rectangle replayButton = new Rectangle();
    private Rectangle newMapButton = new Rectangle();
    private Rectangle menuButton = new Rectangle();
    private Rectangle bar = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    private String bannerTitle;
    private String bannerSubtitle;
    private Color bannerColor = UiTheme.PARCHMENT;
    private Color bannerAccent = UiTheme.GOLD;
    private BufferedImage bannerPortrait;
    private double bannerTime;
    private double bannerDuration;
    private boolean introRunning;
    private Color pulseColor = UiTheme.GOLD;
    private double pulse;
    private double timelineCursor = -1;
    private double endScreenTime;
    private double fps;

    public Hud(Assets assets) {
        this.assets = assets;
        this.icons = new Icons(assets);
    }

    public void layout(int w, int h, int spellCount) {
        width = w;
        height = h;
        int total = BUTTON_W * (spellCount + 1) + GAP * spellCount + 18 + END_W;
        int x = (w - total) / 2;
        int y = h - BUTTON_H - 16;
        bar = new Rectangle(x - 12, y - 10, total + 24, BUTTON_H + 20);
        moveButton = new Rectangle(x, y, BUTTON_W, BUTTON_H);
        spellButtons.clear();
        for (int i = 0; i < spellCount; i++) {
            spellButtons.add(new Rectangle(x + (BUTTON_W + GAP) * (i + 1), y, BUTTON_W, BUTTON_H));
        }
        endButton = new Rectangle(x + total - END_W, y, END_W, BUTTON_H);
        muteButton = new Rectangle(w - 46, h - 44, 30, 30);
        replayButton = new Rectangle(w / 2 - 170, h / 2 + 56, 160, 40);
        newMapButton = new Rectangle(w / 2 + 10, h / 2 + 56, 160, 40);
    }

    public void setMouse(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public void setFps(double fps) {
        this.fps = fps;
    }

    /** Le point est-il sur une partie opaque du HUD (les clics n'atteignent alors pas la carte) ? */
    public boolean covers(int x, int y, HudModel m) {
        if (m.isOver()) return true;
        if (bar.contains(x, y) || muteButton.contains(x, y)) return true;
        int cardsBottom = 12 + cardRows(m) * (CARD_H + CARD_GAP);
        boolean cards = (x < CARD_W + 20 || x > width - CARD_W - 20) && y < cardsBottom;
        int half = Math.max(155, m.turnOrder().size() * TIMELINE_SLOT / 2 + 12);
        boolean center = Math.abs(x - width / 2) < half && y < 124;
        return cards || center;
    }

    private static int cardRows(HudModel m) {
        long red = m.fighterViews().stream().filter(v -> v.fighter().team() == Team.RED).count();
        long blue = m.fighterViews().stream().filter(v -> v.fighter().team() == Team.BLUE).count();
        return (int) Math.max(red, blue);
    }

    public HudAction hit(int x, int y, HudModel m) {
        if (m.isOver()) {
            if (endScreenTime < 0.6) return null;
            if (replayButton.contains(x, y)) return HudAction.of(HudAction.Type.REPLAY);
            if (newMapButton.contains(x, y)) return HudAction.of(HudAction.Type.NEW_MAP);
            if (menuButton.contains(x, y)) return HudAction.of(HudAction.Type.MENU);
            return null;
        }
        if (muteButton.contains(x, y)) return HudAction.of(HudAction.Type.MUTE);
        if (!m.isHumanTurn()) return null;
        if (moveButton.contains(x, y)) return HudAction.of(HudAction.Type.MOVE);
        if (endButton.contains(x, y)) return HudAction.of(HudAction.Type.END_TURN);
        for (int i = 0; i < spellButtons.size(); i++) {
            if (spellButtons.get(i).contains(x, y)) return new HudAction(HudAction.Type.SPELL, i);
        }
        return null;
    }

    /** Grande bannière d'ouverture du combat (nom de la carte). */
    public void showIntro(String title, String subtitle) {
        setBanner(title, subtitle, UiTheme.GOLD_LIGHT, UiTheme.GOLD, null, 2.4);
        introRunning = true;
    }

    /** Bannière de début de tour, avec le portrait et les couleurs de l'équipe du combattant. */
    public void showTurn(Fighter f, String subtitle) {
        pulseColor = UiTheme.teamColor(f.team());
        pulse = 1;
        if (introRunning) return; // la bannière d'ouverture annonce déjà qui commence
        setBanner(f.name(), subtitle, UiTheme.teamTextColor(f.team()), UiTheme.teamColor(f.team()),
                assets.image("ui/portrait_" + f.classId()), 1.3);
    }

    private void setBanner(String title, String subtitle, Color color, Color accent, BufferedImage portrait, double seconds) {
        bannerTitle = title;
        bannerSubtitle = subtitle;
        bannerColor = color;
        bannerAccent = accent;
        bannerPortrait = portrait;
        bannerDuration = seconds;
        bannerTime = 0;
    }

    public void update(double dt, HudModel m) {
        if (bannerTitle != null) {
            bannerTime += dt;
            if (bannerTime >= bannerDuration) {
                bannerTitle = null;
                introRunning = false;
            }
        }
        pulse = Math.max(0, pulse - dt * 1.6);
        int target = m.turnOrder().indexOf(m.active());
        if (timelineCursor < 0) timelineCursor = target;
        double diff = target - timelineCursor;
        if (diff < -1.5) timelineCursor = target; // retour en début de manche : saut direct
        else timelineCursor += diff * (1 - Math.exp(-10 * dt));
        endScreenTime = m.isOver() && !m.isBusy() ? endScreenTime + dt : 0;
    }

    // ------------------------------------------------------------------ rendu

    public void draw(Graphics2D g, HudModel m) {
        drawTurnPulse(g);
        int redRow = 0;
        int blueRow = 0;
        for (FighterView v : m.fighterViews()) {
            boolean red = v.fighter().team() == Team.RED;
            int row = red ? redRow++ : blueRow++;
            int x = red ? 12 : width - 12 - CARD_W;
            boolean active = !m.isOver() && m.active() == v.fighter();
            drawCard(g, v, x, 12 + row * (CARD_H + CARD_GAP), !red, active, label(m, v.fighter()), m.controllerLabel(v.fighter()));
        }
        drawTurnPanel(g, m);
        drawTimeline(g, m);
        if (!m.isOver()) {
            drawActionBar(g, m);
            drawHint(g, m);
        }
        drawFooter(g, m);
        drawBanner(g);
        if (m.isOver() && !m.isBusy()) drawEndScreen(g, m);
    }

    /** Nom affiché : « Guerrier », ou « Guerrier 2 » si l'équipe compte plusieurs combattants de cette classe. */
    static String label(HudModel m, Fighter f) {
        long same = m.fighterViews().stream()
                .filter(v -> v.fighter().team() == f.team() && v.fighter().classId().equals(f.classId())).count();
        if (same <= 1) return f.name();
        int rank = 1;
        for (FighterView v : m.fighterViews()) {
            if (v.fighter() == f) break;
            if (v.fighter().team() == f.team() && v.fighter().classId().equals(f.classId())) rank++;
        }
        return f.name() + " " + rank;
    }

    private void drawCard(Graphics2D g, FighterView v, int x, int y, boolean mirrored, boolean active, String name, String owner) {
        Fighter f = v.fighter();
        boolean dead = v.isShownDead();
        Composite saved = g.getComposite();
        if (!active) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dead ? 0.45f : 0.85f));
        UiTheme.panel(g, x, y, CARD_W, CARD_H, 14, active);
        Color team = UiTheme.teamColor(f.team());
        g.setColor(team);
        g.fillRoundRect(mirrored ? x + CARD_W - 5 : x + 1, y + 10, 4, CARD_H - 20, 3, 3);

        BufferedImage portrait = assets.image("ui/portrait_" + f.classId());
        int ph = 50;
        int pw = (int) Math.round(portrait.getWidth() * (ph / (double) portrait.getHeight()));
        int px = mirrored ? x + CARD_W - 10 - pw : x + 10;
        g.drawImage(portrait, px, y + 7, pw, ph, null);
        if (dead) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(px, y + 7, pw, ph);
            g.setColor(UiTheme.DAMAGE);
            g.setStroke(new BasicStroke(3f));
            g.drawLine(px + 8, y + 15, px + pw - 8, y + ph - 1);
            g.drawLine(px + pw - 8, y + 15, px + 8, y + ph - 1);
            g.setStroke(new BasicStroke(1f));
        }

        int tx = mirrored ? x + 12 : x + 18 + pw;
        int textW = CARD_W - pw - 32;
        g.setFont(UiTheme.TITLE.deriveFont(14f));
        g.setColor(UiTheme.teamTextColor(f.team()));
        g.drawString(name, tx, y + 20);
        drawStatusIcons(g, v, tx + textW, y + 8);

        int barY = y + 27;
        g.setColor(UiTheme.HP_BACK);
        g.fill(new RoundRectangle2D.Double(tx, barY, textW, 12, 6, 6));
        double ratio = Math.max(0, v.hpBar() / f.maxHp());
        g.setPaint(new GradientPaint(tx, 0, team.darker().darker(), tx + textW, 0, team));
        g.fill(new RoundRectangle2D.Double(tx, barY, textW * ratio, 12, 6, 6));
        g.setColor(UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(tx, barY, textW, 12, 6, 6));
        UiTheme.drawCentered(g, Math.max(0, v.shownHp()) + " / " + f.maxHp(), UiTheme.BODY_BOLD.deriveFont(10.5f),
                tx + textW / 2.0, barY + 10, Color.WHITE);

        // hors de son tour, on affiche ce dont le combattant disposera (ses PA/PM max), pas ses restes
        g.setFont(UiTheme.BODY_BOLD.deriveFont(11f));
        g.setColor(active ? UiTheme.PA_COLOR.brighter() : UiTheme.withAlpha(UiTheme.PA_COLOR.brighter(), 150));
        g.drawString("PA " + (active ? f.pa() : f.fighterClass().maxPa()), tx, y + 55);
        g.setColor(active ? UiTheme.PM_COLOR.brighter() : UiTheme.withAlpha(UiTheme.PM_COLOR.brighter(), 150));
        g.drawString("PM " + (active ? f.pm() : f.fighterClass().maxPm()), tx + 44, y + 55);
        String controller = owner;
        g.setFont(UiTheme.BODY.deriveFont(10.5f));
        if (controller != null) {
            FontMetrics fm = g.getFontMetrics();
            String c = controller;
            while (fm.stringWidth(c) > textW - 88 && c.length() > 3) c = c.substring(0, c.length() - 2) + "…";
            g.setColor(UiTheme.GOLD_LIGHT);
            g.drawString(c, tx + textW - fm.stringWidth(c), y + 55);
        } else {
            g.setColor(UiTheme.TEXT_DIM);
            g.drawString("Init. " + f.initiative(), tx + textW - 40, y + 55);
        }
        g.setComposite(saved);
    }

    /** Icônes des statuts actifs, alignées à droite à partir de {@code right}. */
    private void drawStatusIcons(Graphics2D g, FighterView v, int right, int top) {
        if (v.isShownDead()) return;
        int x = right - 14;
        if (v.isShielded()) {
            g.drawImage(assets.image("ui/icon_shield"), x, top, 14, 16, null);
            x -= 17;
        }
        if (v.shows(StatusKind.POISON)) {
            statusDot(g, x, top, UiTheme.POISON, "P");
            x -= 17;
        }
        if (v.shows(StatusKind.SILENCE)) statusDot(g, x, top, UiTheme.SILENCE, "S");
    }

    private static void statusDot(Graphics2D g, int x, int y, Color color, String letter) {
        g.setColor(new Color(20, 15, 10));
        g.fillOval(x, y + 1, 14, 14);
        g.setColor(color);
        g.drawOval(x, y + 1, 14, 14);
        UiTheme.drawCentered(g, letter, UiTheme.BODY_BOLD.deriveFont(9.5f), x + 7, y + 12, color);
    }

    private void drawTurnPanel(Graphics2D g, HudModel m) {
        int w = 300;
        int x = (width - w) / 2;
        UiTheme.panel(g, x, 12, w, 54, 14, false);
        String title;
        Color color;
        if (m.isOver()) {
            title = "Combat terminé";
            color = UiTheme.GOLD_LIGHT;
        } else {
            Fighter a = m.active();
            title = "Tour : " + label(m, a);
            color = UiTheme.teamTextColor(a.team());
        }
        UiTheme.drawCentered(g, title, UiTheme.TITLE.deriveFont(16f), width / 2.0, 36, color);
        UiTheme.drawCentered(g, "Manche " + m.round() + "  ·  " + m.mapName(), UiTheme.BODY.deriveFont(11f),
                width / 2.0, 56, UiTheme.TEXT_DIM);
        int left = m.secondsLeft();
        if (left >= 0 && !m.isOver()) {
            String t = left / 60 + ":" + String.format("%02d", left % 60);
            Color c = left <= 10 ? UiTheme.DAMAGE : UiTheme.PARCHMENT;
            double pulse = left <= 10 ? 1 + 0.08 * Math.sin(System.nanoTime() / 1e8) : 1;
            UiTheme.drawOutlinedCentered(g, t, UiTheme.TITLE.deriveFont((float) (17 * pulse)), x + w - 34, 44, c,
                    new Color(0, 0, 0, 180));
        }
    }

    /** Liseré coloré qui pulse sur les bords de l'écran à chaque changement de tour. */
    private void drawTurnPulse(Graphics2D g) {
        if (pulse <= 0) return;
        int a = (int) (110 * pulse * pulse);
        int band = 70;
        Color c = UiTheme.withAlpha(pulseColor, a);
        Color clear = UiTheme.withAlpha(pulseColor, 0);
        g.setPaint(new GradientPaint(0, 0, c, band, 0, clear));
        g.fillRect(0, 0, band, height);
        g.setPaint(new GradientPaint(width, 0, c, width - band, 0, clear));
        g.fillRect(width - band, 0, band, height);
        g.setPaint(new GradientPaint(0, height, c, 0, height - band, clear));
        g.fillRect(0, height - band, width, band);
    }

    /** Frise de l'ordre de jeu : le combattant actif est mis en avant, les morts sont grisés. */
    private void drawTimeline(Graphics2D g, HudModel m) {
        List<Fighter> order = m.turnOrder();
        if (order.isEmpty()) return;
        int total = order.size() * TIMELINE_SLOT;
        int x0 = (width - total) / 2;
        int y = 74;
        g.setColor(new Color(10, 8, 5, 140));
        g.fill(new RoundRectangle2D.Double(x0 - 8, y - 4, total + 16, 46, 14, 14));
        for (int i = 0; i < order.size(); i++) {
            Fighter f = order.get(i);
            FighterView v = viewOf(m, f);
            boolean active = !m.isOver() && f == m.active();
            boolean dead = v != null && v.isShownDead();
            int size = active ? 36 : 30;
            int cx = x0 + i * TIMELINE_SLOT + TIMELINE_SLOT / 2;
            int px = cx - size / 2;
            int py = y + (active ? 0 : 4);
            Shape frame = new RoundRectangle2D.Double(px - 2, py - 2, size + 4, size + 4, 8, 8);
            g.setColor(new Color(20, 15, 10));
            g.fill(frame);
            BufferedImage portrait = assets.image("ui/portrait_" + f.classId());
            Composite saved = g.getComposite();
            if (dead) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g.drawImage(portrait, px, py, size, size, null);
            g.setComposite(saved);
            g.setStroke(new BasicStroke(active ? 2.5f : 1.5f));
            g.setColor(active ? UiTheme.GOLD_LIGHT : UiTheme.teamColor(f.team()));
            g.draw(frame);
            g.setStroke(new BasicStroke(1f));
            g.setColor(UiTheme.teamColor(f.team()));
            g.fillRoundRect(cx - 8, py + size + 3, 16, 3, 2, 2);
            if (i < order.size() - 1) {
                g.setColor(new Color(255, 255, 255, 60));
                g.drawString("›", x0 + (i + 1) * TIMELINE_SLOT - 3, y + 22);
            }
        }
        if (!m.isOver() && timelineCursor >= 0) {
            double cx = x0 + timelineCursor * TIMELINE_SLOT + TIMELINE_SLOT / 2.0;
            g.setColor(UiTheme.GOLD_LIGHT);
            int[] xs = {(int) cx - 6, (int) cx + 6, (int) cx};
            int[] ys = {y + 46, y + 46, y + 40};
            g.fillPolygon(xs, ys, 3);
        }
    }

    private static FighterView viewOf(HudModel m, Fighter f) {
        for (FighterView v : m.fighterViews()) if (v.fighter() == f) return v;
        return null;
    }

    private void drawActionBar(Graphics2D g, HudModel m) {
        Fighter f = m.active();
        boolean locked = m.isBusy() || !m.isHumanTurn();
        g.setColor(new Color(10, 8, 5, 120));
        g.fill(new RoundRectangle2D.Double(bar.x, bar.y, bar.width, bar.height, 20, 20));

        drawButton(g, moveButton, icons.move(), "Déplacement", f.pm() + " PM", null, null,
                m.selectedSpell() == null, locked || f.pm() == 0, "D");
        List<Spell> spells = f.fighterClass().spells();
        for (int i = 0; i < spellButtons.size() && i < spells.size(); i++) {
            Spell s = spells.get(i);
            String reason = m.unavailableReason(s);
            String overlay = reason == null || !m.isHumanTurn() ? null : f.cooldown(s) > 0 ? String.valueOf(f.cooldown(s))
                    : f.isSilenced() ? "Silence" : null;
            drawButton(g, spellButtons.get(i), icons.spell(s.style()), s.name(), null, String.valueOf(s.cost()), overlay,
                    m.selectedSpell() == s, locked || reason != null, String.valueOf(i + 1));
        }
        drawEndButton(g, locked);
        if (!m.isHumanTurn()) {
            g.setColor(new Color(12, 9, 6, 200));
            g.fill(new RoundRectangle2D.Double(bar.getCenterX() - 150, bar.getCenterY() - 20, 300, 40, 14, 14));
            UiTheme.drawOutlinedCentered(g, m.waitingLabel(), UiTheme.TITLE.deriveFont(18f),
                    bar.getCenterX(), bar.getCenterY() + 7, UiTheme.GOLD_LIGHT, new Color(0, 0, 0, 200));
        }

        for (int i = 0; i < spellButtons.size() && i < spells.size(); i++) {
            if (spellButtons.get(i).contains(mouseX, mouseY)) drawTooltip(g, spellButtons.get(i), spells.get(i), m.unavailableReason(spells.get(i)));
        }
    }

    private void drawButton(Graphics2D g, Rectangle r, BufferedImage icon, String name, String sub, String paCost,
                            String overlay, boolean selected, boolean disabled, String key) {
        boolean hover = r.contains(mouseX, mouseY) && !disabled;
        Composite saved = g.getComposite();
        if (disabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        int lift = hover ? -2 : 0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y + lift, r.width, r.height, 14, 14);
        g.setPaint(selected ? new GradientPaint(0, r.y, new Color(0xC9A227), 0, r.y + r.height, new Color(0x7A5E14))
                : new GradientPaint(0, r.y, new Color(0x3A3020), 0, r.y + r.height, new Color(0x241C12)));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(selected ? UiTheme.GOLD_LIGHT : hover ? UiTheme.GOLD : UiTheme.BRONZE);
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));

        int iconH = 42;
        int iconW = (int) Math.round(icon.getWidth() * (iconH / (double) icon.getHeight()));
        int ix = r.x + (r.width - iconW) / 2;
        int iy = r.y + 6 + lift;
        g.drawImage(icon, ix, iy, iconW, iconH, null);
        if (overlay != null) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(ix, iy, iconW, iconH, 10, 10);
            UiTheme.drawOutlinedCentered(g, overlay, UiTheme.TITLE.deriveFont(overlay.length() > 2 ? 11f : 22f),
                    ix + iconW / 2.0, iy + iconH / 2.0 + (overlay.length() > 2 ? 4 : 8), Color.WHITE, Color.BLACK);
            if (disabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        }

        Font small = UiTheme.BODY_BOLD.deriveFont(10f);
        Color text = selected ? new Color(0x1A1208) : UiTheme.PARCHMENT;
        drawFitted(g, name, small, r.x + r.width / 2.0, r.y + 62 + lift, r.width - 6, text);
        if (sub != null) {
            UiTheme.drawCentered(g, sub, UiTheme.BODY.deriveFont(10f), r.x + r.width / 2.0, r.y + 75 + lift,
                    selected ? new Color(0x3A2A08) : UiTheme.TEXT_DIM);
        }
        if (paCost != null) {
            Ellipse2D badge = new Ellipse2D.Double(r.x + r.width - 20, r.y - 6 + lift, 22, 22);
            g.setColor(new Color(0x1A4A7A));
            g.fill(badge);
            g.setColor(UiTheme.PA_COLOR.brighter());
            g.draw(badge);
            UiTheme.drawCentered(g, paCost, UiTheme.BODY_BOLD.deriveFont(11.5f), r.x + r.width - 9, r.y + 9 + lift, Color.WHITE);
        }
        g.setFont(UiTheme.BODY_BOLD.deriveFont(10f));
        g.setColor(selected ? new Color(0x3A2A08) : new Color(255, 255, 255, 110));
        g.drawString(key, r.x + 6, r.y + 14 + lift);
        g.setComposite(saved);
    }

    private void drawEndButton(Graphics2D g, boolean locked) {
        Rectangle r = endButton;
        boolean hover = r.contains(mouseX, mouseY) && !locked;
        Composite saved = g.getComposite();
        if (locked) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        int lift = hover ? -2 : 0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y + lift, r.width, r.height, 14, 14);
        g.setPaint(new GradientPaint(0, r.y, new Color(0x2D7A40), 0, r.y + r.height, new Color(0x184A26)));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(hover ? new Color(0x9CF0AE) : new Color(0x4A9C5A));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        UiTheme.drawCentered(g, "Fin du tour", UiTheme.TITLE.deriveFont(14.5f), r.x + r.width / 2.0, r.y + 43 + lift, new Color(0xE8FFE8));
        UiTheme.drawCentered(g, "Espace", UiTheme.BODY.deriveFont(10.5f), r.x + r.width / 2.0, r.y + 61 + lift, new Color(0xA8D8B0));
        g.setComposite(saved);
    }

    private void drawTooltip(Graphics2D g, Rectangle anchor, Spell s, String reason) {
        String range = s.maxRange() == 0 ? "sur soi" : s.minRange() == s.maxRange() ? "portée " + s.maxRange()
                : "portée " + s.minRange() + " à " + s.maxRange();
        List<String> lines = new ArrayList<>();
        lines.add(s.cost() + " PA  ·  " + range + (s.cooldown() > 0 ? "  ·  relance " + s.cooldown() : ""));
        lines.add(s.effectSummary());
        String constraints = "Cible : " + s.targetMode().description();
        if (s.lineOnly() && s.targetMode() != TargetMode.LINE) constraints += ", en ligne";
        if (s.maxRange() > 1) constraints += s.needsLineOfSight() ? " · ligne de vue requise" : " · ignore les obstacles";
        lines.add(constraints);
        if (reason != null) lines.add(reason);
        Font body = UiTheme.BODY.deriveFont(12f);
        FontMetrics fm = g.getFontMetrics(body);
        int w = fm.stringWidth(s.name()) + 40;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l) + 28);
        int h = 32 + lines.size() * 17;
        int x = Math.max(8, Math.min(width - w - 8, anchor.x + anchor.width / 2 - w / 2));
        int y = anchor.y - h - 14;
        UiTheme.panel(g, x, y, w, h, 10, false);
        g.setFont(UiTheme.TITLE.deriveFont(14f));
        g.setColor(UiTheme.GOLD_LIGHT);
        g.drawString(s.name(), x + 14, y + 22);
        g.setFont(body);
        for (int i = 0; i < lines.size(); i++) {
            boolean isReason = reason != null && i == lines.size() - 1;
            g.setColor(isReason ? UiTheme.DAMAGE : i == 2 ? UiTheme.TEXT_DIM : UiTheme.PARCHMENT);
            g.drawString(lines.get(i), x + 14, y + 42 + i * 17);
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
        if (!m.online() && (m.isOver() || helpW + 32 < bar.x)) {
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
        Color gold = UiTheme.withAlpha(bannerAccent, 210);
        Color goldClear = UiTheme.withAlpha(bannerAccent, 0);
        g.setPaint(new LinearGradientPaint(0, 0, width, 0, new float[] {0f, 0.35f, 0.65f, 1f},
                new Color[] {goldClear, gold, gold, goldClear}));
        g.fillRect(0, y, width, 2);
        g.fillRect(0, y + h - 2, width, 2);
        if (bannerPortrait != null) {
            int ph = h - 14;
            int pw = (int) Math.round(bannerPortrait.getWidth() * (ph / (double) bannerPortrait.getHeight()));
            Font tf = UiTheme.TITLE.deriveFont(24f);
            int titleW = g.getFontMetrics(tf).stringWidth(bannerTitle);
            g.drawImage(bannerPortrait, (int) (cx - titleW / 2.0 - pw - 18 + slide), y + 7, pw, ph, null);
        }
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
        g.setColor(new Color(5, 10, 5, 165));
        g.fillRect(0, 0, width, height);

        List<FighterView> views = m.fighterViews();
        int rowH = 34;
        int w = Math.min(width - 40, 660);
        int h = 112 + 26 + views.size() * rowH + 74;
        int x = (width - w) / 2;
        int y = Math.max(20, (height - h) / 2);
        UiTheme.panel(g, x, y, w, h, 18, true);

        Team winner = m.winner();
        Team human = m.soloHumanTeam();
        boolean defeat = human != null && human != winner;
        UiTheme.drawCentered(g, defeat ? "Défaite…" : "Victoire !", UiTheme.TITLE.deriveFont(30f), width / 2.0, y + 46,
                defeat ? UiTheme.DAMAGE : UiTheme.GOLD_LIGHT);
        UiTheme.drawCentered(g, "L'équipe " + winner.displayName() + " remporte le combat",
                UiTheme.BODY_BOLD.deriveFont(15f), width / 2.0, y + 76, UiTheme.teamTextColor(winner));
        int seconds = (int) Math.round(m.battleSeconds());
        UiTheme.drawCentered(g, m.round() + (m.round() > 1 ? " manches" : " manche") + "  ·  "
                        + (seconds >= 60 ? seconds / 60 + " min " : "") + seconds % 60 + " s  ·  " + m.mapName(),
                UiTheme.BODY.deriveFont(13f), width / 2.0, y + 98, UiTheme.TEXT_DIM);

        // tableau des statistiques
        String[] headers = {"Dégâts", "Subis", "Soins", "K.O.", "Sorts"};
        int nameW = w - 60 - headers.length * 74;
        int tx = x + 30;
        int ty = y + 124;
        g.setFont(UiTheme.BODY_BOLD.deriveFont(11.5f));
        g.setColor(UiTheme.TEXT_DIM);
        g.drawString("Combattant", tx + 40, ty);
        for (int i = 0; i < headers.length; i++) {
            UiTheme.drawCentered(g, headers[i], g.getFont(), tx + nameW + i * 74 + 37, ty, UiTheme.TEXT_DIM);
        }
        FighterView mvp = null;
        double best = -1;
        for (FighterView v : views) {
            var st = v.fighter().stats();
            double score = st.damageDealt() + st.healingDone() * 0.8 + st.kills() * 25;
            if (score > best) {
                best = score;
                mvp = v;
            }
        }
        for (int r = 0; r < views.size(); r++) {
            FighterView v = views.get(r);
            Fighter f = v.fighter();
            var st = f.stats();
            int ry = ty + 10 + r * rowH;
            g.setColor(r % 2 == 0 ? new Color(255, 255, 255, 12) : new Color(0, 0, 0, 0));
            g.fillRoundRect(tx - 8, ry, w - 44, rowH - 2, 8, 8);
            g.setColor(UiTheme.teamColor(f.team()));
            g.fillRoundRect(tx - 8, ry + 4, 4, rowH - 10, 3, 3);
            BufferedImage portrait = assets.image("ui/portrait_" + f.classId());
            Composite c = g.getComposite();
            if (v.isShownDead()) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f * a));
            g.drawImage(portrait, tx, ry + 2, 28, 28, null);
            g.setComposite(c);
            g.setFont(UiTheme.BODY_BOLD.deriveFont(13f));
            g.setColor(UiTheme.teamTextColor(f.team()));
            String name = label(m, f) + (v.isShownDead() ? "  (K.O.)" : "");
            g.drawString(name, tx + 40, ry + 21);
            if (v == mvp && best > 0) {
                int sx = tx + 46 + g.getFontMetrics().stringWidth(name);
                drawStar(g, sx + 6, ry + 16, 7);
            }
            int[] values = {st.damageDealt(), st.damageTaken(), st.healingDone(), st.kills(), st.spellsCast()};
            for (int i = 0; i < values.length; i++) {
                UiTheme.drawCentered(g, String.valueOf(values[i]), UiTheme.BODY.deriveFont(13f),
                        tx + nameW + i * 74 + 37, ry + 21, values[i] == 0 ? UiTheme.TEXT_DIM : UiTheme.PARCHMENT);
            }
        }
        int by = y + h - 58;
        if (m.online()) {
            replayButton = new Rectangle();
            newMapButton = new Rectangle();
            menuButton = new Rectangle(width / 2 - 110, by, 220, 40);
            drawPlainButton(g, menuButton, "Retour au salon (Échap)", UiTheme.GOLD);
        } else {
            replayButton = new Rectangle(width / 2 - 250, by, 160, 40);
            newMapButton = new Rectangle(width / 2 - 80, by, 160, 40);
            menuButton = new Rectangle(width / 2 + 90, by, 160, 40);
            drawPlainButton(g, replayButton, "Rejouer (R)", UiTheme.GOLD);
            drawPlainButton(g, newMapButton, "Nouvelle carte (N)", new Color(0x4A9C5A));
            drawPlainButton(g, menuButton, "Menu (Échap)", new Color(0x6A5A44));
        }
        g.setComposite(saved);
    }

    /** Étoile dorée du meilleur combattant (dégâts + soins + éliminations). */
    private static void drawStar(Graphics2D g, double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            double rr = i % 2 == 0 ? r : r * 0.45;
            if (i == 0) p.moveTo(cx + Math.cos(ang) * rr, cy + Math.sin(ang) * rr);
            else p.lineTo(cx + Math.cos(ang) * rr, cy + Math.sin(ang) * rr);
        }
        p.closePath();
        g.setColor(UiTheme.GOLD_LIGHT);
        g.fill(p);
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
