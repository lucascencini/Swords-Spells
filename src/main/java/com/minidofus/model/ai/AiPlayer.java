package com.minidofus.model.ai;

import com.minidofus.model.Battle;
import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.FighterClass;
import com.minidofus.model.IntRange;
import com.minidofus.model.Spell;
import com.minidofus.model.Status;
import com.minidofus.model.Team;
import com.minidofus.model.spell.AreaStrikeSpell;
import com.minidofus.model.spell.ChargeSpell;
import com.minidofus.model.spell.GrappleSpell;
import com.minidofus.model.spell.PoisonSpell;
import com.minidofus.model.spell.StrikeSpell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Adversaire contrôlé par l'ordinateur. À chaque appel de {@link #decide}, il essaie sur des copies
 * du combat chaque action possible (lancer un sort maintenant, ou se déplacer puis lancer le meilleur
 * sort depuis la nouvelle case) et garde celle qui mène à la meilleure situation pour son équipe.
 * Il est rappelé après chaque action, jusqu'à ce qu'il choisisse de finir son tour.
 */
public final class AiPlayer {

    private static final double DEAD_PENALTY = 90;
    private static final double WIN_SCORE = 10_000;

    private final Difficulty difficulty;
    private final Random random;
    private final Map<FighterClass, double[]> threatCache = new HashMap<>();

