package com.minidofus.game;

import com.minidofus.model.BattleEvent;
import com.minidofus.model.CastStyle;
import com.minidofus.model.Fighter;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.TargetMode;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Outil de vérification visuelle : affiche un aperçu de portée en mode deux joueurs, puis joue une partie
 * complète ordinateur contre ordinateur dans une vue hors écran. Il enregistre une capture à la première
 * apparition de chaque style de sort, et mesure le temps de rendu.
 * Usage : {@code Snapshots <dossier> [carte] [graine] [équipe rouge] [équipe bleue]}, ex. {@code shots lake 3 warrior,archer mage,mage}
 */
public final class Snapshots {

    private static final int W = 1280;
    private static final int H = 800;
    private static final double DT = 1 / 60.0;

    private final File out;
    private final BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private GameView view;
    private long renderNanos;
    private int renders;
    private int shots;

    private Snapshots(File out) {
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "snapshots");
        out.mkdirs();
        MapTheme theme = args.length > 1 ? MapTheme.valueOf(args[1].toUpperCase(Locale.ROOT)) : MapTheme.FOREST;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 7;
        List<FighterClass> red = args.length > 3 ? team(args[3]) : List.of(FighterCatalog.WARRIOR, FighterCatalog.ARCHER);
        List<FighterClass> blue = args.length > 4 ? team(args[4]) : List.of(FighterCatalog.MAGE, FighterCatalog.WARRIOR);
        new Snapshots(out).play(theme, seed, red, blue);
        System.exit(0);
    }

    private static List<FighterClass> team(String csv) {
        return Arrays.stream(csv.split(",")).map(FighterCatalog::byId).toList();
    }

    private void play(MapTheme theme, long seed, List<FighterClass> red, List<FighterClass> blue) throws Exception {
        // 1) deux joueurs humains : aperçus de déplacement et de portée
        view = new GameView(new MatchSetup(theme, seed, red, blue, null, null));
        view.layoutOffscreen(W, H);
        run(0.7);
        shot("intro");
        run(2.2);
        GameSession s = view.session();
        Fighter me = s.battle().active();
        Fighter foe = closestEnemy(s, me);
        s.hover(foe.position().plus(me.position().x() < foe.position().x() ? -3 : 3, 0));
        run(0.05);
        shot("apercu_deplacement");
        List<Spell> spells = me.fighterClass().spells();
        for (int i = 0; i < spells.size(); i++) {
            if (spells.get(i).maxRange() >= 4 && spells.get(i).targetMode() != TargetMode.SELF) {
                s.selectSpell(i);
                s.hover(foe.position());
                run(0.05);
                shot("apercu_portee_" + spells.get(i).id());
                break;
            }
        }

        // 2) ordinateur contre ordinateur, jusqu'à la fin
        view = new GameView(new MatchSetup(theme, seed, red, blue, Difficulty.HARD, Difficulty.NORMAL));
        view.layoutOffscreen(W, H);
        Set<CastStyle> seen = EnumSet.noneOf(CastStyle.class);
        List<String> pending = new ArrayList<>();
        double[] shotAt = {-1};
        double[] clock = {0};
        boolean[] sawDeath = {false};
        boolean[] sawTransition = {false};
        // les événements sont émis à l'action ; l'animation correspondante démarre quand la file d'attente l'atteint,
        // d'où un délai approximatif avant chaque capture
        view.session().battle().addListener(e -> {
            if (shotAt[0] >= 0) return;
            if (e instanceof BattleEvent.SpellCast c && seen.add(c.spell().style())) {
                pending.add("sort_" + c.spell().id());
                shotAt[0] = clock[0] + (c.spell().style() == CastStyle.ARROW_RAIN || c.spell().style() == CastStyle.FIREBALL ? 0.46 : 0.34);
            } else if (e instanceof BattleEvent.Died && !sawDeath[0]) {
                sawDeath[0] = true;
                pending.add("mort");
                shotAt[0] = clock[0] + 0.75;
            } else if (e instanceof BattleEvent.TurnStarted t && t.round() == 2 && !sawTransition[0]) {
                sawTransition[0] = true;
                pending.add("transition_de_tour");
                shotAt[0] = clock[0] + 0.25;
            }
        });
        for (int i = 0; i < 60 * 600 && !(view.session().isOver() && !view.session().isBusy()); i++) {
            view.advance(DT);
            clock[0] += DT;
            render();
            if (shotAt[0] >= 0 && clock[0] >= shotAt[0]) {
                shot(pending.remove(0));
                shotAt[0] = -1;
            }
        }
        run(1.5);
        shot("fin");
        GameSession end = view.session();
        System.out.printf(Locale.ROOT, "%d manches, vainqueur : %s, %d styles de sorts vus%n",
                end.round(), end.isOver() ? end.winner().displayName() : "aucun", seen.size());
        System.out.printf(Locale.ROOT, "%d images rendues, %.2f ms par image en moyenne (rendu logiciel hors écran)%n",
                renders, renderNanos / 1e6 / renders);
    }

    private static Fighter closestEnemy(GameSession s, Fighter me) {
        Fighter best = null;
        for (Fighter f : s.battle().fighters()) {
            if (!f.isEnemyOf(me)) continue;
            if (best == null || f.position().distanceTo(me.position()) < best.position().distanceTo(me.position())) best = f;
        }
        return best;
    }

    private void run(double seconds) {
        for (double t = 0; t < seconds; t += DT) {
            view.advance(DT);
            render();
        }
    }

    private void render() {
        long t0 = System.nanoTime();
        Graphics2D g = frame.createGraphics();
        view.paint(g);
        g.dispose();
        renderNanos += System.nanoTime() - t0;
        renders++;
    }

    private void shot(String name) throws Exception {
        render();
        ImageIO.write(frame, "png", new File(out, String.format("%02d_%s.png", ++shots, name)));
    }
}
