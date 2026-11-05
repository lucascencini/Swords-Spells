package com.minidofus;

import com.minidofus.engine.Assets;
import com.minidofus.game.GameView;
import com.minidofus.game.MatchSetup;
import com.minidofus.model.FighterClass;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Point d'entrée. {@code --server} lance le serveur en ligne sans fenêtre (voir {@link com.minidofus.net.ServerMain}).
 * Point d'entrée. Sans option, le jeu s'ouvre sur l'écran titre. Avec des options, il lance directement
 * le combat décrit (pratique pour tester) :
 * <pre>
 *   --map forest|ruins|lake|snow|desert|dungeon   carte (sinon aléatoire)
 *   --seed 1234                      graine de la carte (pour rejouer exactement la même)
 *   --red warrior,archer             composition de l'équipe rouge (1 à 3 parmi warrior, mage, archer)
 *   --blue mage                      composition de l'équipe bleue
 *   --ai easy|normal|hard            l'ordinateur joue l'équipe bleue
 *   --ai-red easy|normal|hard        l'ordinateur joue l'équipe rouge
 * </pre>
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("--server")) {
            com.minidofus.net.ServerMain.main(args); // serveur sans fenêtre : aucune classe graphique chargée
            return;
        }
        Random random = new Random();
        MapTheme theme = MapTheme.values()[random.nextInt(MapTheme.values().length)];
        long seed = GameView.randomSeed(random);
        List<FighterClass> red = List.of(FighterCatalog.WARRIOR);
        List<FighterClass> blue = List.of(FighterCatalog.MAGE);
        Difficulty redAi = null;
        Difficulty blueAi = null;
        boolean direct = args.length > 0;
        for (int i = 0; i + 1 < args.length; i += 2) {
            String value = args[i + 1];
            switch (args[i]) {
                case "--map" -> theme = parseTheme(value);
                case "--seed" -> seed = Long.parseLong(value);
                case "--red" -> red = parseTeam(value);
                case "--blue" -> blue = parseTeam(value);
                case "--ai" -> blueAi = parseDifficulty(value);
                case "--ai-red" -> redAi = parseDifficulty(value);
                default -> System.err.println("Option inconnue : " + args[i]);
            }
        }
        MatchSetup setup = direct ? new MatchSetup(theme, seed, red, blue, redAi, blueAi) : null;
        SwingUtilities.invokeLater(() -> open(setup));
    }

    private static void open(MatchSetup setup) {
        JFrame frame = new JFrame("Swords & Spells — Combat tactique");
        GameView view = new GameView(setup, () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
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

    private static List<FighterClass> parseTeam(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).map(FighterCatalog::byId).toList();
    }

    private static Difficulty parseDifficulty(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "easy", "facile" -> Difficulty.EASY;
            case "hard", "difficile" -> Difficulty.HARD;
            default -> Difficulty.NORMAL;
        };
    }

    private static MapTheme parseTheme(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "forest", "foret", "forêt" -> MapTheme.FOREST;
            case "ruins", "ruines" -> MapTheme.RUINS;
            case "lake", "lac" -> MapTheme.LAKE;
            case "snow", "neige" -> MapTheme.SNOW;
            case "desert", "désert" -> MapTheme.DESERT;
            case "dungeon", "donjon", "crypte" -> MapTheme.DUNGEON;
            default -> throw new IllegalArgumentException("Carte inconnue : " + value + " (forest, ruins, lake, snow, desert, dungeon)");
        };
    }
}
