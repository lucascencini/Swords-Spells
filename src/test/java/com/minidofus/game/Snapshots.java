package com.minidofus.game;

import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Pathfinder;
import com.minidofus.model.Spell;
import com.minidofus.model.TargetMode;
import com.minidofus.model.map.MapTheme;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Outil de vérification visuelle : joue une partie automatique (les deux camps sont pilotés par une IA
 * simple) dans une vue hors écran, enregistre des captures aux moments clés et mesure le temps de rendu.
 * Usage : {@code java -cp ... com.minidofus.game.Snapshots <dossier> [forest|ruins|lake] [graine]}
 */
public final class Snapshots {

    private static final int W = 1280;
    private static final int H = 800;
    private static final double DT = 1 / 60.0;

    private final GameView view;
    private final File out;
    private final BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private long renderNanos;
    private int renders;
    private int shots;

    private Snapshots(GameView view, File out) {
        this.view = view;
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "snapshots");
        out.mkdirs();
        MapTheme theme = args.length > 1 ? MapTheme.valueOf(args[1].toUpperCase(Locale.ROOT)) : MapTheme.FOREST;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 7;
        GameView view = new GameView(theme, seed);
        view.layoutOffscreen(W, H);
        new Snapshots(view, out).play();
        System.exit(0);
    }

    private GameSession session() {
        return view.session();
    }

    private Battle battle() {
        return session().battle();
    }

    private void run(double seconds) {
        for (double t = 0; t < seconds; t += DT) {
            view.advance(DT);
            render();
        }
    }

    private void settle() {
        int guard = 0;
        do {
            view.advance(DT);
            render();
        } while (session().isBusy() && guard++ < 600);
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

    private void play() throws Exception {
        run(0.6);
        shot("intro_banniere");
        run(2.0);

        // aperçu du chemin de déplacement
        Fighter a = battle().active();
        Pathfinder.Reach reach = battle().reach();
        Cell target = closestTo(reach.cells(), battle().opponentOf(a).position());
        session().hover(target);
        run(0.1);
        shot("apercu_deplacement");

        boolean rangeShot = false;
        boolean impactShot = false;
        boolean projectileShot = false;
        for (int turn = 0; turn < 60 && !battle().isOver(); turn++) {
            Fighter me = battle().active();
            Fighter foe = battle().opponentOf(me);
            boolean acted = true;
            int actions = 0;
            while (acted && !battle().isOver() && actions++ < 6) {
                acted = false;
                // 1) attaquer si possible
                List<Spell> spells = me.fighterClass().spells();
                for (int i = 0; i < spells.size(); i++) {
                    Spell s = spells.get(i);
                    if (s.targetMode() == TargetMode.SELF) continue;
                    if (!battle().canCast(s, foe.position())) continue;
                    session().selectSpell(i);
                    session().hover(foe.position());
                    run(0.05);
                    if (!impactShot && s.targetMode() == TargetMode.CELL) {
                        shot("apercu_zone_" + s.id());
                        impactShot = true;
                    } else if (!rangeShot) {
                        shot("apercu_portee_" + s.id());
                        rangeShot = true;
                    }
                    session().click(foe.position());
                    if (!projectileShot && (s.id().equals("fireball") || s.id().equals("ranged"))) {
                        run(0.22);
                        shot("projectile_" + s.id());
                        projectileShot = true;
                    }
                    run(0.45);
                    if (shots < 12) shot("impact_" + s.id());
                    settle();
                    acted = true;
                    break;
                }
                if (acted) continue;
                // 2) sinon se rapprocher
                if (me.pm() > 0) {
                    Pathfinder.Reach r = battle().reach();
                    Cell best = closestTo(r.cells(), foe.position());
                    if (best != null && best.distanceTo(foe.position()) < me.position().distanceTo(foe.position())) {
                        session().selectMove();
                        session().hover(best);
                        session().click(best);
                        settle();
                        acted = true;
                        continue;
                    }
                }
                // 3) sinon sort personnel (bouclier/soin)
                for (int i = 0; i < me.fighterClass().spells().size(); i++) {
                    Spell s = me.fighterClass().spells().get(i);
                    if (s.targetMode() == TargetMode.SELF && me.pa() >= s.cost() && (s.id().equals("shield") || me.hp() < me.maxHp() - 12)) {
                        session().selectSpell(i);
                        session().selectSpell(i); // ré-appuyer lance le sort
                        run(0.2);
                        if (shots < 14) shot("sort_personnel_" + s.id());
                        settle();
                        acted = true;
                        break;
                    }
                }
            }
            if (!battle().isOver()) {
                session().endTurn();
                settle();
            }
        }
        run(1.5);
        shot("ecran_de_fin");
        System.out.printf(Locale.ROOT, "%d tours joués, vainqueur : %s%n", battle().turn(),
                battle().isOver() ? battle().winner().name() : "aucun");
        System.out.printf(Locale.ROOT, "%d images rendues, %.2f ms par image en moyenne (rendu logiciel hors écran)%n",
                renders, renderNanos / 1e6 / renders);
    }

    private static Cell closestTo(List<Cell> cells, Cell goal) {
        Cell best = null;
        for (Cell c : new ArrayList<>(cells)) {
            if (best == null || c.distanceTo(goal) < best.distanceTo(goal)) best = c;
        }
        return best;
    }
}
