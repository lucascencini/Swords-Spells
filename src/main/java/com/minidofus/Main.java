package com.minidofus;

import com.minidofus.engine.Assets;
import com.minidofus.game.GameView;
import com.minidofus.model.map.MapTheme;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;

/**
 * Point d'entrée. Options :
 * <pre>
 *   --map forest|ruins|lake   ambiance de la carte (sinon aléatoire)
 *   --seed 1234               graine de la carte (pour rejouer exactement la même)
 * </pre>
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        MapTheme theme = null;
        Long seed = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--map" -> theme = parseTheme(args[i + 1]);
                case "--seed" -> seed = Long.parseLong(args[i + 1]);
                default -> System.err.println("Option inconnue : " + args[i]);
            }
        }
        MapTheme finalTheme = theme;
        Long finalSeed = seed;
        SwingUtilities.invokeLater(() -> open(finalTheme, finalSeed));
    }

    private static void open(MapTheme theme, Long seed) {
        JFrame frame = new JFrame("Mini Dofus — Combat tactique");
        GameView view = new GameView(theme, seed);
        frame.setContentPane(view);
        frame.setIconImage(new Assets().image("ui/portrait_warrior"));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                view.stop();
                System.exit(0);
            }
        });
        frame.setMinimumSize(new Dimension(1000, 660));
        frame.setSize(1280, 820);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        view.start();
    }

    private static MapTheme parseTheme(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "forest", "foret", "forêt" -> MapTheme.FOREST;
            case "ruins", "ruines" -> MapTheme.RUINS;
            case "lake", "lac" -> MapTheme.LAKE;
            default -> throw new IllegalArgumentException("Carte inconnue : " + value + " (forest, ruins, lake)");
        };
    }
}
