package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.Sfx;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Cell;
import com.minidofus.model.map.MapTheme;
import com.minidofus.ui.Hud;
import com.minidofus.ui.HudAction;
import com.minidofus.ui.UiTheme;
import com.minidofus.view.Camera;
import com.minidofus.view.PoseTable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * Composant Swing qui héberge la partie : boucle de jeu, rendu et entrées (souris, clavier).
 * Toute la logique de jeu vit ailleurs ; cette classe ne fait que relayer.
 */
@SuppressWarnings("serial")
public final class GameView extends JComponent {

    private static final int HUD_TOP = 132;
    private static final int HUD_BOTTOM = 150;

    private final Assets assets = new Assets();
    private final PoseTable poses = new PoseTable(assets);
    private final SoundPlayer sound = new SoundPlayer();
    private final Hud hud = new Hud(assets);
    private final Camera camera = new Camera();
    private final Random random = new Random();
    private final Timer timer;
    private GameSession session;
    private long lastFrame;
    private boolean showFps;
    private double fpsAccumulator;
    private int fpsFrames;

    // glisser-déposer de la caméra
    private Point2D.Double dragStart;
    private Point2D.Double dragLast;
    private boolean dragging;

    public GameView(MatchSetup setup) {
        setFocusable(true);
        setDoubleBuffered(true);
        sound.preload(Sfx.allFiles());
        newSession(setup);
        timer = new Timer(1000 / 60, e -> tick());
        timer.setCoalesce(true);
        installMouse();
        installKeys();
        addComponentListener(new ComponentAdapter() {
            private boolean fitted;

            @Override
            public void componentResized(ComponentEvent e) {
                hud.layout(getWidth(), getHeight(), session.spellCount());
                if (!fitted) {
                    fitCamera();
                    fitted = true;
                } else {
                    camera.resize(getWidth(), getHeight());
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
    }

    /** Accès pour les outils de capture d'écran et de test. */
    public GameSession session() {
        return session;
    }

    /** Dimensionne la vue sans fenêtre (outils de test et de capture). */
    public void layoutOffscreen(int w, int h) {
        setSize(w, h);
        hud.layout(w, h, session.spellCount());
        fitCamera();
    }

    // ------------------------------------------------------------------ parties

    /** Thème au hasard et graine aléatoire, pour une nouvelle carte. */
    public static long randomSeed(Random random) {
        return random.nextLong() & 0xFFFFFFL;
    }

    private void newSession(MatchSetup setup) {
        session = new GameSession(setup, assets, poses, sound, hud, p -> camera.focus(p.x, p.y, HUD_TOP, HUD_BOTTOM));
        sound.loop(ambienceOf(setup.theme()), 0.32);
        if (getWidth() > 0) {
            hud.layout(getWidth(), getHeight(), session.spellCount());
            fitCamera();
        }
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

    private void fitCamera() {
        camera.fit(session.scene().arenaBounds(), getWidth(), getHeight(), HUD_TOP, HUD_BOTTOM);
    }

    // ------------------------------------------------------------------ boucle

    /** Fait avancer le jeu de {@code dt} secondes (utilisé par la boucle et par les tests). */
    public void advance(double dt) {
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
            session.scene().draw(g, camera, getWidth(), getHeight(), session.highlights(),
                    session.isOver() ? null : session.active());
            hud.draw(g, session);
        } finally {
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ souris

    private void installMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
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
                if (dragStart != null && !dragging && SwingUtilities.isRightMouseButton(e)) session.cancel();
                dragStart = null;
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    if (!dragging && dragStart.distance(e.getX(), e.getY()) > 4) dragging = true;
                    if (dragging) camera.pan(e.getX() - dragLast.x, e.getY() - dragLast.y);
                    dragLast = new Point2D.Double(e.getX(), e.getY());
                }
                mouseMoved(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hud.setMouse(e.getX(), e.getY());
                boolean overHud = hud.covers(e.getX(), e.getY(), session);
                session.hover(overHud ? null : cellAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hud.setMouse(-1, -1);
                session.hover(null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                camera.zoomAt(e.getX(), e.getY(), Math.pow(1.12, -e.getPreciseWheelRotation()));
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
        }
    }

    // ------------------------------------------------------------------ clavier

    private void installKeys() {
        bind("SPACE", () -> session.endTurn());
        bind("ENTER", () -> session.endTurn());
        bind("ESCAPE", () -> session.cancel());
        bind("D", () -> session.selectMove());
        for (int i = 1; i <= 5; i++) {
            int index = i - 1;
            bind(String.valueOf(i), () -> session.selectSpell(index));
            bind("NUMPAD" + i, () -> session.selectSpell(index));
        }
        bind("C", this::fitCamera);
        bind("M", sound::toggleMute);
        bind("F3", () -> {
            showFps = !showFps;
            hud.setFps(0);
        });
        bind("R", () -> {
            if (session.isOver()) perform(HudAction.of(HudAction.Type.REPLAY));
        });
        bind("N", () -> {
            if (session.isOver()) perform(HudAction.of(HudAction.Type.NEW_MAP));
        });
        bind("ctrl N", () -> perform(HudAction.of(HudAction.Type.NEW_MAP)));
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
