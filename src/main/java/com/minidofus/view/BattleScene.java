package com.minidofus.view;

import com.minidofus.engine.Assets;
import com.minidofus.model.Board;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Terrain;
import com.minidofus.model.Trap;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.Biome;
import com.minidofus.model.map.DecorKind;
import com.minidofus.model.map.DecorPlacement;
import com.minidofus.ui.UiTheme;
import com.minidofus.view.fx.EffectLayer;

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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Rendu du monde. Le sol (tuiles, quadrillage, fleurs) est peint une fois pour toutes dans une image ;
 * à chaque image il ne reste qu'à trier et dessiner quelques dizaines de sprites.
 */
public final class BattleScene {


    private final Assets assets;
    private final PoseTable poses;
    private final Arena arena;
    private final Map<Fighter, FighterView> views = new LinkedHashMap<>();
    private final EffectLayer effects = new EffectLayer();
    private final BackgroundPainter background = new BackgroundPainter();
    private final AmbientParticles particles;
    private final List<Point2D.Double> lights = new ArrayList<>();
    private final List<DecorSprite> sprites = new ArrayList<>();
    private final List<Trap> traps = new ArrayList<>();
    private final List<Object> drawOrder = new ArrayList<>();
    private BufferedImage ground;
    /** Illustration de fond (cartes illustrées), placée dans le repère du monde ; {@code null} sinon. */
    private BufferedImage backdrop;
    private Rectangle2D backdropBounds;
    private Color backdropFill;
    private SkyLayer sky;
    /** Grille → monde : isométrie, ou perspective calée sur le plateau d'une carte illustrée. */
    private Projection projection = Projection.ISO;
    /** Ombres portées : décalage au sol par unité de hauteur (direction opposée au soleil) et opacité. */
    private double sunX = 0.45;
    private double sunY = 0.25;
    private float shadowAlpha = 0.28f;
    private final Map<BufferedImage, Map<String, CastShadow>> shadowCache = new IdentityHashMap<>();
    private ForegroundLayer foreground;
    private double skyBottomY;
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

        final double feetX;
        final double feetY;

        DecorSprite(BufferedImage image, double x, double y, double w, double h, double depth, Cell cell,
                    double feetX, double feetY) {
            this.feetX = feetX;
            this.feetY = feetY;
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
        this.particles = new AmbientParticles(arena.theme().biome());
        if (arena.theme().biome() == Biome.DUNGEON) shadowAlpha = 0;   // éclairage de torches : pas de soleil
        loadBackdrop();
        for (FighterView v : views.values()) v.setProjection(projection);
        buildSprites();
        buildGround();
    }

    public FighterView view(Fighter f) { return views.get(f); }
    public Iterable<FighterView> views() { return views.values(); }
    public EffectLayer effects() { return effects; }
    public Projection projection() { return projection; }
    public Rectangle2D arenaBounds() { return groundBounds; }
    /** Sol précalculé de l'arène (aperçu de carte). */
    public BufferedImage groundImage() { return ground; }

    // ------------------------------------------------------------------ construction des caches

    /**
     * Charge l'illustration de la carte et la place pour que sa zone {@code centerX/centerY} tombe au centre
     * de la grille, à l'échelle {@code scale} (pixels d'image par unité du monde).
     */
    private void loadBackdrop() {
        String name = arena.theme().backdrop();
        if (name == null) return;
        backdrop = assets.image("backdrops/" + name);
        Properties p = assets.properties("backdrops/" + name + ".properties");
        double cx = Double.parseDouble(p.getProperty("centerX", "0"));
        double cy = Double.parseDouble(p.getProperty("centerY", "0"));
        double scale = Double.parseDouble(p.getProperty("scale", "1"));
        int n = arena.board().size();
        double gridCenterY = Iso.worldY(n - 1, n - 1) / 2.0;
        backdropBounds = new Rectangle2D.Double(-cx / scale, gridCenterY - cy / scale,
                backdrop.getWidth() / scale, backdrop.getHeight() / scale);
        backdropFill = averageEdgeColor(backdrop);
        // perspective : coins extérieurs de la grille sur l'illustration (pixels de l'image)
        if (p.getProperty("grid.top") != null) {
            projection = Projection.fromCorners(imagePoint(p, "grid.top", scale), imagePoint(p, "grid.right", scale),
                    imagePoint(p, "grid.bottom", scale), imagePoint(p, "grid.left", scale), n);
        }
        String shadow = p.getProperty("shadow");
        if (shadow != null) {
            String[] v = shadow.split(",");
            sunX = Double.parseDouble(v[0].trim());
            sunY = Double.parseDouble(v[1].trim());
            shadowAlpha = Float.parseFloat(v[2].trim());
        }
        String fg = p.getProperty("foreground");
        if (fg != null) {
            foreground = new ForegroundLayer(assets, fg.trim(), Boolean.parseBoolean(p.getProperty("foreground.dust", "false").trim()),
                    Boolean.parseBoolean(p.getProperty("foreground.sway", "false").trim()));
        }
        String skyName = p.getProperty("sky");
        if (skyName != null) {
            sky = new SkyLayer(assets, skyName.trim(), Integer.parseInt(p.getProperty("skyFrames", "1").trim()));
            skyBottomY = backdropBounds.getY() + Double.parseDouble(p.getProperty("skyBottom", "0")) / scale;
            backdropFill = sky.topColor();
        }
    }

