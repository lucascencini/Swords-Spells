package com.minidofus.view;

import com.minidofus.engine.Assets;
import com.minidofus.engine.Easing;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.ui.UiTheme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * Représentation à l'écran d'un combattant. Elle a son propre état « affiché » (position, PV,
 * bouclier) qui rattrape le modèle au rythme des animations : le joueur voit les PV baisser au
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
    private boolean shielded;
    private double flashDamage;
    private double flashHeal;
    private double lunge;
    private double deathFade;
    private double clock;

    public FighterView(Fighter fighter, Facing initialFacing) {
        this.fighter = fighter;
        this.gx = fighter.position().x();
        this.gy = fighter.position().y();
        this.facing = initialFacing;
        this.shownHp = fighter.hp();
        this.hpBar = fighter.hp();
    }

    public Fighter fighter() { return fighter; }
    public int shownHp() { return shownHp; }
    public double hpBar() { return hpBar; }
    public boolean isShielded() { return shielded; }
    public double gx() { return gx; }
    public double gy() { return gy; }

    public void setShownHp(int hp) { this.shownHp = hp; }
    public void setShielded(boolean shielded) { this.shielded = shielded; }
    public void flashDamage() { this.flashDamage = 1; }
    public void flashHeal() { this.flashHeal = 1; }
    public void lunge() { this.lunge = 1; }

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

    /** Point au-dessus de la tête (pour les chiffres de dégâts et l'origine des projectiles). */
    public Point2D.Double worldHead() {
        Point2D.Double p = worldFeet();
        return new Point2D.Double(p.x, p.y - 62 - hop);
    }

    public void drawGroundMarks(Graphics2D g, boolean active) {
        Point2D.Double p = worldFeet();
        Composite saved = g.getComposite();
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(p.x - 16, p.y - 6, 32, 12));
        if (active && shownHp > 0) {
            double pulse = 0.5 + 0.5 * Math.sin(clock * 4);
            Color team = UiTheme.teamColor(fighter.id());
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(UiTheme.withAlpha(team, (int) (150 + 90 * pulse)));
            double rx = 20 + pulse * 2;
            g.draw(new Ellipse2D.Double(p.x - rx, p.y - rx * 0.5, rx * 2, rx));
            g.setStroke(new BasicStroke(1f));
        }
        g.setComposite(saved);
    }

    public void draw(Graphics2D g, Assets assets, PoseTable poses) {
        Point2D.Double feet = worldFeet();
        BufferedImage img = poses.image(fighter.id(), facing);
        boolean idle = hop == 0;
        double bob = idle && shownHp > 0 ? Math.sin(clock * 2.6 + fighter.id().length()) * 1.2 : 0;
        double lungeOffset = Math.sin(lunge * Math.PI) * 6;
        double dirX = facing == Facing.SE || facing == Facing.NE ? 1 : -1;
        double dirY = facing == Facing.SE || facing == Facing.SW ? 1 : -1;

        double w = img.getWidth() * SPRITE_SCALE;
        double h = img.getHeight() * SPRITE_SCALE;
        double x = feet.x - w / 2 + dirX * lungeOffset;
        double y = feet.y - h * 0.94 - hop + bob + dirY * lungeOffset * 0.5;

        Composite saved = g.getComposite();
        float alpha = (float) (1 - deathFade * 0.65);
        if (alpha < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        overlay(g, assets, img, UiTheme.DAMAGE, flashDamage * 0.75, x, y, w, h);
        overlay(g, assets, img, UiTheme.HEAL, flashHeal * 0.6, x, y, w, h);
        if (shielded && shownHp > 0) drawShieldBubble(g, feet);
        g.setComposite(saved);
    }

    private void overlay(Graphics2D g, Assets assets, BufferedImage img, Color color, double strength,
                         double x, double y, double w, double h) {
        if (strength <= 0.01) return;
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, strength)));
        BufferedImage tint = assets.tinted(img, fighter.id() + facing, color);
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
}
