package com.minidofus;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Parcours complet des menus dans la vraie fenêtre (nécessite un écran ou Xvfb) : écran titre,
 * configuration, combat contre l'ordinateur, pause, retour au menu. Les positions des boutons
 * correspondent à la mise en page pour une zone de jeu de 1280×820.
 */
public final class MenuSmokeTest {

    private static Robot robot;
    private static Point origin;
    private static Rectangle area;
    private static File out;

    private MenuSmokeTest() { }

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "menus");
        out.mkdirs();
        Main.main(new String[0]);
        robot = new Robot();
        robot.setAutoDelay(40);
        Thread.sleep(4000);
        JFrame frame = null;
        for (Frame f : Frame.getFrames()) if (f.isShowing() && f instanceof JFrame j) frame = j;
        if (frame == null) throw new IllegalStateException("aucune fenêtre affichée");
        Container content = frame.getContentPane();
        origin = content.getLocationOnScreen();
        area = new Rectangle(origin.x, origin.y, content.getWidth(), content.getHeight());
        System.out.println("zone de jeu : " + content.getWidth() + "x" + content.getHeight());

        shot("1_titre");
        click(640, 409);                 // « Contre l'ordinateur »
        Thread.sleep(500);
        move(376, 380);                  // survol du premier combattant : info-bulle de classe
        Thread.sleep(300);
        shot("2_configuration");
        click(717, 611);                 // carte suivante (aperçu)
        click(717, 611);
        click(376, 380);                 // change la classe du premier combattant
        Thread.sleep(300);
        shot("3_configuration_carte");
        click(934, 689);                 // « Lancer le combat »
        Thread.sleep(4500);
        shot("4_combat");
        key(KeyEvent.VK_ESCAPE);
        Thread.sleep(400);
        shot("5_pause");
        click(640, 499);                 // « Menu principal »
        Thread.sleep(1500);
        shot("6_retour_titre");
        System.out.println("parcours des menus terminé");
        System.exit(0);
    }

    private static void move(int x, int y) {
        robot.mouseMove(origin.x + x, origin.y + y);
    }

    private static void click(int x, int y) throws InterruptedException {
        move(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(250);
    }

    private static void key(int code) {
        robot.keyPress(code);
        robot.keyRelease(code);
    }

    private static void shot(String name) throws Exception {
        ImageIO.write(robot.createScreenCapture(area), "png", new File(out, name + ".png"));
    }
}
