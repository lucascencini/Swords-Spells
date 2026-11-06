package com.minidofus.view;

import com.minidofus.engine.Assets;
import com.minidofus.engine.Easing;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.StatusKind;
import com.minidofus.ui.UiTheme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;

/**
 * Représentation à l'écran d'un combattant. Elle a son propre état « affiché » (position, PV,
 * statuts) qui rattrape le modèle au rythme des animations : le joueur voit les PV baisser au
 * moment de l'impact, pas au moment du clic.
 */
public final class FighterView {

    private static final double SPRITE_SCALE = 0.95;

    private final Fighter fighter;
    private double gx;
    private double gy;
    private double hop;
    private Facing facing;
    private int shownHp;
    private double hpBar;
    private final Set<StatusKind> shownStatuses = EnumSet.noneOf(StatusKind.class);
    private double flashDamage;
    private double flashHeal;
    private double lunge;
    private double deathFade;
    private double fade = 1;
    private double clock;

    public FighterView(Fighter fighter, Facing initialFacing) {
        this.fighter = fighter;
        this.gx = fighter.position().x();
        this.gy = fighter.position().y();
        this.facing = initialFacing;
        this.shownHp = fighter.hp();
        this.hpBar = fighter.hp();
        this.clock = fighter.slot() * 1.7 + (fighter.team().ordinal() * 0.9);
    }

    public Fighter fighter() { return fighter; }
    public int shownHp() { return shownHp; }
    public double hpBar() { return hpBar; }
    public boolean isShielded() { return shownStatuses.contains(StatusKind.SHIELD); }
    public boolean shows(StatusKind kind) { return shownStatuses.contains(kind); }
    public double gx() { return gx; }
    public double gy() { return gy; }
    public Cell cell() { return new Cell((int) Math.round(gx), (int) Math.round(gy)); }
    public boolean isShownDead() { return shownHp <= 0; }

    public void setShownHp(int hp) { this.shownHp = hp; }
    public void setStatus(StatusKind kind, boolean active) {
        if (active) shownStatuses.add(kind); else shownStatuses.remove(kind);
    }
    public void flashDamage() { this.flashDamage = 1; }
    public void flashHeal() { this.flashHeal = 1; }
    public void lunge() { this.lunge = 1; }
    /** Opacité (0 à 1) utilisée par la téléportation. */
    public void setFade(double fade) { this.fade = fade; }

    public void setGridPosition(double x, double y, double hop) {
        this.gx = x;
        this.gy = y;
        this.hop = hop;
    }

    public void snapTo(Cell cell) {
        setGridPosition(cell.x(), cell.y(), 0);
    }

    public void face(Cell from, Cell to) {
        this.facing = Facing.of(to.x() - from.x(), to.y() - from.y(), facing);
    }

    public void update(double dt) {
        clock += dt;
        flashDamage = Math.max(0, flashDamage - dt * 5);
        flashHeal = Math.max(0, flashHeal - dt * 3);
        lunge = Math.max(0, lunge - dt * 5);
        hpBar = Easing.approach(hpBar, shownHp, 10, dt);
        if (shownHp <= 0) deathFade = Math.min(1, deathFade + dt * 1.5);
    }

    /** Profondeur pour le tri d'affichage (les objets plus « bas » à l'écran passent devant). */
    public double depth() {
        return gx + gy + 0.3;
    }

    public Point2D.Double worldFeet() {
        return new Point2D.Double(Iso.worldX(gx, gy), Iso.worldY(gx, gy));
    }

    /** Point au-dessus de la tête (chiffres de dégâts, origine des projectiles). */
    public Point2D.Double worldHead() {
        Point2D.Double p = worldFeet();
        return new Point2D.Double(p.x, p.y - 62 - hop);
    }

    /** Ombre et anneau d'équipe (épais et pulsé pour le combattant actif). */
    public void drawGroundMarks(Graphics2D g, boolean active) {
        if (shownHp <= 0) return;
        Point2D.Double p = worldFeet();
        g.setColor(new Color(0, 0, 0, (int) (70 * fade)));
        g.fill(new Ellipse2D.Double(p.x - 16, p.y - 6, 32, 12));
        Color team = UiTheme.teamColor(fighter.team());
        double pulse = active ? 0.5 + 0.5 * Math.sin(clock * 4) : 0;
        double rx = active ? 20 + pulse * 2 : 17;
        g.setStroke(new BasicStroke(active ? 2.6f : 1.6f));
        g.setColor(UiTheme.withAlpha(team, (int) ((active ? 150 + 90 * pulse : 150) * fade)));
        g.draw(new Ellipse2D.Double(p.x - rx, p.y - rx * 0.5, rx * 2, rx));
        g.setStroke(new BasicStroke(1f));
    }

