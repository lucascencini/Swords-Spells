package com.minidofus.model;

import com.minidofus.Check;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.spell.ChargeSpell;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tests des règles : déplacements, sorts, bouclier, soin, victoire. */
public final class BattleTests {

    private BattleTests() { }

    /** Combat sur une grille vide 15x15 ; le guerrier commence. */
    private static Battle battle(Cell warriorPos, Cell magePos, List<BattleEvent> log, long seed) {
        Board board = new Board(15);
        Fighter warrior = new Fighter(FighterCatalog.WARRIOR, warriorPos);
        Fighter mage = new Fighter(FighterCatalog.MAGE, magePos);
        Battle b = new Battle(board, List.of(warrior, mage), new Random(seed));
        b.addListener(log::add);
        b.start(warrior);
        return b;
    }

    private static Spell spell(Fighter f, String id) {
        return f.fighterClass().spells().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static Fighter other(Battle b) {
        return b.opponentOf(b.active());
    }

    public static void run() {
        pathfinding();
        movement();
        melee();
        paRules();
        charge();
        fireball();
        shield();
        heal();
        victory();
        determinism();
    }

    private static void pathfinding() {
        Check.suite("Pathfinder");
        Board board = new Board(5);
        var reach = Pathfinder.compute(board, new Cell(0, 0), 2, c -> false);
        Check.equal(5, reach.cells().size(), "portée 2 depuis un coin : 5 cases");
        Check.equal(2, reach.costTo(new Cell(1, 1)), "coût diagonal = 2");
        Check.equal(-1, reach.costTo(new Cell(3, 3)), "case hors portée inatteignable");
        Check.equal(2, reach.pathTo(new Cell(1, 1)).size(), "chemin de 2 cases");
        Check.equal(new Cell(1, 1), reach.pathTo(new Cell(1, 1)).get(1), "le chemin finit sur la cible");

        board.setObstacle(1, 0, true);
        board.setObstacle(0, 1, true);
        Check.that(Pathfinder.compute(board, new Cell(0, 0), 5, c -> false).cells().isEmpty(), "coin muré : rien d'atteignable");

        Board open = new Board(5);
        var blocked = Pathfinder.compute(open, new Cell(0, 0), 4, c -> c.equals(new Cell(1, 0)));
        Check.equal(4, blocked.costTo(new Cell(2, 0)), "contournement d'une case occupée (coût 4)");
        Check.equal(-1, Pathfinder.compute(open, new Cell(0, 0), 3, c -> c.equals(new Cell(1, 0))).costTo(new Cell(2, 0)),
                "contournement impossible avec 3 PM");

        Board lake = new Board(5);
        lake.setTerrain(1, 0, Terrain.WATER);
        Check.equal(-1, Pathfinder.compute(lake, new Cell(0, 0), 1, c -> false).costTo(new Cell(1, 0)), "l'eau est infranchissable");
    }

    private static void movement() {
        Check.suite("Déplacement");
        List<BattleEvent> log = new ArrayList<>();
        Battle b = battle(new Cell(1, 1), new Cell(13, 13), log, 1);
        Fighter w = b.active();
        Check.equal(3, w.pm(), "PM initiaux du guerrier");
        Check.that(!b.move(new Cell(1, 6)), "case hors de portée refusée");
        Check.equal(3, w.pm(), "PM inchangés après un refus");
        Check.that(b.move(new Cell(3, 2)), "déplacement de 3 cases accepté");
        Check.equal(0, w.pm(), "PM dépensés");
        Check.equal(new Cell(3, 2), w.position(), "position mise à jour");
        Check.that(log.get(log.size() - 1) instanceof BattleEvent.Moved, "événement Moved émis");
        Check.that(!b.move(new Cell(3, 3)), "plus de PM : refusé");

        Battle b2 = battle(new Cell(1, 1), new Cell(2, 1), new ArrayList<>(), 1);
        Check.that(!b2.move(new Cell(2, 1)), "impossible d'aller sur l'adversaire");
        Check.that(b2.move(new Cell(2, 2)), "contournement de l'adversaire");
    }

    private static void melee() {
        Check.suite("Mêlée");
        List<BattleEvent> log = new ArrayList<>();
        Battle b = battle(new Cell(5, 5), new Cell(6, 5), log, 2);
        Fighter w = b.active();
        Fighter m = other(b);
        Spell melee = spell(w, "melee");
        Check.that(b.canCast(melee, m.position()), "mêlée sur un adjacent");
        Check.that(!b.canCast(melee, new Cell(5, 6)), "mêlée sur case vide refusée");
        int before = m.hp();
        Check.that(b.cast(melee, m.position()), "lancer accepté");
        Check.equal(2, w.pa(), "coût de 4 PA");
        Check.inRange(before - m.hp(), 12, 18, "dégâts de mêlée");
        int n = log.size();
        Check.that(log.get(n - 2) instanceof BattleEvent.SpellCast && log.get(n - 1) instanceof BattleEvent.Damaged,
                "ordre des événements : SpellCast puis Damaged");

        Battle far = battle(new Cell(5, 5), new Cell(7, 5), new ArrayList<>(), 2);
        Check.that(!far.canCast(spell(far.active(), "melee"), new Cell(7, 5)), "mêlée à distance 2 refusée");
    }

    private static void paRules() {
        Check.suite("Points d'action");
        Battle b = battle(new Cell(5, 5), new Cell(6, 5), new ArrayList<>(), 3);
        Spell melee = spell(b.active(), "melee");
        Cell target = other(b).position();
        Check.that(b.cast(melee, target), "premier coup");
        Check.that(!b.cast(melee, target), "second coup refusé (2 PA restants, coût 4)");
        Fighter first = b.active();
        b.endTurn();
        Check.that(b.active() != first, "le tour passe à l'adversaire");
        Check.equal(7, b.active().pa(), "PA du mage restaurés");
        b.endTurn();
        Check.equal(6, b.active().pa(), "PA du guerrier restaurés au tour suivant");
        Check.that(b.cast(spell(b.active(), "melee"), other(b).position()), "le guerrier peut rejouer");
    }

    private static void charge() {
        Check.suite("Charge");
        Battle b = battle(new Cell(1, 5), new Cell(5, 5), new ArrayList<>(), 4);
        Fighter w = b.active();
        Fighter m = other(b);
        ChargeSpell charge = (ChargeSpell) spell(w, "charge");
        Check.that(b.canCast(charge, new Cell(5, 5)), "charge sur l'ennemi à 4 cases");
        Check.that(!b.canCast(charge, new Cell(2, 4)), "cible non alignée refusée");
        Check.that(!b.canCast(charge, new Cell(1, 5)), "charge sur soi refusée");
        Check.that(b.cast(charge, new Cell(5, 5)), "charge lancée");
        Check.equal(new Cell(4, 5), w.position(), "arrêt devant l'ennemi");
        Check.inRange(m.maxHp() - m.hp(), 14, 20, "dégâts de charge");
        Check.equal(1, w.pa(), "coût de 5 PA");

        Battle far = battle(new Cell(1, 5), new Cell(8, 5), new ArrayList<>(), 4);
        ChargeSpell c2 = (ChargeSpell) spell(far.active(), "charge");
        Check.that(far.cast(c2, new Cell(3, 5)), "charge vers une case vide");
        Check.equal(new Cell(3, 5), far.fighters().get(0).position(), "parcourt exactement la distance visée");
        Check.equal(60, far.fighters().get(1).hp(), "aucun dégât sans ennemi percuté");

        Battle wall = battle(new Cell(1, 5), new Cell(5, 5), new ArrayList<>(), 4);
        wall.board().setObstacle(2, 5, true);
        Check.that(!wall.canCast(spell(wall.active(), "charge"), new Cell(5, 5)), "obstacle immédiat : charge impossible");

        Battle mid = battle(new Cell(1, 5), new Cell(5, 5), new ArrayList<>(), 4);
        mid.board().setObstacle(3, 5, true);
        var plan = ((ChargeSpell) spell(mid.active(), "charge")).plan(mid, mid.active(), new Cell(5, 5));
        Check.equal(1, plan.path().size(), "arrêt devant un obstacle en cours de route");
        Check.that(plan.hit() == null, "pas d'ennemi percuté derrière un obstacle");

        Battle beyond = battle(new Cell(1, 5), new Cell(3, 5), new ArrayList<>(), 4);
        Check.that(beyond.canCast(spell(beyond.active(), "charge"), new Cell(5, 5)), "viser derrière l'ennemi percute l'ennemi");
    }

    private static void fireball() {
        Check.suite("Boule de feu");
        Battle b = battle(new Cell(5, 5), new Cell(10, 5), new ArrayList<>(), 5);
        b.endTurn();
        Fighter mage = b.active();
        Fighter warrior = other(b);
        Spell fire = spell(mage, "fireball");
        Check.that(b.canCast(fire, new Cell(7, 7)), "case vide à portée ciblable");
        Check.that(!b.canCast(fire, new Cell(20, 5)), "hors grille refusé");
        Check.that(!b.canCast(fire, new Cell(16, 5)), "hors portée refusé");
        Check.that(b.cast(fire, new Cell(7, 7)), "tir dans le vide");
        Check.equal(100, warrior.hp(), "aucun dégât si personne dans la croix");
        Check.that(!b.canCast(fire, new Cell(5, 6)), "3 PA restants : seconde boule de feu refusée");
        b.endTurn();
        b.endTurn();
        Check.that(b.cast(fire, new Cell(5, 6)), "tir sur une case adjacente à l'ennemi");
        Check.inRange(100 - warrior.hp(), 14, 20, "l'ennemi est touché par la croix");

        Battle self = battle(new Cell(5, 7), new Cell(5, 5), new ArrayList<>(), 5);
        self.endTurn();
        Check.that(self.cast(spell(self.active(), "fireball"), new Cell(5, 6)), "tir adjacent au lanceur");
        Check.equal(60, self.active().hp(), "le lanceur n'est jamais touché par sa croix");
        Check.that(other(self).hp() < 100, "l'ennemi de l'autre côté est touché");
    }

    private static void shield() {
        Check.suite("Bouclier");
        List<BattleEvent> log = new ArrayList<>();
        Battle b = battle(new Cell(5, 5), new Cell(6, 5), log, 6);
        Fighter w = b.active();
        Fighter m = other(b);
        Check.that(b.cast(spell(w, "shield"), w.position()), "bouclier sur soi");
        Check.that(!b.canCast(spell(w, "shield"), m.position()), "bouclier sur un autre refusé");
        Check.that(w.hasShield(), "bouclier actif");
        b.endTurn();
        Check.that(w.hasShield(), "toujours actif pendant le tour adverse");
        int before = w.hp();
        Check.that(b.cast(spell(m, "ranged"), w.position()), "le mage attaque le guerrier protégé");
        Check.inRange(before - w.hp(), 4, 6, "dégâts 8-12 réduits de moitié");
        b.endTurn();
        Check.that(!w.hasShield(), "expiré au début du tour suivant du guerrier (1 seul tour adverse couvert)");
        Check.that(log.stream().anyMatch(e -> e instanceof BattleEvent.ShieldChanged s && !s.active()), "événement d'expiration émis");
    }

    private static void heal() {
        Check.suite("Soin");
        List<BattleEvent> log = new ArrayList<>();
        Battle b = battle(new Cell(5, 5), new Cell(6, 5), log, 7);
        b.endTurn();
        Fighter mage = b.active();
        mage.takeDamage(5);
        Check.that(b.cast(spell(mage, "heal"), mage.position()), "soin lancé");
        Check.equal(60, mage.hp(), "les PV ne dépassent pas le maximum");
        BattleEvent.Healed healed = (BattleEvent.Healed) log.get(log.size() - 1);
        Check.equal(5, healed.amount(), "l'événement indique les PV réellement rendus");

        mage.takeDamage(40);
        Check.that(b.cast(spell(mage, "heal"), mage.position()), "second soin");
        Check.inRange(mage.hp(), 20 + 12, 20 + 18, "soin de 12 à 18");
    }

    private static void victory() {
        Check.suite("Victoire");
        List<BattleEvent> log = new ArrayList<>();
        Battle b = battle(new Cell(5, 5), new Cell(6, 5), log, 8);
        Fighter w = b.active();
        Fighter m = other(b);
        m.takeDamage(55);
        Check.that(b.cast(spell(w, "melee"), m.position()), "coup fatal");
        Check.that(b.isOver(), "combat terminé");
        Check.equal(w, b.winner(), "le guerrier gagne");
        Check.equal(0, m.hp(), "PV à zéro, jamais négatifs");
        long ends = log.stream().filter(e -> e instanceof BattleEvent.BattleEnded).count();
        Check.equal(1L, ends, "BattleEnded émis une seule fois");
        Check.that(!b.move(new Cell(5, 6)), "plus de déplacement après la fin");
        Check.that(!b.canCast(spell(w, "shield"), w.position()), "plus de sort après la fin");
        int events = log.size();
        b.endTurn();
        Check.equal(events, log.size(), "endTurn ignoré après la fin");
    }

    private static void determinism() {
        Check.suite("Déterminisme");
        int first = -1;
        for (int run = 0; run < 2; run++) {
            Battle b = battle(new Cell(5, 5), new Cell(6, 5), new ArrayList<>(), 99);
            b.cast(spell(b.active(), "melee"), other(b).position());
            int hp = other(b).hp();
            if (run == 0) first = hp;
            else Check.equal(first, hp, "même graine, mêmes dégâts");
        }
    }
}
