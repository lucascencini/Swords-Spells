package com.minidofus.net;

import com.minidofus.Main;
import com.minidofus.server.AccountStore;

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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parcours des comptes dans la vraie fenêtre (écran ou Xvfb), zone de jeu de 1280×820 : connexion à un compte
 * (avec un Mage niveau 26 préparé), création d'un personnage, talents, grimoire, combat solo jusqu'à l'écran de
 * fin avec l'expérience, retour aux personnages puis salon.
 */
public final class AccountSmokeTest {

    private static Robot robot;
    private static Point origin;
    private static Rectangle area;
    private static File out;

    private AccountSmokeTest() { }

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "accounts");
        out.mkdirs();
        Path data = Files.createTempDirectory("smoke-accounts");
        AccountStore store = new AccountStore(data);
        AccountStore.Account acc = store.create("Lucas", AccountStore.clientKey("Lucas", "secret"));
        AccountStore.Character merlin = new AccountStore.Character();
        merlin.id = store.newCharacterId();
        merlin.name = "Merlin";
        merlin.classId = "mage";
        merlin.level = 26;
        merlin.xp = 900;
        acc.characters.add(merlin);
        store.save(acc);
        Path file;
        try (var s = Files.list(data.resolve("accounts"))) {
            file = s.findFirst().orElseThrow();
        }
        GameServer server = new GameServer(0, "", data, s -> System.out.println("  serveur " + s));
        int port = server.start();

        Main.main(new String[0]);
        robot = new Robot();
        robot.setAutoDelay(30);
        Thread.sleep(3500);
        JFrame frame = null;
        for (Frame f : Frame.getFrames()) if (f.isShowing() && f instanceof JFrame j) frame = j;
        Container content = frame.getContentPane();
        origin = content.getLocationOnScreen();
        area = new Rectangle(origin.x, origin.y, content.getWidth(), content.getHeight());
        System.out.println("  zone de jeu " + area.width + "×" + area.height);

        click(640, 449);                                   // « Jouer en ligne »
        Thread.sleep(400);
        click(640, 220);
        selectAllAndPaste("127.0.0.1:" + port);
        click(640, 290);
        selectAllAndPaste("Lucas");
        click(640, 360);
        selectAllAndPaste("secret");
        shot("01_connexion");
        click(527, 496);                                   // « Se connecter »
        Thread.sleep(1500);
        shot("02_personnages");

        click(280, 238);                                   // « Nouveau personnage »
        Thread.sleep(300);
        paste("Brok");
        click(259, 420);                                   // carte Guerrier
        Thread.sleep(300);
        shot("03_creation");
        click(990, 679);                                   // « Créer »
        Thread.sleep(1200);
        shot("04_personnages_2");

        click(280, 164);                                   // Merlin
        click(607, 654);                                   // Talents
        Thread.sleep(400);
        for (int i = 0; i < 3; i++) click(168, 221);       // Flammes ardentes ×3
        for (int i = 0; i < 2; i++) click(344, 221);
        for (int i = 0; i < 3; i++) click(552, 221);       // Morsure du froid ×3
        for (int i = 0; i < 2; i++) click(728, 221);
        click(596, 362);                                   // palier 2 du givre
        click(212, 362);
        click(212, 362);
        click(1024 - 44, 221);
        rightClick(1024 - 44, 221);                        // retiré aussitôt (clic droit)
        robot.mouseMove(origin.x + 212, origin.y + 362);   // info-bulle
        Thread.sleep(500);
        shot("05_talents");
        String before = Files.readString(file);
        click(1106, 765);                                  // Enregistrer
        waitFor(() -> !read(file).equals(before), 4000);
        Thread.sleep(400);
        shot("06_talents_enregistres");
        System.out.println("  talents enregistrés : " + read(file).contains("mage_pyro_combustion"));
        click(134, 765);                                   // Retour

        click(834, 654);                                   // Grimoire
        Thread.sleep(400);
        click(114 + 170, 114 + 122 + 54);                  // Téléportation (retirée)
        click(114 + 170, 114 + 3 * 122 + 54);              // Nova (équipée)
        robot.mouseMove(origin.x + 700, origin.y + 600);
        Thread.sleep(300);
        shot("07_grimoire");
        String before2 = Files.readString(file);
        click(1056, 750);                                  // Enregistrer
        waitFor(() -> !read(file).equals(before2), 4000);
        System.out.println("  grimoire enregistré : " + read(file).contains("nova"));
        click(164, 750);                                   // Retour
        Thread.sleep(300);

        click(720, 725);                                   // Combat contre l'ordinateur
        Thread.sleep(400);
        click(728, 196);                                   // 2 contre 2
        click(728, 248);                                   // difficulté : Difficile → Facile
        click(728, 248);                                   // Facile → Normal ... (cycle)
        Thread.sleep(300);
        shot("08_solo");
        String before3 = Files.readString(file);
        click(976, 687);                                   // Lancer le combat
        Thread.sleep(6000);
        shot("09_combat");
        robot.keyPress(KeyEvent.VK_9);
        robot.keyRelease(KeyEvent.VK_9);
        Thread.sleep(800);
        shot("10_sort_choisi");
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline && read(file).equals(before3)) {
            key(KeyEvent.VK_SPACE);
            Thread.sleep(1200);
        }
        System.out.println("  expérience enregistrée : " + !read(file).equals(before3));
        Thread.sleep(5000);
        shot("11_fin_recompenses");
        key(KeyEvent.VK_ESCAPE);
        Thread.sleep(1200);
        shot("12_retour_personnages");
        click(1061, 725);                                  // Salon en ligne
        Thread.sleep(800);
        click(233, 485);                                   // Prendre la place rouge 1
        Thread.sleep(600);
        click(177, 357);                                   // ‹ : personnage précédent (Brok)
        Thread.sleep(800);
        shot("13_salon");
        System.exit(0);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (Exception e) {
            return "";
        }
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
        Thread.sleep(160);
    }

    private static void rightClick(int x, int y) throws InterruptedException {
        robot.mouseMove(origin.x + x, origin.y + y);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        Thread.sleep(160);
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
