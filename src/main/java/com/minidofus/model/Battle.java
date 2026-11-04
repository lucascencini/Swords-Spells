package com.minidofus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Règles d'un combat à deux : tours, déplacements, lancer de sorts, victoire.
 * Cette classe ne connaît ni l'écran, ni le son, ni l'interface : elle applique les règles
 * immédiatement et publie des {@link BattleEvent}.
 */
public final class Battle implements SpellContext {

    /** Seuil à partir duquel un coup est affiché comme « fort » (purement cosmétique). */
    public static final int HEAVY_HIT_THRESHOLD = 18;

    private final Board board;
    private final List<Fighter> fighters;
    private final Random rng;
    private final List<Consumer<BattleEvent>> listeners = new ArrayList<>();
    private Fighter active;
    private Fighter winner;
    private int turn;
    private int revision;

    public Battle(Board board, List<Fighter> fighters, Random rng) {
        if (fighters.size() != 2) throw new IllegalArgumentException("Un combat oppose exactement 2 combattants");
        this.board = board;
        this.fighters = List.copyOf(fighters);
        this.rng = rng;
    }

    public void addListener(Consumer<BattleEvent> listener) {
        listeners.add(listener);
    }

    /** Démarre le combat ; le premier joueur est tiré au sort. */
    public void start() {
        start(fighters.get(rng.nextInt(fighters.size())));
    }

    public void start(Fighter first) {
        this.active = first;
        this.turn = 1;
        beginTurn();
    }

    // ------------------------------------------------------------------ état

    public Fighter active() { return active; }
    public List<Fighter> fighters() { return Collections.unmodifiableList(fighters); }
    public boolean isOver() { return winner != null; }
    public Fighter winner() { return winner; }
    public int turn() { return turn; }

    /** Compteur incrémenté à chaque changement d'état : permet à l'interface d'invalider ses caches. */
    public int revision() { return revision; }

    @Override public Board board() { return board; }
    @Override public Random rng() { return rng; }

    @Override
    public Fighter fighterAt(Cell cell) {
        for (Fighter f : fighters) {
            if (f.isAlive() && f.position().equals(cell)) return f;
        }
        return null;
    }

    @Override
    public Fighter opponentOf(Fighter fighter) {
        return fighters.get(0) == fighter ? fighters.get(1) : fighters.get(0);
    }

    // ------------------------------------------------------------------ actions du joueur actif

    /** Cases atteignables par le combattant actif avec ses PM restants. */
    public Pathfinder.Reach reach() {
        Fighter mover = active;
        return Pathfinder.compute(board, mover.position(), mover.pm(), c -> {
            Fighter f = fighterAt(c);
            return f != null && f != mover;
        });
    }

    public boolean move(Cell target) {
        if (isOver()) return false;
        Pathfinder.Reach reach = reach();
        int cost = reach.costTo(target);
        if (cost <= 0 || cost > active.pm()) return false;
        List<Cell> path = reach.pathTo(target);
        active.spendPm(cost);
        moveFighter(active, path, MoveStyle.WALK);
        revision++;
        return true;
    }

    public boolean canCast(Spell spell, Cell target) {
        return !isOver()
                && active.fighterClass().spells().contains(spell)
                && active.pa() >= spell.cost()
                && board.inBounds(target)
                && spell.canTarget(this, active, target);
    }

    public boolean cast(Spell spell, Cell target) {
        if (!canCast(spell, target)) return false;
        Fighter caster = active;
        List<Cell> impact = spell.impactCells(this, caster, target);
        caster.spendPa(spell.cost());
        emit(new BattleEvent.SpellCast(caster, spell, target, impact));
        spell.apply(this, caster, target);
        revision++;
        return true;
    }

    public void endTurn() {
        if (isOver()) return;
        active = opponentOf(active);
        turn++;
        beginTurn();
    }

    private void beginTurn() {
        boolean shieldExpired = active.startTurn();
        if (shieldExpired) emit(new BattleEvent.ShieldChanged(active, false));
        emit(new BattleEvent.TurnStarted(active, turn));
        revision++;
    }

    // ------------------------------------------------------------------ SpellContext

    @Override
    public void moveFighter(Fighter fighter, List<Cell> path, MoveStyle style) {
        if (path.isEmpty()) return;
        fighter.moveTo(path.get(path.size() - 1));
        emit(new BattleEvent.Moved(fighter, List.copyOf(path), style));
    }

    @Override
    public void dealDamage(Fighter source, Fighter target, int raw, CastStyle style) {
        int dealt = target.takeDamage(raw);
        emit(new BattleEvent.Damaged(source, target, dealt, target.hp(), dealt >= HEAVY_HIT_THRESHOLD, style));
        if (!target.isAlive() && winner == null) {
            winner = source;
            emit(new BattleEvent.BattleEnded(source));
        }
    }

    @Override
    public void heal(Fighter target, int amount) {
        int healed = target.heal(amount);
        emit(new BattleEvent.Healed(target, healed, target.hp()));
    }

    @Override
    public void applyShield(Fighter target, int percent, int turns) {
        target.applyShield(percent, turns);
        emit(new BattleEvent.ShieldChanged(target, true));
    }

    private void emit(BattleEvent event) {
        for (Consumer<BattleEvent> l : listeners) l.accept(event);
    }
}