    /** Point « x, y » en pixels de l'illustration, converti en coordonnées du monde. */
    private Point2D.Double imagePoint(Properties p, String key, double scale) {
        String[] xy = p.getProperty(key).split(",");
        return new Point2D.Double(backdropBounds.getX() + Double.parseDouble(xy[0].trim()) / scale,
                backdropBounds.getY() + Double.parseDouble(xy[1].trim()) / scale);
    }

    private static Color averageEdgeColor(BufferedImage img) {
        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        for (int x = 0; x < img.getWidth(); x += 8) {
            for (int y : new int[] {0, img.getHeight() - 1}) {
                int c = img.getRGB(x, y);
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
                count++;
            }
        }
        return new Color((int) (r / count), (int) (g / count), (int) (b / count));
    }

    private void buildGround() {
        Board board = arena.board();
        int n = board.size();
        Rectangle2D grid = projection.outline(n).getBounds2D();
        double margin = shadowAlpha > 0 ? 60 : 2;                    // place pour les ombres des obstacles du bord
        double minX = grid.getMinX() - margin;
        double maxX = grid.getMaxX() + margin;
        double minY = grid.getMinY() - 4;
        double maxY = grid.getMaxY() + 24 + (shadowAlpha > 0 ? margin : 0);
        groundBounds = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);

        ground = Assets.createImage((int) Math.ceil(groundBounds.getWidth()), (int) Math.ceil(groundBounds.getHeight()));
        Graphics2D g = ground.createGraphics();
        UiTheme.enableQuality(g);
        g.translate(-minX, -minY);

        for (int sum = 0; sum <= 2 * (n - 1); sum++) {           // de l'arrière vers l'avant
            for (int x = Math.max(0, sum - n + 1); x <= Math.min(n - 1, sum); x++) {
                int y = sum - x;
                // carte illustrée : le sol est l'illustration, seules les cases spéciales ont une tuile
                if (backdrop != null && board.terrain(x, y) == Terrain.GRASS) continue;
                String tile = tileFor(board.terrain(x, y), board.variant(x, y));
                BufferedImage img = assets.image(tile);
                double ts = projection.scaleAt(x, y);
                g.drawImage(img, (int) Math.round(projection.x(x, y) - Iso.TILE_ANCHOR_X * ts),
                        (int) Math.round(projection.y(x, y) - Iso.TILE_ANCHOR_Y * ts),
                        (int) Math.round(img.getWidth() * ts), (int) Math.round(img.getHeight() * ts), null);
            }
        }

