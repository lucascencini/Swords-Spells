package com.minidofus.net;

import com.minidofus.Main;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Parcours en ligne complet dans la vraie fenêtre (écran ou Xvfb) : un serveur local, un joueur automatique
 * (chef du salon) et le jeu piloté avec de vrais clics et touches : connexion, salon, combat, chat.
 * Les coordonnées correspondent à une zone de jeu de 1280×820.
 */
public final class OnlineSmokeTest {

    private static Robot robot;
    private static Point origin;
    private static Rectangle area;
    private static File out;

    private OnlineSmokeTest() { }

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "online");
        out.mkdirs();
        GameServer server = new GameServer(0, "", s -> System.out.println("  serveur " + s));
        int port = server.start();

        NetTests.Bot bot = new NetTests.Bot(port, "Bruno", "");
        Thread pump = new Thread(() -> {
            while (true) {
                bot.pump();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        pump.setDaemon(true);
        pump.start();
        waitFor(() -> bot.lobby != null, 5000);
        bot.send(Protocol.SETTINGS, "FOREST", 2, "NORMAL", 60);
        bot.send(Protocol.CLAIM, 3);
        bot.send(Protocol.CHAT, "Salut ! Prends une place rouge et on lance.");

        Main.main(new String[0]);
        robot = new Robot();
        robot.setAutoDelay(40);
        Thread.sleep(3500);
        JFrame frame = null;
        for (Frame f : Frame.getFrames()) if (f.isShowing() && f instanceof JFrame j) frame = j;
        Container content = frame.getContentPane();
        origin = content.getLocationOnScreen();
        area = new Rectangle(origin.x, origin.y, content.getWidth(), content.getHeight());

        click(640, 449);                                   // « Jouer en ligne »
        Thread.sleep(400);
        click(640, 220);                                   // champ adresse
        selectAllAndPaste("127.0.0.1:" + port);
        click(640, 290);                                   // champ compte / pseudo
        selectAllAndPaste("Lucas");
        shot("1_connexion");
        click(489, 561);                                   // « Jouer en invité »
        boolean joined = waitFor(() -> bot.lobby != null && bot.lobby.players.size() == 2, 6000);
        System.out.println("  le jeu a rejoint le salon : " + joined);
        Thread.sleep(800);
        click(233, 485);                                   // « Prendre » la place rouge 1
        waitFor(() -> bot.lobby.slotOwner[0] > 0, 4000);
        click(1000, 730);                                  // champ de chat du salon
        paste("Bonjour Bruno, c'est parti !");
        key(KeyEvent.VK_ENTER);
        waitFor(() -> bot.chat.stream().anyMatch(l -> l.startsWith("Lucas")), 4000);
        Thread.sleep(500);
        shot("2_salon");
        System.out.println("  chat reçu par l'autre joueur : " + bot.chat.stream().anyMatch(l -> l.startsWith("Lucas")));

        bot.autoplay = true;
        bot.send(Protocol.START);
        waitFor(() -> bot.synced, 5000);
        Thread.sleep(4500);
        shot("3_combat");
        // écrire dans le chat pendant le combat
        key(KeyEvent.VK_ENTER);
        paste("Bonne chance !");
        Thread.sleep(300);
        shot("4_chat_en_combat");
        key(KeyEvent.VK_ENTER);
        boolean battleChat = waitFor(() -> bot.chat.stream().anyMatch(l -> l.equals("Lucas: Bonne chance !")), 4000);
        System.out.println("  chat en combat reçu : " + battleChat);
        // jouer : à chaque tour du joueur, Espace (fin de tour) ; les autres tours sont joués par Bruno et l'ordinateur
        int ends = 0;
        long deadline = System.currentTimeMillis() + 240_000;
        boolean midShot = false;
        while (System.currentTimeMillis() < deadline && !(bot.match != null && bot.match.battle().isOver())) {
            if (bot.match != null && bot.synced && bot.match.controllerOfActive() > 0 && bot.match.controllerOfActive() != bot.net.playerId()) {
                Thread.sleep(2500); // laisse les animations se terminer côté jeu
                if (!midShot) {
                    shot("5_tour_du_joueur");
                    midShot = true;
                }
                int before = bot.match.applied();
                key(KeyEvent.VK_SPACE);
                waitFor(() -> bot.match.applied() > before, 4000);
                ends++;
            }
            Thread.sleep(200);
        }
        Thread.sleep(3500);
        shot("6_fin");
        System.out.println("  fins de tour envoyées depuis le jeu : " + ends + ", combat terminé : "
                + (bot.match != null && bot.match.battle().isOver()) + ", désynchronisations : " + bot.desyncs);
        System.exit(0);
    }

    private interface Cond {
        boolean ok();
    }

    private static boolean waitFor(Cond c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.ok()) return true;
            Thread.sleep(50);
        }
        return c.ok();
    }

    private static void click(int x, int y) throws InterruptedException {
        robot.mouseMove(origin.x + x, origin.y + y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(200);
    }

    private static void key(int code) {
        robot.keyPress(code);
        robot.keyRelease(code);
    }

    private static void paste(String text) throws InterruptedException {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        robot.keyPress(KeyEvent.VK_CONTROL);
        key(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        Thread.sleep(150);
    }

    private static void selectAllAndPaste(String text) throws InterruptedException {
        for (int i = 0; i < 40; i++) key(KeyEvent.VK_BACK_SPACE);
        paste(text);
    }

    private static void shot(String name) throws Exception {
        ImageIO.write(robot.createScreenCapture(area), "png", new File(out, name + ".png"));
    }
}
