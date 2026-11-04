package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.Sfx;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Cell;
import com.minidofus.model.FighterClass;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;
import com.minidofus.ui.Hud;
import com.minidofus.ui.HudAction;
import com.minidofus.ui.UiTheme;
import com.minidofus.ui.menu.MenuButton;
import com.minidofus.ui.menu.Screen;
import com.minidofus.ui.menu.SetupScreen;
import com.minidofus.ui.menu.TitleScreen;
import com.minidofus.view.BattleScene;
import com.minidofus.view.Camera;
import com.minidofus.view.PoseTable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Composant Swing qui héberge le jeu : boucle à 60 images/s, rendu et entrées. Trois états :
 * un écran de menu (titre, configuration), un combat, ou un combat en pause. Toute la logique vit
 * ailleurs ; cette classe aiguille.
 */
@SuppressWarnings("serial")
public final class GameView extends JComponent {

    private static final int HUD_TOP = 132;
    private static final int HUD_BOTTOM = 150;

    private final Assets assets = new Assets();
    private final PoseTable poses = new PoseTable(assets);
    private final SoundPlayer sound = new SoundPlayer();
    private final SoundPlayer silent = new SoundPlayer();
    private final Hud hud = new Hud(assets);
    private final Hud demoHud = new Hud(assets);
    private final Camera camera = new Camera();
    private final Camera demoCamera = new Camera();
    private final Random random = new Random();
    private final Timer timer;
    private GameSession session;
    private long lastFrame;
    private boolean showFps;
    private double fpsAccumulator;
    private int fpsFrames;

    // menus et pause
    private final TitleScreen title;
    private final SetupScreen setupScreen;
    private Screen menu;
    private GameSession demo;
    private double demoEndTimer;
    private boolean paused;
    private final List<MenuButton> pauseButtons;
    private int mouseX = -1;
    private int mouseY = -1;

    // glisser-déposer de la caméra
    private Point2D.Double dragStart;
    private Point2D.Double dragLast;
    private boolean dragging;

    /**
     * @param setup partie à lancer immédiatement, ou {@code null} pour ouvrir l'écran titre
     * @param quit  action pour quitter l'application
     */
    public GameView(MatchSetup setup, Runnable quit) {
        setFocusable(true);
        setDoubleBuffered(true);
        silent.toggleMute();
        sound.preload(Sfx.allFiles());
        title = new TitleScreen(this::drawDemo, () -> openSetup(true), () -> openSetup(false), quit);
        setupScreen = new SetupScreen(assets, this::mapPreview, this::startFromSetup, this::openTitle);
        setupScreen.setBackground(this::drawDemo);
        pauseButtons = List.of(
                new MenuButton("Reprendre", null, true, () -> paused = false),
                new MenuButton("Recommencer", "Même carte, mêmes équipes", false, () -> newSession(session.setup())),
                new MenuButton("Menu principal", null, false, this::openTitle));
        if (setup != null) newSession(setup);
        else openTitle();
        timer = new Timer(1000 / 60, e -> tick());
        timer.setCoalesce(true);
        installMouse();
        installKeys();
        addComponentListener(new ComponentAdapter() {
            private boolean fitted;

            @Override
            public void componentResized(ComponentEvent e) {
                layoutAll();
                if (!fitted) {
                    fitCameras();
                    fitted = true;
                } else {
                    camera.resize(getWidth(), getHeight());
                    demoCamera.resize(getWidth(), getHeight());
                }
            }
        });
    }