    public void draw(Graphics2D g, Assets assets, PoseTable poses) {
        Point2D.Double feet = worldFeet();
        BufferedImage img = poses.image(fighter.classId(), facing);
        boolean idle = hop == 0 && shownHp > 0;
        double bob = idle ? Math.sin(clock * 2.6) * 1.2 : 0;
        double lungeOffset = Math.sin(lunge * Math.PI) * 6;
        double dirX = facing == Facing.SE || facing == Facing.NE ? 1 : -1;
        double dirY = facing == Facing.SE || facing == Facing.SW ? 1 : -1;

        double w = img.getWidth() * SPRITE_SCALE;
        double h = img.getHeight() * SPRITE_SCALE;
        double x = feet.x - w / 2 + dirX * lungeOffset;
        double y = feet.y - h * 0.94 - hop + bob + dirY * lungeOffset * 0.5;

        Composite saved = g.getComposite();
        float alpha = (float) ((1 - deathFade * 0.65) * fade);
        if (alpha < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        overlay(g, assets, img, UiTheme.DAMAGE, flashDamage * 0.75, x, y, w, h);
        overlay(g, assets, img, UiTheme.HEAL, flashHeal * 0.6, x, y, w, h);
        if (shows(StatusKind.POISON) && shownHp > 0) {
            overlay(g, assets, img, UiTheme.POISON, 0.12 + 0.1 * Math.sin(clock * 3), x, y, w, h);
        }
        if (isShielded() && shownHp > 0) drawShieldBubble(g, feet);
        g.setComposite(saved);
        if (shownHp > 0 && fade > 0.5) drawStatusBadges(g, feet.x, y - 6);
    }

    private void overlay(Graphics2D g, Assets assets, BufferedImage img, Color color, double strength,
                         double x, double y, double w, double h) {
        if (strength <= 0.01) return;
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, strength)));
        BufferedImage tint = assets.tinted(img, fighter.classId() + facing, color);
        g.drawImage(tint, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        g.setComposite(saved);
    }

    private void drawShieldBubble(Graphics2D g, Point2D.Double feet) {
        double pulse = 0.5 + 0.5 * Math.sin(clock * 3);
        double cx = feet.x;
        double cy = feet.y - 32;
        float r = 34;
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, r,
                new float[] {0f, 0.75f, 1f},
                new Color[] {new Color(136, 204, 255, 0), new Color(136, 204, 255, 40), new Color(170, 220, 255, (int) (110 + 60 * pulse))}));
        g.fill(new Ellipse2D.Double(cx - 26, cy - r, 52, r * 2));
    }

    /** Petites pastilles au-dessus de la tête : poison (goutte verte), silence (bulle barrée). */
    private void drawStatusBadges(Graphics2D g, double cx, double top) {
        int count = (shows(StatusKind.POISON) ? 1 : 0) + (shows(StatusKind.SILENCE) ? 1 : 0);
        if (count == 0) return;
        double x = cx - (count - 1) * 8;
        if (shows(StatusKind.POISON)) {
            badge(g, x, top, UiTheme.POISON);
            Path2D drop = new Path2D.Double();
            drop.moveTo(x, top - 4);
            drop.curveTo(x + 4, top + 1, x + 3, top + 4, x, top + 4);
            drop.curveTo(x - 3, top + 4, x - 4, top + 1, x, top - 4);
            g.setColor(Color.WHITE);
            g.fill(drop);
            x += 16;
        }
        if (shows(StatusKind.SILENCE)) {
            badge(g, x, top, UiTheme.SILENCE);
            g.setColor(new Color(60, 40, 90));
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine((int) x - 3, (int) top - 3, (int) x + 3, (int) top + 3);
            g.draw(new Ellipse2D.Double(x - 4, top - 4, 8, 8));
            g.setStroke(new BasicStroke(1f));
        }
    }

    private static void badge(Graphics2D g, double x, double y, Color color) {
        g.setColor(new Color(20, 15, 10, 200));
        g.fill(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
        g.setColor(color);
        g.draw(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
    }
}
