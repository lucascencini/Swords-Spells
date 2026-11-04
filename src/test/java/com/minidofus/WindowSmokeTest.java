package com.minidofus;

import javax.imageio.ImageIO;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Test de fumée de la vraie fenêtre : lance le jeu, envoie de vrais clics et touches via {@link Robot}
 * et enregistre des captures d'écran. Nécessite un écran (ou Xvfb).
 */
public final class WindowSmokeTest {

    private WindowSmokeTest() { }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "smoke");
        out.mkdirs();
        Main.main(new String[] {"--map", "forest", "--seed", "11"});
        Robot robot = new Robot();
        robot.setAutoDelay(40);
        Thread.sleep(3500);

        Frame frame = null;
        for (Frame f : Frame.getFrames()) if (f.isShowing()) frame = f;
        if (frame == null) throw new IllegalStateException("aucune fenêtre affichée");
        java.awt.Container content = ((javax.swing.JFrame) frame).getContentPane();
        Rectangle b = content.getBounds();
        java.awt.Point origin = content.getLocationOnScreen();
        System.out.println("fenêtre : " + frame.getTitle() + " " + b.width + "x" + b.height);
        Rectangle area = new Rectangle(origin.x, origin.y, b.width, b.height);
        ImageIO.write(robot.createScreenCapture(area), "png", new File(out, "1_fenetre.png"));

        // survol du centre de l'arène puis touche 2 (sort de zone ou charge) et survol
        robot.mouseMove(origin.x + b.width / 2, origin.y + b.height / 2 - 20);
        robot.keyPress(KeyEvent.VK_2);
        robot.keyRelease(KeyEvent.VK_2);
        Thread.sleep(400);
        ImageIO.write(robot.createScreenCapture(area), "png", new File(out, "2_touche_2.png"));

        // Échap puis clic sur « Fin du tour » (coordonnées calculées comme dans Hud.layout)
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        int total = 74 * 4 + 10 * 3 + 22 + 118;
        int endX = (b.width - total) / 2 + total - 59;
        int endY = b.height - 84 - 18 + 42;
        robot.mouseMove(origin.x + endX, origin.y + endY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(500);
        ImageIO.write(robot.createScreenCapture(area), "png", new File(out, "3_fin_de_tour.png"));

        // zoom molette et touche F3 (compteur d'images par seconde)
        robot.mouseMove(origin.x + b.width / 2, origin.y + b.height / 2);
        robot.mouseWheel(-3);
        robot.keyPress(KeyEvent.VK_F3);
        robot.keyRelease(KeyEvent.VK_F3);
        Thread.sleep(2000);
        ImageIO.write(robot.createScreenCapture(area), "png", new File(out, "4_zoom_fps.png"));
        System.out.println("test de fenêtre terminé");
        System.exit(0);
    }
}