        g.setStroke(new BasicStroke(1f));
        boolean darkGround = arena.theme().biome() == Biome.DUNGEON;
        boolean sand = arena.theme().biome() == Biome.DESERT;
        g.setColor(darkGround ? new Color(255, 230, 200, 26)
                : backdrop != null ? (sand ? new Color(110, 55, 15, 62) : new Color(25, 45, 10, 70))
                : new Color(20, 40, 10, 38));
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                if (board.terrain(x, y) != Terrain.WATER) g.draw(projection.cell(x, y, 0));
            }
        }

        if (shadowAlpha > 0) {
            for (DecorSprite d : sprites) drawShadow(g, d.image, d.x, d.y, d.w, d.h, d.feetX, d.feetY, shadowAlpha);
        }
        if (backdrop != null) drawArenaOutline(g, projection.outline(n));
        for (DecorPlacement d : arena.decor()) {
            if (d.kind().role() == DecorKind.Role.FLOWER) drawDecor(g, d);
        }
        g.dispose();
    }

    /** Contour de l'aire de jeu, utile quand le sol illustré déborde de la grille. */
    private static void drawArenaOutline(Graphics2D g, Path2D outline) {
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(20, 35, 10, 70));
        g.draw(outline);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 240, 190, 120));
        g.draw(outline);
        g.setStroke(new BasicStroke(1f));
    }

    /** Tuile à utiliser pour une case, selon le biome de la carte. */
    private String tileFor(Terrain terrain, int variant) {
        String prefix;
        String stone;
        String water;
        switch (arena.theme().biome()) {
            case SNOW -> { prefix = "snow"; stone = "snow_stone"; water = "ice"; }
            case DESERT -> { prefix = "sand"; stone = "sand_stone"; water = "water"; }
            case DUNGEON -> { prefix = "dungeon"; stone = "dungeon_stone"; water = "dungeon_water"; }
            default -> { prefix = "grass"; stone = "stone"; water = "water"; }
        }
        return switch (terrain) {
            case WATER -> "tiles/" + water;
            case ICE -> "tiles/ice";
            case STONE -> "tiles/" + stone;
            case GRASS -> "tiles/" + prefix + "_" + (variant % 6 + 1);
        };
    }

    private void buildSprites() {
        for (DecorPlacement d : arena.decor()) {
            if (d.kind().role() == DecorKind.Role.FLOWER) continue;
            BufferedImage img = d.mirrored() ? assets.mirrored("decor/" + d.kind().spriteKey())
                    : assets.image("decor/" + d.kind().spriteKey());
            double s = d.finalScale() * projection.scaleAt(d.x(), d.y());
            double w = img.getWidth() * s;
            double h = img.getHeight() * s;
            double anchorY = d.kind().role() == DecorKind.Role.TREE ? 0.93 : 0.80;
            double wx = projection.x(d.x(), d.y());
            double wy = projection.y(d.x(), d.y());
            Cell cell = d.kind().isBlocking() ? new Cell((int) Math.round(d.x()), (int) Math.round(d.y())) : null;
            sprites.add(new DecorSprite(img, wx - w / 2, wy - h * anchorY, w, h, d.x() + d.y(), cell, wx, wy));
            if (d.kind().isLight()) lights.add(new Point2D.Double(wx, wy - h * anchorY + 10));
        }
        sprites.sort(Comparator.comparingDouble(o -> o.depth));
    }

    private void drawDecor(Graphics2D g, DecorPlacement d) {
        BufferedImage img = d.mirrored() ? assets.mirrored("decor/" + d.kind().spriteKey())
                : assets.image("decor/" + d.kind().spriteKey());
        double s = d.finalScale() * projection.scaleAt(d.x(), d.y());
        double w = img.getWidth() * s;
        double h = img.getHeight() * s;
        double wx = projection.x(d.x(), d.y());
        double wy = projection.y(d.x(), d.y());
        g.drawImage(img, (int) Math.round(wx - w / 2), (int) Math.round(wy - h * 0.75), (int) Math.round(w), (int) Math.round(h), null);
    }

    // ------------------------------------------------------------------ mise à jour et rendu

    /** Fait trembler l'obstacle de la case (coup reçu). */
    public void shakeObstacle(Cell cell) {
        for (DecorSprite s : sprites) if (cell.equals(s.cell)) s.shake = 1;
    }

    /** Retire l'obstacle détruit de la case. */
    public void removeObstacle(Cell cell) {
        if (sprites.removeIf(s -> cell.equals(s.cell)) && shadowAlpha > 0) buildGround();   // efface son ombre
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
        if (sky != null) sky.update(dt);
        if (foreground != null) foreground.update(dt);
        particles.update(dt);
        for (FighterView v : views.values()) v.update(dt);
        effects.update(dt);
    }

    public void draw(Graphics2D g, Camera camera, int width, int height, Highlights highlights, Fighter active) {
        if (backdrop != null) {
            g.setColor(backdropFill);
            g.fillRect(0, 0, width, height);
        } else {
            g.drawImage(background.get(width, height, arena.seed(), arena.theme().biome()), 0, 0, null);
        }

        AffineTransform screen = g.getTransform();
        g.transform(camera.transform());
        if (sky != null) sky.draw(g, backdropBounds.getCenterX(), skyBottomY, backdropBounds.getWidth());
        if (backdrop != null) {
            g.drawImage(backdrop, (int) Math.round(backdropBounds.getX()), (int) Math.round(backdropBounds.getY()),
                    (int) Math.round(backdropBounds.getWidth()), (int) Math.round(backdropBounds.getHeight()), null);
        }

        g.drawImage(ground, (int) Math.round(groundBounds.getX()), (int) Math.round(groundBounds.getY()), null);
        if (shadowAlpha > 0) {
            for (FighterView v : views.values()) {
                FighterView.Silhouette sil = v.silhouette(poses);
                if (sil == null) continue;
                AffineTransform t = scaledAtFeet(g, v);
                drawShadow(g, sil.image(), sil.x(), sil.y(), sil.w(), sil.h(), sil.feetX(), sil.feetY(),
                        (float) (shadowAlpha * sil.alpha()));
                g.setTransform(t);
            }
        }
        drawWaterShimmer(g);
        drawLights(g);
        drawHighlights(g, highlights);
        for (Trap t : traps) drawTrap(g, t);
        for (FighterView v : views.values()) {
            AffineTransform t = scaledAtFeet(g, v);
            v.drawGroundMarks(g, v.fighter() == active);
            g.setTransform(t);
        }

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
                FighterView v = (FighterView) o;
                AffineTransform t = scaledAtFeet(g, v);
                v.draw(g, assets, poses);
                g.setTransform(t);
            }
        }

        effects.drawWorld(g);
        g.setTransform(screen);
        if (foreground != null) {
            int n = arena.board().size();
            Point2D.Double c = camera.toScreen(projection.x((n - 1) / 2.0, (n - 1) / 2.0), projection.y((n - 1) / 2.0, (n - 1) / 2.0));
            foreground.draw(g, width, height, c.x - width / 2.0, c.y - height / 2.0, camera.zoom() / camera.fitZoom());
        }
        particles.draw(g, width, height);
        effects.drawScreen(g, camera);
        drawPathLabel(g, camera, highlights);
    }

    /**
     * Ombre portée d'un sprite posé au sol en ({@code fx}, {@code fy}) : sa silhouette floue, couchée dans la
     * direction opposée au soleil (chaque point de hauteur h est décalé de h × (sunX, sunY)). L'ombre couchée
     * est calculée une fois par image et par point d'appui, puis simplement copiée à l'échelle.
     */
    private void drawShadow(Graphics2D g, BufferedImage img, double x, double y, double w, double h,
                            double fx, double fy, float alpha) {
        if (alpha <= 0.01) return;
        double k = w / img.getWidth();
        double feetU = (fx - x) / k;
        double feetV = (fy - y) / k;
        String key = Math.round(feetU) + ":" + Math.round(feetV);
        CastShadow cs = shadowCache.computeIfAbsent(img, i -> new LinkedHashMap<>())
                .computeIfAbsent(key, kk -> castShadow(img, feetU, feetV));
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
        g.drawImage(cs.image, (int) Math.round(fx + cs.offsetX * k), (int) Math.round(fy + cs.offsetY * k),
                (int) Math.round(cs.image.getWidth() * k), (int) Math.round(cs.image.getHeight() * k), null);
        g.setComposite(saved);
    }

    private static final int SHADOW_PAD = 4;

    /** Ombre couchée prête à copier, et position de son coin par rapport au point d'appui (pixels source). */
    private record CastShadow(BufferedImage image, double offsetX, double offsetY) { }

    private CastShadow castShadow(BufferedImage img, double feetU, double feetV) {
        BufferedImage sil = silhouetteOf(img);
        // pixel (u, v) de la silhouette → (u - sunX (v - fv), -sunY (v - fv)) autour des pieds
        AffineTransform t = new AffineTransform(1, 0, -sunX, -sunY, 0, 0);
        t.translate(-SHADOW_PAD - feetU, -SHADOW_PAD - feetV);
        Rectangle2D b = t.createTransformedShape(new Rectangle2D.Double(0, 0, sil.getWidth(), sil.getHeight())).getBounds2D();
        BufferedImage out = Assets.createImage((int) Math.ceil(b.getWidth()) + 2, (int) Math.ceil(b.getHeight()) + 2);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform place = AffineTransform.getTranslateInstance(-b.getX() + 1, -b.getY() + 1);
        place.concatenate(t);
        g.drawImage(sil, place, null);
        g.dispose();
        return new CastShadow(out, b.getX() - 1, b.getY() - 1);
    }

    /** Silhouette sombre floutée (bords doux) d'une image, avec une marge pour le flou. */
    private static BufferedImage silhouetteOf(BufferedImage img) {
        int w = img.getWidth() + 2 * SHADOW_PAD;
        int h = img.getHeight() + 2 * SHADOW_PAD;
        BufferedImage flat = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int a = (img.getRGB(x, y) >>> 24);
                if (a > 0) flat.setRGB(x + SHADOW_PAD, y + SHADOW_PAD, (a << 24) | 0x140C06);
            }
        }
        float[] kernel = new float[25];
        double[] g1 = {1, 4, 6, 4, 1};
        for (int i = 0; i < 25; i++) kernel[i] = (float) (g1[i / 5] * g1[i % 5] / 256.0);
        return new java.awt.image.ConvolveOp(new java.awt.image.Kernel(5, 5, kernel),
                java.awt.image.ConvolveOp.EDGE_ZERO_FILL, null).filter(flat, null);
    }

    /** Réduit le dessin d'un combattant selon la profondeur (perspective) ; renvoie la transformation d'avant. */
    private static AffineTransform scaledAtFeet(Graphics2D g, FighterView v) {
        AffineTransform before = g.getTransform();
        double s = v.depthScale();
        if (s != 1) {
            Point2D.Double feet = v.worldFeet();
            g.translate(feet.x, feet.y);
            g.scale(s, s);
            g.translate(-feet.x, -feet.y);
        }
        return before;
    }

    /** Halo vacillant des torches, posé sur le sol. */
    private void drawLights(Graphics2D g) {
        for (int i = 0; i < lights.size(); i++) {
            Point2D.Double p = lights.get(i);
            double flicker = 0.85 + 0.15 * Math.sin(waterClock * 7 + i * 1.7) * Math.sin(waterClock * 3.1 + i);
            float r = (float) (150 * flicker);
            g.setPaint(new RadialGradientPaint((float) p.x, (float) p.y + 30, r, new float[] {0f, 0.4f, 1f},
                    new Color[] {new Color(255, 170, 80, (int) (95 * flicker)), new Color(255, 130, 50, (int) (40 * flicker)),
                            new Color(255, 110, 40, 0)}));
            g.fill(new Ellipse2D.Double(p.x - r, p.y + 30 - r * 0.6, r * 2, r * 1.2));
        }
    }

    /** Léger miroitement sur l'eau, sans recalculer le sol. */
    private void drawWaterShimmer(Graphics2D g) {
        Board board = arena.board();
        Composite saved = g.getComposite();
        for (int x = 0; x < board.size(); x++) {
            for (int y = 0; y < board.size(); y++) {
                Terrain t = board.terrain(x, y);
                if (t != Terrain.WATER && t != Terrain.ICE) continue;
                double speed = t == Terrain.ICE ? 0.6 : 1.8;
                double a = 0.05 + 0.05 * Math.sin(waterClock * speed + x * 0.9 + y * 1.3);
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
        double cx = projection.x(trap.cell().x(), trap.cell().y());
        double cy = projection.y(trap.cell().x(), trap.cell().y());
        AffineTransform before = g.getTransform();
        double ts = projection.scaleAt(trap.cell().x(), trap.cell().y());
        g.translate(cx, cy);
        g.scale(ts, ts);
        g.translate(-cx, -cy);
        Color team = UiTheme.teamColor(trap.owner().team());
        double pulse = 0.5 + 0.5 * Math.sin(waterClock * 3);
        g.setColor(new Color(30, 20, 10, 150));
        g.fill(new Ellipse2D.Double(cx - 15, cy - 8, 30, 16));
        g.setStroke(new BasicStroke(2f));
        g.setColor(UiTheme.withAlpha(team, (int) (160 + 80 * pulse)));
        g.draw(new Ellipse2D.Double(cx - 15, cy - 8, 30, 16));
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
        g.setTransform(before);
    }

    private void drawPathLabel(Graphics2D g, Camera camera, Highlights h) {
        if (h.label() == null || h.labelCell() == null) return;
        Point2D.Double p = camera.toScreen(projection.x(h.labelCell().x(), h.labelCell().y()),
                projection.y(h.labelCell().x(), h.labelCell().y()));
        UiTheme.drawOutlinedCentered(g, h.label(), UiTheme.BODY_BOLD.deriveFont(14f), p.x, p.y + 5,
                h.labelColor(), new Color(0, 0, 0, 200));
    }

    /** Losange d'une case, rétréci de {@code inset} pixels. */
    public Path2D diamond(int x, int y, double inset) {
        return projection.cell(x, y, inset);
    }

    /** Case de la grille sous un point du monde (ou {@code null} hors de l'arène). */
    public Cell cellAt(double wx, double wy) {
        Point2D.Double gp = projection.toGrid(wx, wy);
        Cell c = new Cell((int) Math.round(gp.x), (int) Math.round(gp.y));
        return arena.board().inBounds(c) ? c : null;
    }
}
