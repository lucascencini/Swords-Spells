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
import com.minidofus.ui.menu.TextField;
import com.minidofus.ui.menu.TitleScreen;
import com.minidofus.net.Protocol;
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
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
    private OnlineController online;
    private final TextField battleChat = new TextField("Message… (Entrée pour envoyer, Échap pour fermer)", Protocol.MAX_CHAT, false);
    private boolean chatOpen;
    private GameSession demo;
    private double demoEndTimer;
    private boolean paused;
    private final List<MenuButton> pauseButtons;
    private final List<MenuButton> onlinePauseButtons;
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
        title = new TitleScreen(this::drawDemo, () -> openSetup(true), () -> openSetup(false), this::openOnline, quit);
        setupScreen = new SetupScreen(assets, this::mapPreview, this::startFromSetup, this::openTitle);
        setupScreen.setBackground(this::drawDemo);
        pauseButtons = List.of(
                new MenuButton("Reprendre", null, true, () -> paused = false),
                new MenuButton("Recommencer", "Même carte, mêmes équipes", false, () -> newSession(session.setup())),
                new MenuButton("Menu principal", null, false, this::openTitle));
        onlinePauseButtons = List.of(
                new MenuButton("Reprendre", "La partie continue pendant la pause", true, () -> paused = false),
                new MenuButton("Quitter la partie", "L'ordinateur jouera vos personnages", false, () -> {
                    paused = false;
                    if (online != null) online.quitMatch();
                }));
        if (setup != null) newSession(setup);
        else openTitle();
        timer = new Timer(1000 / 60, e -> tick());
        timer.setCoalesce(true);
        installMouse();
        installKeys();
        installTyping();
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
        if (online != null) online.layout(w, h);
        int bw = 300;
        int y = h / 2 - 70;
        for (MenuButton b : pauseButtons) {
            b.setBounds((w - bw) / 2, y, bw, 54);
            y += 66;
        }
        y = h / 2 - 70;
        for (MenuButton b : onlinePauseButtons) {
            b.setBounds((w - bw) / 2, y, bw, 58);
            y += 70;
        }
        // à gauche de la barre d'actions (6 boutons de 70 px + fin de tour), sans la chevaucher
        int actionBarLeft = (w - (70 * 6 + 8 * 5 + 18 + 112)) / 2 - 12;
        battleChat.setBounds(16, h - 58, Math.max(200, Math.min(380, actionBarLeft - 28)), 40);
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
        online = null;
        chatOpen = false;
        menu = title;
        startDemo();
    }

    private void openOnline() {
        online = new OnlineController(assets, poses, sound, hud, this::drawDemo,
                p -> camera.focus(p.x, p.y, HUD_TOP, HUD_BOTTOM), this::openTitle);
        layoutAll();
        menu = online.screen();
    }

    /** Suit l'état du mode en ligne : écran de menu, ou combat (nouveau combat → caméra recadrée). */
    private void followOnline() {
        online.update();
        if (online == null) return; // sortie du mode en ligne pendant le traitement
        menu = online.screen();
        GameSession s = online.session();
        if (s != null && s != session) {
            session = s;
            demo = null;
            chatOpen = false;
            layoutAll();
            fitCameras();
        } else if (s == null && session != null && menu != null) {
            session = null;
            if (demo == null) startDemo(); // fond animé des menus (personnages, salon)
        }
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
        List<FighterClass> red = List.of(all.get(random.nextInt(all.size())), all.get(random.nextInt(all.size())));
        List<FighterClass> blue = List.of(all.get(random.nextInt(all.size())), all.get(random.nextInt(all.size())));
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
        return new BattleScene(assets, poses, MapGenerator.generate(theme, 3), List.of()).previewImage(560, 2.2);
    }

    /** Ambiance sonore en boucle de chaque carte (fichiers générés par tools/ambience.py). */
    private static String ambienceOf(MapTheme theme) {
        return switch (theme) {
            case FOREST -> "amb_forest";
            case RUINS -> "amb_ruins";
            case MOUNTAIN -> "amb_snow";
            case DESERT -> "amb_desert";
            case ARENA -> "amb_arena";
        };
    }

    private MapTheme randomTheme() {
        MapTheme[] all = MapTheme.values();
        return all[random.nextInt(all.length)];
    }

    // ------------------------------------------------------------------ boucle

    /** Fait avancer le jeu de {@code dt} secondes (utilisé par la boucle et par les tests). */
    public void advance(double dt) {
        if (online != null) followOnline();
        battleChat.update(dt);
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
        if ((paused && online == null) || session == null) return; // en ligne, la pause ne fige pas la partie
        camera.update(dt);
        session.update(dt);
        hud.update(dt, session);
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastFrame) / 1e9); // pas de saut énorme après une pause
        lastFrame = now;
        advance(dt);
        // la barre de sorts suit le combattant actif (de 1 à 9 sorts selon la classe et le grimoire)
        if (session != null && hud.spellSlots() != session.spellCount()) hud.layout(getWidth(), getHeight(), session.spellCount());
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
                drawConnectionBanner(g);
                return;
            }
            session.scene().draw(g, camera, getWidth(), getHeight(), session.highlights(),
                    session.isOver() ? null : session.active());
            hud.draw(g, session);
            if (online != null) drawBattleChat(g);
            if (paused) drawPause(g);
            drawConnectionBanner(g);
        } finally {
            g.dispose();
        }
    }

    private List<MenuButton> currentPauseButtons() {
        return online != null ? onlinePauseButtons : pauseButtons;
    }

    private void drawPause(Graphics2D g) {
        g.setColor(new Color(5, 4, 2, 170));
        g.fillRect(0, 0, getWidth(), getHeight());
        int w = 360;
        int h = online != null ? 240 : 290;
        UiTheme.panel(g, (getWidth() - w) / 2.0, getHeight() / 2.0 - 140, w, h, 20, true);
        UiTheme.drawCentered(g, "Pause", UiTheme.TITLE.deriveFont(26f), getWidth() / 2.0, getHeight() / 2.0 - 98, UiTheme.GOLD_LIGHT);
        for (MenuButton b : currentPauseButtons()) b.draw(g, mouseX, mouseY);
    }

    /** Chat en surimpression pendant un combat en ligne (Entrée pour écrire). */
    private void drawBattleChat(Graphics2D g) {
        int w = battleChat.bounds().width;
        int bottom = chatOpen ? battleChat.bounds().y - 10 : getHeight() - 22;
        online.chat().drawFading(g, 18, bottom, w, chatOpen ? 12 : 7, chatOpen);
        if (chatOpen) {
            battleChat.draw(g, mouseX, mouseY);
        } else {
            UiTheme.drawOutlinedLeft(g, "Entrée : discuter", UiTheme.BODY.deriveFont(11f), 18, getHeight() - 6,
                    new Color(255, 255, 255, 150), new Color(0, 0, 0, 120));
        }
    }

    private void drawConnectionBanner(Graphics2D g) {
        String text = online == null ? null : online.banner();
        if (text == null) return;
        int w = 460;
        UiTheme.panel(g, (getWidth() - w) / 2.0, 130, w, 44, 12, true);
        UiTheme.drawCentered(g, text, UiTheme.BODY_BOLD.deriveFont(14f), getWidth() / 2.0, 158, UiTheme.DAMAGE_HEAVY);
    }

    // ------------------------------------------------------------------ souris

    private void installMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (menu != null) {
                    if (SwingUtilities.isLeftMouseButton(e)) menu.mousePressed(e.getX(), e.getY());
                    else if (SwingUtilities.isRightMouseButton(e)) menu.mouseRightPressed(e.getX(), e.getY());
                    return;
                }
                if (paused) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        for (MenuButton b : currentPauseButtons()) if (b.press(e.getX(), e.getY())) break;
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
            case REPLAY -> {
                if (online == null) newSession(session.setup());
            }
            case NEW_MAP -> {
                if (online == null) newSession(session.setup().withMap(randomTheme(), randomSeed(random)));
            }
            case MENU -> {
                if (online != null) online.backToLobby();
                else openTitle();
            }
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
            else if (inBattle() && online != null) openBattleChat();
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
        for (int i = 1; i <= 9; i++) {
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
            if (session.isOver() && online == null) perform(HudAction.of(HudAction.Type.REPLAY));
        });
        battleKey("N", () -> {
            if (session.isOver() && online == null) perform(HudAction.of(HudAction.Type.NEW_MAP));
        });
    }

    private void openBattleChat() {
        chatOpen = true;
        battleChat.setFocused(true);
    }

    /** La saisie de texte est-elle active (champ d'un menu, ou chat du combat) ? */
    private boolean typing() {
        return (menu != null && menu.wantsText()) || (menu == null && chatOpen);
    }

    /**
     * Saisie de texte. Quand un champ a le focus, les touches sont consommées ici : les raccourcis du jeu
     * (D, C, M, 1-5, Espace…) ne se déclenchent pas pendant qu'on écrit.
     */
    private void installTyping() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!typing()) {
                    if (menu != null && e.getKeyCode() == KeyEvent.VK_TAB) menu.key("TAB");
                    return;
                }
                int code = e.getKeyCode();
                boolean ctrl = e.isControlDown() || e.isMetaDown();
                String key = switch (code) {
                    case KeyEvent.VK_ENTER -> "ENTER";
                    case KeyEvent.VK_ESCAPE -> "ESCAPE";
                    case KeyEvent.VK_BACK_SPACE -> "BACK_SPACE";
                    case KeyEvent.VK_TAB -> e.isShiftDown() ? "shift TAB" : "TAB";
                    case KeyEvent.VK_UP -> "UP";
                    case KeyEvent.VK_DOWN -> "DOWN";
                    default -> null;
                };
                if (ctrl && code == KeyEvent.VK_V) {
                    typed(clipboardText());
                } else if (key != null) {
                    if (menu != null) menu.key(key);
                    else battleChatKey(key);
                }
                e.consume();
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (!typing()) return;
                char c = e.getKeyChar();
                if (!Character.isISOControl(c) && c != KeyEvent.CHAR_UNDEFINED && !e.isControlDown()) {
                    typed(String.valueOf(c));
                }
                e.consume();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (typing()) e.consume();
            }
        });
        setFocusTraversalKeysEnabled(false); // Tab sert à passer d'un champ à l'autre
    }

    private void typed(String text) {
        if (text == null || text.isEmpty()) return;
        if (menu != null) menu.text(text);
        else battleChat.insert(text);
    }

    private void battleChatKey(String key) {
        switch (key) {
            case "ENTER" -> {
                if (online != null) online.sendChat(battleChat.text());
                battleChat.setText("");
                chatOpen = false;
            }
            case "ESCAPE" -> {
                battleChat.setText("");
                chatOpen = false;
            }
            case "BACK_SPACE" -> battleChat.backspace();
            default -> { }
        }
    }

    private static String clipboardText() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return data == null ? "" : data.toString().replace('\n', ' ').trim();
        } catch (Exception e) {
            return "";
        }
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