    public void start() {
        lastFrame = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    public void stop() {
        timer.stop();
        sound.close();
        silent.close();
    }

    /** Accès pour les outils de capture d'écran et de test. */
    public GameSession session() {
        return session;
    }

    /** Dimensionne la vue sans fenêtre (outils de test et de capture). */
    public void layoutOffscreen(int w, int h) {
        setSize(w, h);
        layoutAll();
        fitCameras();
    }

    /** Graine aléatoire courte (facile à noter pour rejouer une carte). */
    public static long randomSeed(Random random) {
        return random.nextLong() & 0xFFFFFFL;
    }

    private void layoutAll() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0) return;
        if (session != null) hud.layout(w, h, session.spellCount());
        if (demo != null) demoHud.layout(w, h, demo.spellCount());
        title.layout(w, h);
        setupScreen.layout(w, h);
        int bw = 300;
        int y = h / 2 - 70;
        for (MenuButton b : pauseButtons) {
            b.setBounds((w - bw) / 2, y, bw, 54);
            y += 66;
        }
    }

    private void fitCameras() {
        if (getWidth() <= 0) return;
        if (session != null) camera.fit(session.scene().arenaBounds(), getWidth(), getHeight(), HUD_TOP, HUD_BOTTOM);
        if (demo != null) demoCamera.fit(demo.scene().arenaBounds(), getWidth(), getHeight(), 60, 60);
    }

    // ------------------------------------------------------------------ navigation

    private void openTitle() {
        paused = false;
        session = null;
        menu = title;
        startDemo();
    }

    private void openSetup(boolean versusComputer) {
        setupScreen.setVersusComputer(versusComputer);
        menu = setupScreen;
    }

    private void startFromSetup(SetupScreen.Result r) {
        MapTheme theme = r.theme() != null ? r.theme() : randomTheme();
        newSession(new MatchSetup(theme, randomSeed(random), r.red(), r.blue(), null, r.difficulty()));
    }

    private void newSession(MatchSetup setup) {
        menu = null;
        paused = false;
        demo = null;
        session = new GameSession(setup, assets, poses, sound, hud, p -> camera.focus(p.x, p.y, HUD_TOP, HUD_BOTTOM));
        sound.loop(ambienceOf(setup.theme()), 0.32);
        layoutAll();
        fitCameras();
    }

    /** Partie de démonstration, ordinateur contre ordinateur, en fond des menus. */
    private void startDemo() {
        List<FighterClass> all = FighterCatalog.ALL;
        List<FighterClass> red = List.of(all.get(random.nextInt(3)), all.get(random.nextInt(3)));
        List<FighterClass> blue = List.of(all.get(random.nextInt(3)), all.get(random.nextInt(3)));
        MapTheme theme = randomTheme();
        demo = new GameSession(new MatchSetup(theme, randomSeed(random), red, blue, Difficulty.NORMAL, Difficulty.NORMAL),
                assets, poses, silent, demoHud, p -> demoCamera.focus(p.x, p.y, 60, 60));
        demoEndTimer = 0;
        sound.loop(ambienceOf(theme), 0.25);
        layoutAll();
        fitCameras();
    }

    private void drawDemo(Graphics2D g) {
        if (demo == null) {
            g.setColor(new Color(0x14100C));
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        demo.scene().draw(g, demoCamera, getWidth(), getHeight(), demo.highlights(), demo.isOver() ? null : demo.active());
    }

    /** Aperçu du terrain d'une carte (sol précalculé de la scène), pour l'écran de configuration. */
    private BufferedImage mapPreview(MapTheme theme) {
        return new BattleScene(assets, poses, MapGenerator.generate(theme, 3), List.of()).groundImage();
    }

    /** Ambiance sonore en boucle de chaque carte (fichiers générés par tools/ambience.py). */
    private static String ambienceOf(MapTheme theme) {
        return switch (theme) {
            case FOREST -> "amb_forest";
            case RUINS -> "amb_ruins";
            case LAKE -> "amb_lake";
            case SNOW -> "amb_snow";
            case DESERT -> "amb_desert";
            case DUNGEON -> "amb_dungeon";
        };
    }

    private MapTheme randomTheme() {
        MapTheme[] all = MapTheme.values();
        return all[random.nextInt(all.length)];
    }

    // ------------------------------------------------------------------ boucle

    /** Fait avancer le jeu de {@code dt} secondes (utilisé par la boucle et par les tests). */
    public void advance(double dt) {
        if (menu != null) {
            menu.update(dt);
            if (demo != null) {
                demoCamera.update(dt);
                demo.update(dt);
                demoHud.update(dt, demo);
                if (demo.isOver() && !demo.isBusy() && (demoEndTimer += dt) > 2.5) startDemo();
            }
            return;
        }
        if (paused || session == null) return;
        camera.update(dt);
        session.update(dt);
        hud.update(dt, session);
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastFrame) / 1e9); // pas de saut énorme après une pause
        lastFrame = now;
        advance(dt);
        if (showFps) {
            fpsAccumulator += dt;
            fpsFrames++;
            if (fpsAccumulator >= 0.5) {
                hud.setFps(fpsFrames / fpsAccumulator);
                fpsAccumulator = 0;
                fpsFrames = 0;
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            UiTheme.enableQuality(g);
            if (menu != null) {
                menu.draw(g);
                return;
            }
            session.scene().draw(g, camera, getWidth(), getHeight(), session.highlights(),
                    session.isOver() ? null : session.active());
            hud.draw(g, session);
            if (paused) drawPause(g);
        } finally {
            g.dispose();
        }
    }

    private void drawPause(Graphics2D g) {
        g.setColor(new Color(5, 4, 2, 170));
        g.fillRect(0, 0, getWidth(), getHeight());
        int w = 360;
        int h = 290;
        UiTheme.panel(g, (getWidth() - w) / 2.0, getHeight() / 2.0 - 140, w, h, 20, true);
        UiTheme.drawCentered(g, "Pause", UiTheme.TITLE.deriveFont(26f), getWidth() / 2.0, getHeight() / 2.0 - 98, UiTheme.GOLD_LIGHT);
        for (MenuButton b : pauseButtons) b.draw(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ souris

    private void installMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (menu != null) {
                    if (SwingUtilities.isLeftMouseButton(e)) menu.mousePressed(e.getX(), e.getY());
                    return;
                }
                if (paused) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        for (MenuButton b : pauseButtons) if (b.press(e.getX(), e.getY())) break;
                    }
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
                    dragStart = new Point2D.Double(e.getX(), e.getY());
                    dragLast = dragStart;
                    dragging = false;
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                HudAction action = hud.hit(e.getX(), e.getY(), session);
                if (action != null) {
                    perform(action);
                } else if (!hud.covers(e.getX(), e.getY(), session)) {
                    session.click(cellAt(e.getX(), e.getY()));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (session != null && menu == null && !paused && dragStart != null && !dragging
                        && SwingUtilities.isRightMouseButton(e)) {
                    session.cancel();
                }
                dragStart = null;
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null && menu == null && !paused) {
                    if (!dragging && dragStart.distance(e.getX(), e.getY()) > 4) dragging = true;
                    if (dragging) camera.pan(e.getX() - dragLast.x, e.getY() - dragLast.y);
                    dragLast = new Point2D.Double(e.getX(), e.getY());
                }
                mouseMoved(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (menu != null) {
                    menu.mouseMoved(e.getX(), e.getY());
                    return;
                }
                hud.setMouse(e.getX(), e.getY());
                boolean overHud = paused || hud.covers(e.getX(), e.getY(), session);
                session.hover(overHud ? null : cellAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hud.setMouse(-1, -1);
                if (session != null) session.hover(null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (menu == null && !paused) camera.zoomAt(e.getX(), e.getY(), Math.pow(1.12, -e.getPreciseWheelRotation()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private Cell cellAt(int sx, int sy) {
        Point2D.Double w = camera.toWorld(sx, sy);
        return session.scene().cellAt(w.x, w.y);
    }

    private void perform(HudAction action) {
        switch (action.type()) {
            case MOVE -> session.selectMove();
            case SPELL -> session.selectSpell(action.index());
            case END_TURN -> session.endTurn();
            case MUTE -> sound.toggleMute();
            case REPLAY -> newSession(session.setup());
            case NEW_MAP -> newSession(session.setup().withMap(randomTheme(), randomSeed(random)));
            case MENU -> openTitle();
        }
    }

    // ------------------------------------------------------------------ clavier

    private boolean inBattle() {
        return menu == null && !paused && session != null;
    }

    private void installKeys() {
        bind("ESCAPE", () -> {
            if (menu != null) menu.key("ESCAPE");
            else if (paused) paused = false;
            else if (session.isOver()) perform(HudAction.of(HudAction.Type.MENU));
            else if (session.selectedSpell() != null) session.cancel();
            else paused = true;
        });
        bind("ENTER", () -> {
            if (menu != null) menu.key("ENTER");
            else if (inBattle()) session.endTurn();
        });
        bind("LEFT", () -> {
            if (menu != null) menu.key("LEFT");
        });
        bind("RIGHT", () -> {
            if (menu != null) menu.key("RIGHT");
        });
        battleKey("SPACE", () -> session.endTurn());
        battleKey("D", () -> session.selectMove());
        for (int i = 1; i <= 5; i++) {
            int index = i - 1;
            battleKey(String.valueOf(i), () -> session.selectSpell(index));
            battleKey("NUMPAD" + i, () -> session.selectSpell(index));
        }
        battleKey("C", () -> camera.fit(session.scene().arenaBounds(), getWidth(), getHeight(), HUD_TOP, HUD_BOTTOM));
        bind("M", sound::toggleMute);
        bind("F3", () -> {
            showFps = !showFps;
            hud.setFps(0);
        });
        battleKey("R", () -> {
            if (session.isOver()) perform(HudAction.of(HudAction.Type.REPLAY));
        });
        battleKey("N", () -> {
            if (session.isOver()) perform(HudAction.of(HudAction.Type.NEW_MAP));
        });
    }

    private void battleKey(String key, Runnable action) {
        bind(key, () -> {
            if (inBattle()) action.run();
        });
    }

    private void bind(String key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }
}
