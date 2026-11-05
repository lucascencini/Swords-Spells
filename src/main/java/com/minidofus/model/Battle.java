package com.minidofus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Règles d'un combat entre deux équipes : ordre d'initiative, déplacements, sorts, statuts, pièges,
 * victoire. Cette classe ne connaît ni l'écran, ni le son : elle applique les règles immédiatement et
 * publie des {@link BattleEvent}. Une copie « de simulation » ({@link #simulate()}) permet à l'IA
 * d'essayer des actions sans rien afficher ni tirer de hasard.
 */
public final class Battle implements SpellContext {

    /** Seuil à partir duquel un coup est affiché comme « fort » (purement cosmétique). */
    public static final int HEAVY_HIT_THRESHOLD = 18;
    /** Dégâts de choc par case de poussée non parcourue. */
    public static final int COLLISION_DAMAGE_PER_CELL = 6;
    /** Variation aléatoire ajoutée à l'initiative de base de chaque classe. */
    public static final int INITIATIVE_ROLL = 6;

    private final Board board;
    private final List<Fighter> fighters;
    private final Random rng;
    private final boolean simulation;
    private final List<Consumer<BattleEvent>> listeners = new ArrayList<>();
    private final List<Trap> traps = new ArrayList<>();
    private List<Fighter> turnOrder = List.of();
    private int activeIndex;
    private int round;
    private int turnsPlayed;
    private Team winner;
    private int revision;

    public Battle(Board board, List<Fighter> fighters, Random rng) {
        this(board, fighters, rng, false);
    }

    private Battle(Board board, List<Fighter> fighters, Random rng, boolean simulation) {
        boolean red = fighters.stream().anyMatch(f -> f.team() == Team.RED);
        boolean blue = fighters.stream().anyMatch(f -> f.team() == Team.BLUE);
        if (!red || !blue) throw new IllegalArgumentException("Il faut au moins un combattant dans chaque équipe");
        this.board = board;
        this.fighters = List.copyOf(fighters);
        this.rng = rng;
        this.simulation = simulation;
    }

    public void addListener(Consumer<BattleEvent> listener) {
        listeners.add(listener);
    }

    /** Démarre le combat : tirage de l'initiative puis ordre de jeu équipes alternées. */
    public void start() {
        for (Fighter f : fighters) f.setInitiative(f.fighterClass().initiative() + rng.nextInt(INITIATIVE_ROLL));
        startWithOrder(computeTurnOrder(fighters));
    }

    /** Démarre avec un ordre imposé (tests). */
    public void startWithOrder(List<Fighter> order) {
        this.turnOrder = List.copyOf(order);
        this.activeIndex = 0;
        this.round = 1;
        emit(new BattleEvent.TurnOrderSet(turnOrder));
        beginTurn();
    }

    /**
     * Chaque équipe est triée par initiative décroissante, puis les équipes jouent en alternance ;
     * l'équipe du combattant le plus rapide commence.
     */
    public static List<Fighter> computeTurnOrder(List<Fighter> fighters) {
        Comparator<Fighter> byInitiative = Comparator.comparingInt(Fighter::initiative).reversed()
                .thenComparingInt(Fighter::slot);
        List<Fighter> red = fighters.stream().filter(f -> f.team() == Team.RED).sorted(byInitiative).toList();
        List<Fighter> blue = fighters.stream().filter(f -> f.team() == Team.BLUE).sorted(byInitiative).toList();
        boolean redFirst = red.get(0).initiative() >= blue.get(0).initiative();
        List<Fighter> first = redFirst ? red : blue;
        List<Fighter> second = redFirst ? blue : red;
        List<Fighter> order = new ArrayList<>();
        for (int i = 0; i < Math.max(first.size(), second.size()); i++) {
            if (i < first.size()) order.add(first.get(i));
            if (i < second.size()) order.add(second.get(i));
        }
        return order;
    }

    // ------------------------------------------------------------------ état

    public Fighter active() { return turnOrder.get(activeIndex); }
    public List<Fighter> fighters() { return fighters; }
    public List<Fighter> turnOrder() { return turnOrder; }
    public int activeIndex() { return activeIndex; }
    public List<Trap> traps() { return Collections.unmodifiableList(traps); }
    public boolean isOver() { return winner != null; }
    public Team winner() { return winner; }
    public int round() { return round; }
    public int turnsPlayed() { return turnsPlayed; }

    /** Compteur incrémenté à chaque changement d'état : permet à l'interface d'invalider ses caches. */
    public int revision() { return revision; }

    public List<Fighter> team(Team team) {
        return fighters.stream().filter(f -> f.team() == team).toList();
    }

    public boolean isTeamAlive(Team team) {
        for (Fighter f : fighters) if (f.team() == team && f.isAlive()) return true;
        return false;
    }

    @Override public Board board() { return board; }

    @Override
    public Fighter fighterAt(Cell cell) {
        for (Fighter f : fighters) {
            if (f.isAlive() && f.position().equals(cell)) return f;
        }
        return null;
    }

    @Override
    public Trap trapAt(Cell cell) {
        for (Trap t : traps) if (t.cell().equals(cell)) return t;
        return null;
    }

    @Override
    public boolean hasLineOfSight(Cell from, Cell to) {
        return LineOfSight.isClear(from, to, c -> board.inBounds(c) && (board.blocksSight(c) || fighterAt(c) != null));
    }

    @Override
    public int roll(IntRange range) {
        return simulation ? (range.min() + range.max() + 1) / 2 : range.roll(rng);
    }

    // ------------------------------------------------------------------ actions du joueur actif

    /** Cases atteignables par le combattant actif avec ses PM restants. */
    public Pathfinder.Reach reach() {
        Fighter mover = active();
        return Pathfinder.compute(board, mover.position(), mover.pm(), c -> {
            Fighter f = fighterAt(c);
            return f != null && f != mover;
        });
    }

    public boolean move(Cell target) {
        if (isOver()) return false;
        Fighter mover = active();
        Pathfinder.Reach reach = reach();
        int cost = reach.costTo(target);
        if (cost <= 0 || cost > mover.pm()) return false;
        int steps = moveFighter(mover, reach.pathTo(target), MoveStyle.WALK);
        mover.spendPm(steps);
        mover.stats().addCells(steps);
        revision++;
        endTurnIfActiveDied();
        return true;
    }

    /** Raison pour laquelle un sort n'est pas lançable en ce moment (indépendamment de la cible), ou {@code null}. */
    public String unavailableReason(Spell spell) {
        Fighter f = active();
        if (isOver()) return "Combat terminé";
        if (f.isSilenced()) return "Réduit au silence";
        if (f.cooldown(spell) > 0) return "Relance dans " + f.cooldown(spell) + " tour" + (f.cooldown(spell) > 1 ? "s" : "");
        if (f.pa() < spell.cost()) return "PA insuffisants";
        return null;
    }

    public boolean canCast(Spell spell, Cell target) {
        return active().fighterClass().spells().contains(spell)
                && unavailableReason(spell) == null
                && board.inBounds(target)
                && spell.canTarget(this, active(), target);
    }

    public boolean cast(Spell spell, Cell target) {
        if (!canCast(spell, target)) return false;
        Fighter caster = active();
        List<Cell> impact = spell.impactCells(this, caster, target);
        caster.spendPa(spell.cost());
        caster.startCooldown(spell);
        caster.stats().addSpell();
        emit(new BattleEvent.SpellCast(caster, spell, target, impact));
        spell.apply(this, caster, target);
        revision++;
        endTurnIfActiveDied();
        return true;
    }

    public void endTurn() {
        if (isOver()) return;
        Fighter f = active();
        for (StatusKind expired : f.endTurn()) {
            emit(new BattleEvent.StatusChanged(f, expired, false, 0));
            if (expired == StatusKind.SILENCE) {
                f.addStatus(new Status(StatusKind.SILENCE_IMMUNITY, 1, 0, null));
                emit(new BattleEvent.StatusChanged(f, StatusKind.SILENCE_IMMUNITY, true, 1));
            }
        }
        emit(new BattleEvent.TurnEnded(f));
        turnsPlayed++;
        advance();
        beginTurn();
    }

    private void endTurnIfActiveDied() {
        if (!isOver() && !active().isAlive()) endTurn();
    }

    private void advance() {
        activeIndex = (activeIndex + 1) % turnOrder.size();
        if (activeIndex == 0) round++;
    }

    private void beginTurn() {
        for (int guard = 0; guard < turnOrder.size() * 2 && !isOver(); guard++) {
            Fighter f = active();
            if (f.isAlive()) {
                Status poison = f.status(StatusKind.POISON);
                if (poison != null) dealDamage(poison.source(), f, poison.value(), CastStyle.POISON_TICK);
            }
            if (isOver()) break;
            if (!f.isAlive()) {
                advance();
                continue;
            }
            for (StatusKind expired : f.startTurn()) emit(new BattleEvent.StatusChanged(f, expired, false, 0));
            emit(new BattleEvent.TurnStarted(f, round));
            break;
        }
        revision++;
    }

    /**
     * Applique une commande du combattant actif. Toutes les vérifications sont faites ici : une commande
     * invalide (case hors de portée, sort indisponible, rang de sort inconnu…) est refusée sans effet.
     */
    public boolean apply(Command command) {
        if (isOver() || command == null) return false;
        if (command instanceof Command.Move m) return move(new Cell(m.x(), m.y()));
        if (command instanceof Command.Cast c) {
            List<Spell> spells = active().fighterClass().spells();
            if (c.spell() < 0 || c.spell() >= spells.size()) return false;
            return cast(spells.get(c.spell()), new Cell(c.x(), c.y()));
        }
        endTurn();
        return true;
    }

    /** Commande équivalente à un déplacement ou un sort, pour l'envoyer par le réseau. */
    public Command toCommand(Spell spell, Cell target) {
        return new Command.Cast(active().fighterClass().spells().indexOf(spell), target.x(), target.y());
    }

    /**
     * Empreinte de l'état (positions, PV, PA, PM, statuts, relances, obstacles, pièges, tour). Deux combats
     * synchronisés ont la même ; une différence signale une désynchronisation.
     */
    public long stateHash() {
        long h = 1125899906842597L;
        h = 31 * h + turnsPlayed;
        h = 31 * h + activeIndex;
        h = 31 * h + (winner == null ? -1 : winner.ordinal());
        for (Fighter f : fighters) {
            h = 31 * h + f.position().x();
            h = 31 * h + f.position().y();
            h = 31 * h + f.hp();
            h = 31 * h + f.pa();
            h = 31 * h + f.pm();
            for (Status s : f.statuses()) {
                h = 31 * h + s.kind().ordinal();
                h = 31 * h + s.turnsLeft();
            }
            for (Spell s : f.fighterClass().spells()) h = 31 * h + f.cooldown(s);
        }
        for (int i = 0; i < board.size() * board.size(); i++) {
            Cell c = board.cellAt(i);
            h = 31 * h + (board.hasObstacle(c) ? 1 + board.obstacleHp(c) : 0);
        }
        for (Trap t : traps) {
            h = 31 * h + t.cell().x() * 97 + t.cell().y();
            h = 31 * h + fighters.indexOf(t.owner());
        }
        return h;
    }

    // ------------------------------------------------------------------ SpellContext

    @Override
    public int moveFighter(Fighter fighter, List<Cell> path, MoveStyle style) {
        if (path.isEmpty()) return 0;
        List<Cell> travelled = new ArrayList<>();
        Trap sprung = null;
        for (Cell c : path) {
            travelled.add(c);
            Trap t = trapAt(c);
            if (t != null && t.owner().isEnemyOf(fighter)) {
                sprung = t;
                break;
            }
        }
        fighter.moveTo(travelled.get(travelled.size() - 1));
        emit(new BattleEvent.Moved(fighter, List.copyOf(travelled), style));
        if (sprung != null) spring(sprung, fighter);
        return travelled.size();
    }

    @Override
    public void teleport(Fighter fighter, Cell to, MoveStyle style) {
        moveFighter(fighter, List.of(to), style);
    }

    private void spring(Trap trap, Fighter victim) {
        traps.remove(trap);
        emit(new BattleEvent.TrapTriggered(trap, victim));
        dealDamage(trap.owner(), victim, roll(trap.damage()), CastStyle.TRAP_TRIGGER);
    }

    @Override
    public void push(Fighter source, Fighter target, int dx, int dy, int distance) {
        List<Cell> path = new ArrayList<>();
        Cell current = target.position();
        int remaining = 0;
        Cell blockedBy = null;
        for (int i = 0; i < distance; i++) {
            Cell next = current.plus(dx, dy);
            if (!board.isWalkable(next) || fighterAt(next) != null) {
                remaining = distance - i;
                blockedBy = next;
                break;
            }
            path.add(next);
            current = next;
        }
        int moved = moveFighter(target, path, MoveStyle.PUSHED);
        if (moved < path.size() || remaining == 0 || !target.isAlive()) return; // arrêté par un piège, ou pas de choc
        int damage = COLLISION_DAMAGE_PER_CELL * remaining;
        dealDamage(source, target, damage, CastStyle.COLLISION);
        Fighter other = fighterAt(blockedBy);
        if (other != null) dealDamage(source, other, damage / 2, CastStyle.COLLISION);
        else if (board.isDestructible(blockedBy)) damageObstacle(source, blockedBy, damage, CastStyle.COLLISION);
    }

    @Override
    public void pull(Fighter source, Fighter target, int distance) {
        int dx = Integer.signum(source.position().x() - target.position().x());
        int dy = Integer.signum(source.position().y() - target.position().y());
        if (dx != 0 && dy != 0) return; // seulement en ligne droite
        List<Cell> path = new ArrayList<>();
        Cell current = target.position();
        for (int i = 0; i < distance; i++) {
            Cell next = current.plus(dx, dy);
            if (!board.isWalkable(next) || fighterAt(next) != null) break;
            path.add(next);
            current = next;
        }
        moveFighter(target, path, MoveStyle.PULLED);
    }

    @Override
    public void dealDamage(Fighter source, Fighter target, int raw, CastStyle style) {
        if (!target.isAlive() || isOver()) return;
        int dealt = target.takeDamage(raw);
        target.stats().addDamageTaken(dealt);
        if (source != null && source.isEnemyOf(target)) source.stats().addDamageDealt(dealt);
        emit(new BattleEvent.Damaged(source, target, dealt, target.hp(), dealt >= HEAVY_HIT_THRESHOLD, style));
        if (!target.isAlive()) {
            if (source != null && source.isEnemyOf(target)) source.stats().addKill();
            traps.removeIf(t -> t.owner() == target);
            emit(new BattleEvent.Died(target, source));
            if (!isTeamAlive(target.team())) {
                winner = target.team().opposite();
                emit(new BattleEvent.BattleEnded(winner));
            }
        }
    }

    @Override
    public void damageObstacle(Fighter source, Cell cell, int raw, CastStyle style) {
        int dealt = board.damageObstacle(cell, raw);
        if (dealt == 0) return;
        int left = board.obstacleHp(cell);
        emit(new BattleEvent.ObstacleDamaged(cell, dealt, left, style));
        if (!board.hasObstacle(cell)) {
            if (source != null) source.stats().addObstacleDestroyed();
            emit(new BattleEvent.ObstacleDestroyed(cell));
        }
    }

    @Override
    public void heal(Fighter source, Fighter target, int amount) {
        if (!target.isAlive()) return;
        int healed = target.heal(amount);
        if (source != null) source.stats().addHealing(healed);
        emit(new BattleEvent.Healed(source, target, healed, target.hp()));
    }

    @Override
    public void addStatus(Fighter source, Fighter target, StatusKind kind, int turns, int value) {
        if (!target.isAlive()) return;
        if (kind == StatusKind.SILENCE && target.has(StatusKind.SILENCE_IMMUNITY)) return;
        target.addStatus(new Status(kind, turns, value, source));
        emit(new BattleEvent.StatusChanged(target, kind, true, turns));
    }

    @Override
    public void placeTrap(Fighter owner, Cell cell, IntRange damage, int maxPerOwner) {
        List<Trap> own = traps.stream().filter(t -> t.owner() == owner).toList();
        if (own.size() >= maxPerOwner) {
            Trap oldest = own.get(0);
            traps.remove(oldest);
            emit(new BattleEvent.TrapRemoved(oldest));
        }
        Trap trap = new Trap(cell, owner, damage);
        traps.add(trap);
        emit(new BattleEvent.TrapPlaced(trap));
    }

    private void emit(BattleEvent event) {
        for (Consumer<BattleEvent> l : listeners) l.accept(event);
    }

    // ------------------------------------------------------------------ simulation (IA)

    /**
     * Copie indépendante du combat, sans écouteurs, où chaque tirage vaut la moyenne de son intervalle.
     * Les combattants de la copie sont distincts : utiliser {@link #counterpart} pour les retrouver.
     */
    public Battle simulate() {
        Map<Fighter, Fighter> mapping = new IdentityHashMap<>();
        List<Fighter> copies = new ArrayList<>();
        for (Fighter f : fighters) {
            Fighter c = f.copy();
            mapping.put(f, c);
            copies.add(c);
        }
        for (Fighter f : fighters) mapping.get(f).copyStatusesFrom(f, mapping);
        Battle sim = new Battle(board.copy(), copies, new Random(0), true);
        sim.turnOrder = turnOrder.stream().map(mapping::get).toList();
        sim.activeIndex = activeIndex;
        sim.round = round;
        sim.turnsPlayed = turnsPlayed;
        sim.winner = winner;
        for (Trap t : traps) sim.traps.add(new Trap(t.cell(), mapping.get(t.owner()), t.damage()));
        return sim;
    }

    /** Combattant de ce combat qui occupe la même place que {@code other} dans un autre combat (copie). */
    public Fighter counterpart(Battle other, Fighter otherFighter) {
        return fighters.get(other.fighters.indexOf(otherFighter));
    }
}
