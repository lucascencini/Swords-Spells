package com.minidofus.ui;

import com.minidofus.engine.Assets;
import com.minidofus.model.CastStyle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Icônes des actions. Celles fournies par le pack (bottes, feu, bouclier, flèche) sont utilisées
 * telles quelles ; les autres sont dessinées dans le même style (cadre octogonal dégradé).
 */
public final class Icons {

    private static final int W = 52;
    private static final int H = 58;

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
            case ARCANE_BOLT -> assets.image("ui/icon_strike");
            case MELEE -> framed(new Color(0x9AA3AD), new Color(0x4A525C), Icons::sword);
            case DASH -> framed(new Color(0xE8963A), new Color(0x8A4A14), Icons::chevrons);
            case HEAL -> framed(new Color(0x48C774), new Color(0x1E6B3A), Icons::plus);
        };
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

    private static void chevrons(Graphics2D g, double cx, double cy) {
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 2; i++) {
            double ox = cx - 9 + i * 12;
            Path2D p = new Path2D.Double();
            p.moveTo(ox - 5, cy - 11);
            p.lineTo(ox + 5, cy);
            p.lineTo(ox - 5, cy + 11);
            g.setColor(new Color(60, 25, 0, 120));
            g.translate(1, 2);
            g.draw(p);
            g.translate(-1, -2);
            g.setColor(new Color(0xFFF4DC));
            g.draw(p);
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
}
