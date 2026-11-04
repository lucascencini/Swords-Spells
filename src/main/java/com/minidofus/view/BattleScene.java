package com.minidofus.view;

import com.minidofus.engine.Assets;
import com.minidofus.model.Board;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Terrain;
import com.minidofus.model.Trap;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.DecorKind;
import com.minidofus.model.map.DecorPlacement;
import com.minidofus.ui.UiTheme;
import com.minidofus.view.fx.EffectLayer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rendu du monde. Le sol (tuiles, quadrillage, fleurs) est peint une fois pour toutes dans une image ;
 * à chaque image il ne reste qu'à trier et dessiner quelques dizaines de sprites.
 */
public final class BattleScene {

    private static final String[] GRASS = {"tiles/grass_1", "tiles/grass_2", "tiles/grass_3",
            "tiles/grass_4", "tiles/grass_5", "tiles/grass_6"};

    private final Assets assets;
    private final PoseTable poses;
    private final Arena arena;
    private final Map<Fighter, FighterView> views = new LinkedHashMap<>();
    private final EffectLayer effects = new EffectLayer();
    private final BackgroundPainter background = new BackgroundPainter();
    private final List<DecorSprite> sprites = new ArrayList<>();
    private final List<Trap> traps = new ArrayList<>();
    private final List<Object> drawOrder = new ArrayList<>();
    private BufferedImage ground;
    private Rectangle2D groundBounds;
    private double waterClock;

    /** Sprite de décor positionné dans le monde ; {@code cell} est renseigné pour les obstacles (destructibles ou non). */
    private static final class DecorSprite {
        final BufferedImage image;
        final double x;
        final double y;
        final double w;
        final double h;
        final double depth;
        final Cell cell;
        double shake;

