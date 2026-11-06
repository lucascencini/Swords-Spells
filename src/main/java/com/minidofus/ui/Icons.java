package com.minidofus.ui;

import com.minidofus.engine.Assets;
import com.minidofus.model.CastStyle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Icônes des actions. Celles fournies par le pack (bottes, feu, bouclier, flèche) sont utilisées
 * telles quelles ; les autres sont dessinées dans le même style (cadre octogonal dégradé + glyphe blanc).
 */
public final class Icons {

    private static final int W = 52;
    private static final int H = 58;
    private static final Color GLYPH = new Color(0xFFF6E4);

    private final Assets assets;
    private final Map<CastStyle, BufferedImage> spellIcons = new EnumMap<>(CastStyle.class);

    public Icons(Assets assets) {
        this.assets = assets;
    }

    public BufferedImage move() {
        return assets.image("ui/icon_boots");
    }

    public BufferedImage spell(CastStyle style) {
        return spellIcons.computeIfAbsent(style, this::create);
    }

    private BufferedImage create(CastStyle style) {
        return switch (style) {
            case FIREBALL -> assets.image("ui/icon_fire");
            case SHIELD -> assets.image("ui/icon_shield");
            case ARROW -> assets.image("ui/icon_strike");
            case MELEE -> framed(new Color(0x9AA3AD), new Color(0x4A525C), Icons::sword);
            case STUN_STRIKE -> framed(new Color(0x9C8FD0), new Color(0x463A7A), (g, x, y) -> {
                sword(g, x, y);
                star(g, x - 12, y - 14, 4);
                star(g, x + 13, y - 9, 3);
            });
            case PUSH_STRIKE -> framed(new Color(0xC98A4E), new Color(0x6E3E14), Icons::pushGlyph);
            case DASH -> framed(new Color(0xE8963A), new Color(0x8A4A14), Icons::chevrons);
            case GRAPPLE -> framed(new Color(0x7FA3BF), new Color(0x2E4A60), Icons::hook);
            case ARCANE_BOLT -> framed(new Color(0x6A8CF0), new Color(0x22307A), (g, x, y) -> star(g, x, y, 13));
            case HEAL -> framed(new Color(0x48C774), new Color(0x1E6B3A), Icons::plus);
            case TELEPORT -> framed(new Color(0xB07CE8), new Color(0x4A1E7A), Icons::spiral);
            case POISON -> framed(new Color(0x86C84A), new Color(0x2E5A14), Icons::drop);
            case RECOIL_ARROW -> framed(new Color(0x4FB8A8), new Color(0x1A5A50), Icons::recoil);
            case TRAP -> framed(new Color(0xA88A62), new Color(0x4E3A22), Icons::trap);
            case ARROW_RAIN -> framed(new Color(0xE0B94A), new Color(0x7A5A10), Icons::rain);
            case LEAP -> framed(new Color(0x9CC8A0), new Color(0x2E5A36), Icons::leap);
            case CROW -> pictured(new Color(0x8E86B8), new Color(0x2E2848), "fx/crow_2", 30);
            case BEES -> framed(new Color(0xF0C850), new Color(0x7A5410), (g, x, y) -> {
                for (int i = 0; i < 5; i++) {
                    double a = i * 1.26;
                    picture(g, assets.image("fx/bee_" + (1 + i % 2)), x + Math.cos(a) * 11, y + Math.sin(a) * 10, 12);
                }
            });
            case TORNADO -> pictured(new Color(0xA8D890), new Color(0x2E5A24), "fx/tornado_3", 40);
            case LIGHTNING -> pictured(new Color(0x8A86C8), new Color(0x262850), "fx/lightning_3", 44);
            case AQUA_VITA -> pictured(new Color(0x5ED8C0), new Color(0x145A50), "fx/potion_1", 36);
            case ICE_ZONE -> pictured(new Color(0x8AC8F0), new Color(0x1E4870), "fx/ice_burst_4", 30);
            case STONE_SHIELD -> pictured(new Color(0xC8B898), new Color(0x4E4232), "fx/stone_1", 40);
            case FROST_BOLT -> pictured(new Color(0x9ED0F0), new Color(0x1E4870), "fx/ice_shard", 20);
            case ICE_PRISON -> pictured(new Color(0x7FB8E8), new Color(0x183858), "fx/ice_burst_4", 28);
            case ARCANE_RAY -> pictured(new Color(0x9A8CF0), new Color(0x2A1E70), "fx/ice_grow_3", 34);
            case OVERLOAD -> framed(new Color(0xC08CF0), new Color(0x4A1E7A), (g, x, y) -> star(g, x, y, 17));
            case NOVA -> pictured(new Color(0xF0A060), new Color(0x2A3870), "fx/ice_burst_3", 30);
            case METEOR -> pictured(new Color(0xF08840), new Color(0x6A1E08), "fx/fire_projectile", 40);
            case COLLISION, TRAP_TRIGGER, POISON_TICK, BEES_TICK, ICE_TICK, BURN_TICK -> framed(new Color(0x777777), new Color(0x333333), (g, x, y) -> { });
        };
    }

