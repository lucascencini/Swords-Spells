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
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Représentation à l'écran d'un combattant. Elle a son propre état « affiché » (position, PV,
 * statuts) qui rattrape le modèle au rythme des animations : le joueur voit les PV baisser au
 * moment de l'impact, pas au moment du clic.
 */
public final class FighterView {

    /** Images de marche par case parcourue (cycle de 4 images = 2 cases). */
    private static final double WALK_FRAMES_PER_CELL = 2.0;

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
    private double fade = 1;
    private double clock;
    private double castGlow;
    private Color castColor = Color.WHITE;
    private double deathProgress;
    private double stepPhase;
    private boolean walking;
    private double walkDistance;

    private Projection projection = Projection.ISO;

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
    /** Incantation : aura et étincelles de la couleur du sort. */
    public void cast(Color color) {
        this.castColor = color;
        this.castGlow = 1;
    }
    /** Progression de la chute (0 = debout, 1 = au sol et disparu). */
    public void setDeathProgress(double p) { this.deathProgress = p; }
    public double deathProgress() { return deathProgress; }
    /** Opacité (0 à 1) utilisée par la téléportation. */
    public void setFade(double fade) { this.fade = fade; }

    /** Pendant un déplacement à pied : active le cycle de marche (classes qui en ont un). */
    public void setWalking(boolean walking) {
        this.walking = walking;
        if (!walking) walkDistance = 0;
    }

    public void setGridPosition(double x, double y, double hop) {
        double d = Math.hypot(x - gx, y - gy);
        walkDistance += d;
        if (hop > 0) stepPhase += d * Math.PI;
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
        castGlow = Math.max(0, castGlow - dt * 1.8);
        hpBar = Easing.approach(hpBar, shownHp, 10, dt);

    }

    /** Profondeur pour le tri d'affichage (les objets plus « bas » à l'écran passent devant). */
    public double depth() {
        return gx + gy + 0.3;
    }

    public Point2D.Double worldFeet() {
        return projection.point(gx, gy);
    }

    /** Projection de la carte (perspective sur une carte illustrée). */
    public void setProjection(Projection projection) {
        this.projection = projection;
    }

    /** Réduction due à la perspective à l'endroit où se tient le combattant (1 en isométrique). */
    public double depthScale() {
        return projection.scaleAt(gx, gy);
    }

    /** Point au-dessus de la tête (chiffres de dégâts, origine des projectiles). */
    public Point2D.Double worldHead() {
        Point2D.Double p = worldFeet();
        return new Point2D.Double(p.x, p.y - (62 + hop) * depthScale());
    }