    public AiPlayer(Difficulty difficulty, Random random) {
        this.difficulty = difficulty;
        this.random = random;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    private record Candidate(AiAction action, double score) { }

    public AiAction decide(Battle battle) {
        if (battle.isOver()) return new AiAction.EndTurn();
        Fighter me = battle.active();
        Team team = me.team();
        List<Candidate> candidates = new ArrayList<>();
        double stay = evaluate(battle, team);
        candidates.add(new Candidate(new AiAction.EndTurn(), stay + 0.01));

        for (Spell spell : me.fighterClass().spells()) {
            if (battle.unavailableReason(spell) != null) continue;
            for (Cell target : targets(battle, me, spell)) {
                if (!battle.canCast(spell, target)) continue;
                Battle sim = battle.simulate();
                sim.cast(spell, target);
                double score = evaluate(sim, team);
                // un sort qui n'améliore rien (soin d'un allié en pleine santé…) gaspille des PA
                if (score <= stay + 1e-9) score -= 1.5;
                candidates.add(new Candidate(new AiAction.Cast(spell, target), score));
            }
        }

        if (me.pm() > 0) {
            for (Cell cell : battle.reach().cells()) {
                Battle sim = battle.simulate();
                sim.move(cell);
                double score = evaluate(sim, team);
                if (!sim.isOver() && sim.active().team() == team) score = Math.max(score, bestCastScore(sim, team));
                candidates.add(new Candidate(new AiAction.Move(cell), score));
            }
        }

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            candidates.set(i, new Candidate(c.action(), c.score() + (random.nextDouble() - 0.5) * difficulty.noise));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int pick = difficulty.topChoices > 1 ? random.nextInt(Math.min(difficulty.topChoices, candidates.size())) : 0;
        AiAction chosen = candidates.get(pick).action();
        // ne jamais choisir au hasard une action pire que de simplement finir le tour
        if (candidates.get(pick).score() < stay - 0.5) chosen = new AiAction.EndTurn();
        return chosen;
    }

    /** Meilleure note atteignable en lançant un seul sort depuis la position actuelle de la simulation. */
    private double bestCastScore(Battle battle, Team team) {
        double best = Double.NEGATIVE_INFINITY;
        Fighter me = battle.active();
        for (Spell spell : me.fighterClass().spells()) {
            if (battle.unavailableReason(spell) != null) continue;
            for (Cell target : targets(battle, me, spell)) {
                if (!battle.canCast(spell, target)) continue;
                Battle sim = battle.simulate();
                sim.cast(spell, target);
                best = Math.max(best, evaluate(sim, team));
            }
        }
        return best;
    }

    /** Cases candidates pour un sort : on n'essaie que celles qui peuvent avoir un intérêt. */
    private static Set<Cell> targets(Battle battle, Fighter me, Spell spell) {
        Set<Cell> cells = new LinkedHashSet<>();
        Cell from = me.position();
        int size = battle.board().size();
        switch (spell.targetMode()) {
            case SELF -> cells.add(from);
            case ALLY -> {
                for (Fighter f : battle.fighters()) if (f.isAlive() && !f.isEnemyOf(me)) cells.add(f.position());
            }
            case ENEMY -> {
                for (Fighter f : battle.fighters()) if (f.isAlive() && f.isEnemyOf(me)) cells.add(f.position());
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        Cell c = new Cell(x, y);
                        if (battle.board().isDestructible(c) && spell.isInRange(from, c)) cells.add(c);
                    }
                }
            }
            case CELL -> {
                for (Fighter f : battle.fighters()) {
                    if (!f.isAlive() || !f.isEnemyOf(me)) continue;
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) cells.add(f.position().plus(dx, dy));
                }
            }
            case LINE, FREE_CELL -> {
                boolean trap = spell.style() == CastStyle.TRAP;
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        Cell c = new Cell(x, y);
                        if (!spell.isInRange(from, c)) continue;
                        if (trap && distanceToEnemy(battle, me, c) > 2) continue;
                        cells.add(c);
                    }
                }
            }
        }
        cells.removeIf(c -> !battle.board().inBounds(c));
        return cells;
    }

    // ------------------------------------------------------------------ évaluation

    /** Note de la situation du point de vue de {@code team} : plus c'est haut, mieux c'est. */
    double evaluate(Battle b, Team team) {
        if (b.isOver()) return b.winner() == team ? WIN_SCORE : -WIN_SCORE;
        double score = 0;
        for (Fighter f : b.fighters()) {
            double sign = f.team() == team ? 1 : -1;
            if (!f.isAlive()) {
                score -= sign * DEAD_PENALTY;
                continue;
            }
            // un PV ennemi vaut d'autant plus que l'ennemi est déjà affaibli (favorise la mise à mort)
            double hpWeight = f.team() == team ? 1.0 : 1.0 + 0.6 * (1 - f.hp() / (double) f.maxHp());
            score += sign * f.hp() * hpWeight;
            for (Status s : f.statuses()) {
                switch (s.kind()) {
                    case POISON, BEES -> score -= sign * s.value() * s.turnsLeft() * 0.8;
                    case SILENCE -> score -= sign * 9;
                    case SHIELD -> score += sign * 4 * difficulty.threatWeight * 10;
                    case STONE_SKIN -> score += sign * (8 + 6 * difficulty.threatWeight * 10);
                    default -> { }
                }
            }
        }
        // sol gelé : chaque ennemi du poseur qui s'y trouve encaissera dégâts et perte de PM à son tour
        for (com.minidofus.model.IceZone z : b.iceZones()) {
            for (Fighter f : b.fighters()) {
                if (!f.isAlive() || !z.owner().isEnemyOf(f) || !z.contains(f.position())) continue;
                double sign = f.team() == team ? 1 : -1;
                score -= sign * (z.damage() + 4 * z.pmLoss());
            }
        }
        for (Fighter ally : b.team(team)) {
            if (!ally.isAlive()) continue;
            int d = distanceToEnemy(b, ally, ally.position());
            score -= difficulty.positionWeight * Math.abs(d - ally.fighterClass().preferredRange());
            if (difficulty.threatWeight > 0) {
                double fragility = 1.5 - ally.hp() / (double) ally.maxHp();
                score -= difficulty.threatWeight * incomingThreat(b, ally) * fragility;
            }
        }
        return score;
    }

    /** Dégâts que les ennemis vivants pourraient infliger à {@code target} à leur prochain tour (estimation). */
    private double incomingThreat(Battle b, Fighter target) {
        double total = 0;
        for (Fighter e : b.fighters()) {
            if (!e.isAlive() || !e.isEnemyOf(target)) continue;
            double[] t = threatOf(e.fighterClass());
            int reach = e.fighterClass().maxPm() + (int) t[1];
            if (e.position().distanceTo(target.position()) <= reach) total += t[0];
        }
        if (target.hasShield()) total *= 0.5;
        if (target.has(com.minidofus.model.StatusKind.STONE_SKIN)) total *= 0.45;
        return total;
    }

    /** [dégâts moyens du meilleur sort offensif, portée maximale offensive] d'une classe. */
    private double[] threatOf(FighterClass c) {
        return threatCache.computeIfAbsent(c, k -> {
            double best = 0;
            int range = 1;
            for (Spell s : k.spells()) {
                IntRange dmg = null;
                if (s instanceof StrikeSpell st) dmg = st.damage();
                else if (s instanceof AreaStrikeSpell a) dmg = a.damage();
                else if (s instanceof ChargeSpell ch) dmg = ch.damage();
                else if (s instanceof GrappleSpell g) dmg = g.damage();
                else if (s instanceof PoisonSpell p) dmg = new IntRange(p.damagePerTurn() * p.turns(), p.damagePerTurn() * p.turns());
                if (dmg == null) continue;
                best = Math.max(best, (dmg.min() + dmg.max()) / 2.0);
                range = Math.max(range, s.maxRange());
            }
            return new double[] {best, range};
        });
    }

    private static int distanceToEnemy(Battle b, Fighter me, Cell from) {
        int best = Integer.MAX_VALUE;
        for (Fighter f : b.fighters()) {
            if (f.isAlive() && f.isEnemyOf(me)) best = Math.min(best, f.position().distanceTo(from));
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }
}