    /** Icône dont le glyphe est une image (effets du Druide). */
    private BufferedImage pictured(Color light, Color dark, String image, double height) {
        BufferedImage pic = assets.image(image);
        return framed(light, dark, (g, x, y) -> picture(g, pic, x, y, height));
    }

    private static void picture(Graphics2D g, BufferedImage pic, double cx, double cy, double height) {
        double h = height;
        double w = h * pic.getWidth() / pic.getHeight();
        if (w > 42) {
            w = 42;
            h = w * pic.getHeight() / pic.getWidth();
        }
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(pic, (int) Math.round(cx - w / 2), (int) Math.round(cy - h / 2), (int) Math.round(w), (int) Math.round(h), null);
    }

    private interface Glyph {
        void draw(Graphics2D g, double cx, double cy);
    }

    private static BufferedImage framed(Color light, Color dark, Glyph glyph) {
        BufferedImage img = Assets.createImage(W, H);
        Graphics2D g = img.createGraphics();
        UiTheme.enableQuality(g);
        Path2D frame = octagon(2, 2, W - 4, H - 4, 11);
        g.setPaint(new GradientPaint(0, 0, light, 0, H, dark));
        g.fill(frame);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(255, 255, 255, 150));
        g.draw(frame);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0, 0, 0, 90));
        g.draw(octagon(5, 5, W - 10, H - 10, 9));
        glyph.draw(g, W / 2.0, H / 2.0);
        g.dispose();
        return img;
    }

    private static Path2D octagon(double x, double y, double w, double h, double c) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x + c, y);
        p.lineTo(x + w - c, y);
        p.lineTo(x + w, y + c);
        p.lineTo(x + w, y + h - c);
        p.lineTo(x + w - c, y + h);
        p.lineTo(x + c, y + h);
        p.lineTo(x, y + h - c);
        p.lineTo(x, y + c);
        p.closePath();
        return p;
    }

    /** Trait épais clair avec ombre portée, pour les glyphes au trait. */
    private static void strokeWithShadow(Graphics2D g, java.awt.Shape shape, float width) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 90));
        g.translate(1, 2);
        g.draw(shape);
        g.translate(-1, -2);
        g.setColor(GLYPH);
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
    }

    private static void sword(Graphics2D g, double cx, double cy) {
        g.rotate(Math.toRadians(-45), cx, cy);
        Path2D blade = new Path2D.Double();
        blade.moveTo(cx, cy - 19);
        blade.lineTo(cx + 4, cy - 13);
        blade.lineTo(cx + 4, cy + 6);
        blade.lineTo(cx - 4, cy + 6);
        blade.lineTo(cx - 4, cy - 13);
        blade.closePath();
        g.setPaint(new GradientPaint((float) cx - 4, 0, new Color(0xF2F5F8), (float) cx + 4, 0, new Color(0xA9B4BF)));
        g.fill(blade);
        g.setColor(new Color(0x3A2A1A));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(blade);
        g.setColor(new Color(0xC9A227));
        g.fillRoundRect((int) cx - 10, (int) cy + 6, 20, 4, 3, 3);
        g.setColor(new Color(0x5A3A1E));
        g.fillRoundRect((int) cx - 2, (int) cy + 10, 4, 9, 2, 2);
        g.rotate(Math.toRadians(45), cx, cy);
    }

    private static void star(Graphics2D g, double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double rr = i % 2 == 0 ? r : r * 0.42;
            double x = cx + Math.cos(a) * rr;
            double y = cy + Math.sin(a) * rr;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        g.setColor(new Color(0, 0, 0, 80));
        g.translate(1, 1.5);
        g.fill(p);
        g.translate(-1, -1.5);
        g.setColor(new Color(0xFFE88A));
        g.fill(p);
    }

    private static void chevrons(Graphics2D g, double cx, double cy) {
        for (int i = 0; i < 2; i++) {
            double ox = cx - 9 + i * 12;
            Path2D p = new Path2D.Double();
            p.moveTo(ox - 5, cy - 11);
            p.lineTo(ox + 5, cy);
            p.lineTo(ox - 5, cy + 11);
            strokeWithShadow(g, p, 5f);
        }
    }

    private static void pushGlyph(Graphics2D g, double cx, double cy) {
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(cx - 14, cy);
        arrow.lineTo(cx + 6, cy);
        arrow.moveTo(cx - 2, cy - 8);
        arrow.lineTo(cx + 6, cy);
        arrow.lineTo(cx - 2, cy + 8);
        strokeWithShadow(g, arrow, 4.5f);
        Path2D wall = new Path2D.Double();
        wall.moveTo(cx + 14, cy - 13);
        wall.lineTo(cx + 14, cy + 13);
        strokeWithShadow(g, wall, 4f);
    }

    private static void hook(Graphics2D g, double cx, double cy) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx + 2, cy - 16);
        p.lineTo(cx + 2, cy + 4);
        p.append(new Arc2D.Double(cx - 12, cy - 4, 14, 16, 0, -180, Arc2D.OPEN), true);
        p.lineTo(cx - 9, cy - 1);
        strokeWithShadow(g, p, 4f);
        for (int i = 0; i < 3; i++) {
            g.setColor(GLYPH);
            g.draw(new Ellipse2D.Double(cx + 8, cy - 18 + i * 7, 5, 6));
        }
    }

    private static void plus(Graphics2D g, double cx, double cy) {
        g.setColor(new Color(0, 60, 20, 110));
        g.fillRoundRect((int) cx - 5, (int) cy - 14, 12, 30, 5, 5);
        g.fillRoundRect((int) cx - 14, (int) cy - 5, 30, 12, 5, 5);
        g.setColor(Color.WHITE);
        g.fillRoundRect((int) cx - 6, (int) cy - 15, 12, 30, 5, 5);
        g.fillRoundRect((int) cx - 15, (int) cy - 6, 30, 12, 5, 5);
    }

    private static void spiral(Graphics2D g, double cx, double cy) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i <= 60; i++) {
            double a = i * 0.32;
            double r = 2 + i * 0.26;
            double x = cx + Math.cos(a) * r;
            double y = cy + Math.sin(a) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        strokeWithShadow(g, p, 3.2f);
    }

    private static void drop(Graphics2D g, double cx, double cy) {
        Path2D d = new Path2D.Double();
        d.moveTo(cx, cy - 16);
        d.curveTo(cx + 13, cy, cx + 11, cy + 13, cx, cy + 13);
        d.curveTo(cx - 11, cy + 13, cx - 13, cy, cx, cy - 16);
        g.setColor(new Color(0, 0, 0, 90));
        g.translate(1, 2);
        g.fill(d);
        g.translate(-1, -2);
        g.setColor(GLYPH);
        g.fill(d);
        g.setColor(new Color(0x2E5A14));
        g.fill(new Ellipse2D.Double(cx - 6, cy + 1, 4, 4));
        g.fill(new Ellipse2D.Double(cx + 2, cy + 4, 3, 3));
    }

    private static void recoil(Graphics2D g, double cx, double cy) {
        Path2D shaft = new Path2D.Double();
        shaft.moveTo(cx - 14, cy + 6);
        shaft.lineTo(cx + 10, cy - 6);
        shaft.moveTo(cx + 3, cy - 8);
        shaft.lineTo(cx + 10, cy - 6);
        shaft.lineTo(cx + 6, cy + 1);
        strokeWithShadow(g, shaft, 3.6f);
        Path2D back = new Path2D.Double();
        back.moveTo(cx + 2, cy + 8);
        back.lineTo(cx + 9, cy + 14);
        back.lineTo(cx + 16, cy + 8);
        strokeWithShadow(g, back, 3f);
    }

    private static void trap(Graphics2D g, double cx, double cy) {
        Path2D teeth = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            double ix = cx + Math.cos(a) * 9;
            double iy = cy + Math.sin(a) * 9;
            double ox = cx + Math.cos(a + Math.PI / 10) * 16;
            double oy = cy + Math.sin(a + Math.PI / 10) * 16;
            if (i == 0) teeth.moveTo(ix, iy); else teeth.lineTo(ix, iy);
            teeth.lineTo(ox, oy);
        }
        teeth.closePath();
        strokeWithShadow(g, teeth, 2.4f);
        g.setColor(GLYPH);
        g.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
    }

    private static void rain(Graphics2D g, double cx, double cy) {
        for (int i = -1; i <= 1; i++) {
            double x = cx + i * 10;
            double y = cy + (i == 0 ? -4 : 2);
            Path2D a = new Path2D.Double();
            a.moveTo(x - 4, y - 12);
            a.lineTo(x + 3, y + 9);
            a.moveTo(x - 2, y + 6);
            a.lineTo(x + 3, y + 9);
            a.lineTo(x + 5, y + 4);
            strokeWithShadow(g, a, 3f);
        }
    }

    private static void leap(Graphics2D g, double cx, double cy) {
        Path2D arc = new Path2D.Double();
        arc.moveTo(cx - 15, cy + 10);
        arc.quadTo(cx, cy - 26, cx + 13, cy + 6);
        arc.moveTo(cx + 6, cy + 2);
        arc.lineTo(cx + 13, cy + 7);
        arc.lineTo(cx + 15, cy - 1);
        strokeWithShadow(g, arc, 3.8f);
        g.setColor(GLYPH);
        g.fillRoundRect((int) cx - 18, (int) cy + 12, 36, 3, 2, 2);
    }
}