    /** Ombre et anneau d'équipe (épais et pulsé pour le combattant actif) ; petite stèle une fois mort. */
    public void drawGroundMarks(Graphics2D g, boolean active) {
        Point2D.Double p = worldFeet();
        if (deathProgress >= 1) {
            drawGrave(g, p);
            return;
        }
        if (shownHp <= 0) return;
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

    /**
     * Dessin du combattant. Le pack ne fournit que des poses fixes : toutes les animations sont
     * procédurales (respiration, balancement de marche, élan, recul, aura d'incantation, chute).
     */
    public void draw(Graphics2D g, Assets assets, PoseTable poses) {
        if (deathProgress >= 1) return;
        Point2D.Double feet = worldFeet();
        String classId = fighter.classId();
        boolean animatedWalk = poses.hasWalk(classId);
        BufferedImage img = poses.image(classId, facing);
        if (walking && animatedWalk) {
            List<BufferedImage> frames = poses.walk(classId, facing);
            img = frames.get((int) (walkDistance * WALK_FRAMES_PER_CELL) % frames.size());
        }
        // avec un vrai cycle de marche, plus besoin de petits bonds ni de balancement
        double hop = animatedWalk && walking ? this.hop * 0.15 : this.hop;
        double spriteScale = poses.scale(classId);
        boolean idle = hop == 0 && !walking && shownHp > 0 && deathProgress == 0;
        double breathe = idle ? Math.sin(clock * 2.6) : 0;
        double lungeOffset = Math.sin(lunge * Math.PI) * 6;
        double recoil = flashDamage > 0 ? Math.sin(flashDamage * Math.PI * 4) * 2.5 * flashDamage : 0;
        double dirX = facing == Facing.SE || facing == Facing.NE ? 1 : -1;
        double dirY = facing == Facing.SE || facing == Facing.SW ? 1 : -1;

        double cast = castGlow > 0 ? Math.sin(Math.min(1, castGlow) * Math.PI) : 0;
        double w = img.getWidth() * spriteScale * (1 + cast * 0.05);
        double h = img.getHeight() * spriteScale * (1 + breathe * 0.012 + cast * 0.07);
        double x = -w / 2 + dirX * lungeOffset + recoil;
        double y = -h * 0.94 - hop + dirY * lungeOffset * 0.5;

        if (castGlow > 0 && shownHp > 0) drawCastAura(g, feet, cast);

        AffineTransform saved = g.getTransform();
        Composite savedComposite = g.getComposite();
        g.translate(feet.x, feet.y);
        double tilt = hop > 0 && !animatedWalk ? Math.sin(stepPhase) * 0.07 : 0;
        if (deathProgress > 0) {
            double fall = Math.min(1, deathProgress / 0.55);
            tilt = dirX * fall * fall * 1.35;
            g.translate(0, deathProgress * 6);
        }
        if (tilt != 0) g.rotate(tilt);
        float alpha = (float) (fade * (deathProgress > 0.45 ? 1 - (deathProgress - 0.45) / 0.55 : 1));
        if (alpha < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, alpha)));
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        overlay(g, assets, img, UiTheme.DAMAGE, flashDamage * 0.75, x, y, w, h);
        overlay(g, assets, img, UiTheme.HEAL, flashHeal * 0.6, x, y, w, h);
        overlay(g, assets, img, castColor, cast * 0.35, x, y, w, h);
        if (shows(StatusKind.POISON) && shownHp > 0) {
            overlay(g, assets, img, UiTheme.POISON, 0.12 + 0.1 * Math.sin(clock * 3), x, y, w, h);
        }
        g.setTransform(saved);
        if (isShielded() && shownHp > 0) drawShieldBubble(g, feet);
        g.setComposite(savedComposite);
        if (shownHp > 0 && fade > 0.5) drawStatusBadges(g, feet.x, feet.y + y - 6);
    }

    /** Aura colorée au sol et étincelles qui montent pendant une incantation. */
    private void drawCastAura(Graphics2D g, Point2D.Double feet, double strength) {
        float r = (float) (26 + 14 * strength);
        g.setPaint(new RadialGradientPaint((float) feet.x, (float) feet.y - 26, r * 1.6f, new float[] {0f, 1f},
                new Color[] {UiTheme.withAlpha(castColor, (int) (120 * strength)), UiTheme.withAlpha(castColor, 0)}));
        g.fill(new Ellipse2D.Double(feet.x - r * 1.6, feet.y - 26 - r * 1.6, r * 3.2, r * 3.2));
        for (int i = 0; i < 7; i++) {
            double a = i * 0.9 + clock * 2;
            double rise = ((clock * 1.3 + i * 0.37) % 1.0);
            double sx = feet.x + Math.cos(a) * 16;
            double sy = feet.y - 6 - rise * 56;
            int alpha = (int) (230 * strength * (1 - rise));
            g.setColor(UiTheme.withAlpha(castColor.brighter(), alpha));
            g.fill(new Ellipse2D.Double(sx - 1.6, sy - 1.6, 3.2, 3.2));
        }
    }

    /** Petite stèle aux couleurs de l'équipe, là où le combattant est tombé. */
    private void drawGrave(Graphics2D g, Point2D.Double p) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new Ellipse2D.Double(p.x - 11, p.y - 4, 22, 8));
        g.setColor(new Color(150, 145, 140));
        g.fillRoundRect((int) p.x - 6, (int) p.y - 18, 12, 16, 8, 8);
        g.setColor(new Color(90, 85, 80));
        g.drawRoundRect((int) p.x - 6, (int) p.y - 18, 12, 16, 8, 8);
        g.setColor(UiTheme.teamColor(fighter.team()));
        g.fillRect((int) p.x - 1, (int) p.y - 15, 2, 9);
        g.fillRect((int) p.x - 4, (int) p.y - 12, 8, 2);
    }

    private void overlay(Graphics2D g, Assets assets, BufferedImage img, Color color, double strength,
                         double x, double y, double w, double h) {
        if (strength <= 0.01) return;
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, strength)));
        BufferedImage tint = assets.tinted(img, "pose@" + System.identityHashCode(img), color);
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