        DecorSprite(BufferedImage image, double x, double y, double w, double h, double depth, Cell cell) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.depth = depth;
            this.cell = cell;
        }
    }

    public BattleScene(Assets assets, PoseTable poses, Arena arena, List<FighterView> fighterViews) {
        this.assets = assets;
        this.poses = poses;
        this.arena = arena;
        for (FighterView v : fighterViews) views.put(v.fighter(), v);
        buildGround();
        buildSprites();
    }

    public FighterView view(Fighter f) { return views.get(f); }
    public Iterable<FighterView> views() { return views.values(); }
    public EffectLayer effects() { return effects; }
    public Rectangle2D arenaBounds() { return groundBounds; }

    // ------------------------------------------------------------------ construction des caches

    private void buildGround() {
        Board board = arena.board();
        int n = board.size();
        double minX = Iso.worldX(0, n - 1) - Iso.TILE_ANCHOR_X;
        double maxX = Iso.worldX(n - 1, 0) + Iso.TILE_ANCHOR_X;
        double minY = Iso.worldY(0, 0) - Iso.TILE_ANCHOR_Y;
        double maxY = Iso.worldY(n - 1, n - 1) + 40;
        groundBounds = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);

        ground = Assets.createImage((int) Math.ceil(groundBounds.getWidth()), (int) Math.ceil(groundBounds.getHeight()));
        Graphics2D g = ground.createGraphics();
        UiTheme.enableQuality(g);
        g.translate(-minX, -minY);

        for (int sum = 0; sum <= 2 * (n - 1); sum++) {           // de l'arrière vers l'avant
            for (int x = Math.max(0, sum - n + 1); x <= Math.min(n - 1, sum); x++) {
                int y = sum - x;
                String tile = switch (board.terrain(x, y)) {
                    case WATER -> "tiles/water";
                    case STONE -> "tiles/stone";
                    default -> GRASS[board.variant(x, y) % GRASS.length];
                };
                BufferedImage img = assets.image(tile);
                g.drawImage(img, (int) Math.round(Iso.worldX(x, y) - Iso.TILE_ANCHOR_X),
                        (int) Math.round(Iso.worldY(x, y) - Iso.TILE_ANCHOR_Y), null);
            }
        }

        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(20, 40, 10, 38));
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                if (board.terrain(x, y) != Terrain.WATER) g.draw(diamond(x, y, 0));
            }
        }

        for (DecorPlacement d : arena.decor()) {
            if (d.kind().role() == DecorKind.Role.FLOWER) drawDecor(g, d);
        }
        g.dispose();
    }

    private void buildSprites() {
        for (DecorPlacement d : arena.decor()) {
            if (d.kind().role() == DecorKind.Role.FLOWER) continue;
            BufferedImage img = d.mirrored() ? assets.mirrored("decor/" + d.kind().spriteKey())
                    : assets.image("decor/" + d.kind().spriteKey());
            double s = d.finalScale();
            double w = img.getWidth() * s;
            double h = img.getHeight() * s;
            double anchorY = d.kind().role() == DecorKind.Role.TREE ? 0.93 : 0.80;
            double wx = Iso.worldX(d.x(), d.y());
            double wy = Iso.worldY(d.x(), d.y());
            Cell cell = d.kind().isBlocking() ? new Cell((int) Math.round(d.x()), (int) Math.round(d.y())) : null;
            sprites.add(new DecorSprite(img, wx - w / 2, wy - h * anchorY, w, h, d.x() + d.y(), cell));
        }
        sprites.sort(Comparator.comparingDouble(o -> o.depth));
    }

    private void drawDecor(Graphics2D g, DecorPlacement d) {
        BufferedImage img = d.mirrored() ? assets.mirrored("decor/" + d.kind().spriteKey())
                : assets.image("decor/" + d.kind().spriteKey());
        double s = d.finalScale();
        double w = img.getWidth() * s;
        double h = img.getHeight() * s;
        double wx = Iso.worldX(d.x(), d.y());
        double wy = Iso.worldY(d.x(), d.y());
        g.drawImage(img, (int) Math.round(wx - w / 2), (int) Math.round(wy - h * 0.75), (int) Math.round(w), (int) Math.round(h), null);
    }

    // ------------------------------------------------------------------ mise à jour et rendu

    /** Fait trembler l'obstacle de la case (coup reçu). */
    public void shakeObstacle(Cell cell) {
        for (DecorSprite s : sprites) if (cell.equals(s.cell)) s.shake = 1;
    }

    /** Retire l'obstacle détruit de la case. */
    public void removeObstacle(Cell cell) {
        sprites.removeIf(s -> cell.equals(s.cell));
    }

    public void addTrap(Trap trap) {
        traps.add(trap);
    }

    public void removeTrap(Trap trap) {
        traps.remove(trap);
    }

    public void update(double dt) {
        for (DecorSprite s : sprites) s.shake = Math.max(0, s.shake - dt * 4);
        waterClock += dt;
        for (FighterView v : views.values()) v.update(dt);
        effects.update(dt);
    }

    public void draw(Graphics2D g, Camera camera, int width, int height, Highlights highlights, Fighter active) {
        g.drawImage(background.get(width, height, arena.seed()), 0, 0, null);

        AffineTransform screen = g.getTransform();
        g.transform(camera.transform());

        g.drawImage(ground, (int) Math.round(groundBounds.getX()), (int) Math.round(groundBounds.getY()), null);
        drawWaterShimmer(g);
        drawHighlights(g, highlights);
        for (Trap t : traps) drawTrap(g, t);
        for (FighterView v : views.values()) v.drawGroundMarks(g, v.fighter() == active);

        // tri en profondeur : décor (déjà trié) fusionné avec les combattants
        drawOrder.clear();
        drawOrder.addAll(sprites);
        drawOrder.addAll(views.values());
        drawOrder.sort(Comparator.comparingDouble(o -> o instanceof DecorSprite s ? s.depth : ((FighterView) o).depth()));
        for (Object o : drawOrder) {
            if (o instanceof DecorSprite s) {
                double dx = s.shake > 0 ? Math.sin(s.shake * 40) * 3 * s.shake : 0;
                g.drawImage(s.image, (int) Math.round(s.x + dx), (int) Math.round(s.y),
                        (int) Math.round(s.w), (int) Math.round(s.h), null);
            } else {
                ((FighterView) o).draw(g, assets, poses);
            }
        }

        effects.drawWorld(g);
        g.setTransform(screen);
        effects.drawScreen(g, camera);
        drawPathLabel(g, camera, highlights);
    }

    /** Léger miroitement sur l'eau, sans recalculer le sol. */
    private void drawWaterShimmer(Graphics2D g) {
        Board board = arena.board();
        Composite saved = g.getComposite();
        for (int x = 0; x < board.size(); x++) {
            for (int y = 0; y < board.size(); y++) {
                if (board.terrain(x, y) != Terrain.WATER) continue;
                double a = 0.06 + 0.06 * Math.sin(waterClock * 1.8 + x * 0.9 + y * 1.3);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
                g.setColor(Color.WHITE);
                g.fill(diamond(x, y, 4));
            }
        }
        g.setComposite(saved);
    }

    private void drawHighlights(Graphics2D g, Highlights h) {
        for (Map.Entry<Cell, Highlights.Style> e : h.cells().entrySet()) {
            Highlights.Style s = e.getValue();
            Path2D shape = diamond(e.getKey().x(), e.getKey().y(), 3);
            g.setColor(UiTheme.withAlpha(s.color, (int) (255 * s.fill)));
            g.fill(shape);
            g.setStroke(new BasicStroke(2f));
            g.setColor(UiTheme.withAlpha(s.color, (int) (255 * s.border)));
            g.draw(shape);
        }
        Cell hover = h.hover();
        if (hover != null && arena.board().inBounds(hover)) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(255, 255, 255, 150));
            g.draw(diamond(hover.x(), hover.y(), 2));
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** Piège : cercle denté aux couleurs de l'équipe du poseur. */
    private void drawTrap(Graphics2D g, Trap trap) {
        double cx = Iso.worldX(trap.cell().x(), trap.cell().y());
        double cy = Iso.worldY(trap.cell().x(), trap.cell().y());
        Color team = UiTheme.teamColor(trap.owner().team());
        double pulse = 0.5 + 0.5 * Math.sin(waterClock * 3);
        g.setColor(new Color(30, 20, 10, 150));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - 15, cy - 8, 30, 16));
        g.setStroke(new BasicStroke(2f));
        g.setColor(UiTheme.withAlpha(team, (int) (160 + 80 * pulse)));
        g.draw(new java.awt.geom.Ellipse2D.Double(cx - 15, cy - 8, 30, 16));
        Path2D teeth = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double ix = cx + Math.cos(a) * 9;
            double iy = cy + Math.sin(a) * 4.8;
            double ox = cx + Math.cos(a + Math.PI / 8) * 14;
            double oy = cy + Math.sin(a + Math.PI / 8) * 7.5;
            if (i == 0) teeth.moveTo(ix, iy); else teeth.lineTo(ix, iy);
            teeth.lineTo(ox, oy);
        }
        teeth.closePath();
        g.setColor(new Color(200, 205, 215, 210));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(teeth);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawPathLabel(Graphics2D g, Camera camera, Highlights h) {
        if (h.label() == null || h.labelCell() == null) return;
        Point2D.Double p = camera.toScreen(Iso.worldX(h.labelCell().x(), h.labelCell().y()),
                Iso.worldY(h.labelCell().x(), h.labelCell().y()));
        UiTheme.drawOutlinedCentered(g, h.label(), UiTheme.BODY_BOLD.deriveFont(14f), p.x, p.y + 5,
                h.labelColor(), new Color(0, 0, 0, 200));
    }

    /** Losange d'une case, rétréci de {@code inset} pixels. */
    public static Path2D diamond(int x, int y, double inset) {
        double cx = Iso.worldX(x, y);
        double cy = Iso.worldY(x, y);
        double hw = Iso.HALF_W - inset;
        double hh = Iso.HALF_H - inset * Iso.HALF_H / Iso.HALF_W;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx, cy - hh);
        p.lineTo(cx + hw, cy);
        p.lineTo(cx, cy + hh);
        p.lineTo(cx - hw, cy);
        p.closePath();
        return p;
    }

    /** Case de la grille sous un point du monde (ou {@code null} hors de l'arène). */
    public Cell cellAt(double wx, double wy) {
        Point2D.Double gp = Iso.toGrid(wx, wy);
        Cell c = new Cell((int) Math.round(gp.x), (int) Math.round(gp.y));
        return arena.board().inBounds(c) ? c : null;
    }
}
