package com.minidofus.model;

import com.minidofus.Check;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.spell.ChargeSpell;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tests des règles : déplacements, sorts, statuts, relances, initiative, pièges, obstacles, victoire. */
public final class BattleTests {

    private BattleTests() { }

    /** Petit montage de combat : combattants placés à la main, ordre de jeu imposé (dans l'ordre donné). */
    private static final class Setup {
        final Board board = new Board(15);
        final List<Fighter> fighters = new ArrayList<>();
        final List<BattleEvent> log = new ArrayList<>();
        Battle battle;

        Fighter add(FighterClass c, Team team, int x, int y) {
            Fighter f = new Fighter(c, team, (int) fighters.stream().filter(o -> o.team() == team).count(), new Cell(x, y));
            fighters.add(f);
            return f;
        }

        Battle start(long seed) {
            battle = new Battle(board, fighters, new Random(seed));
            battle.addListener(log::add);
            battle.startWithOrder(fighters);
            return battle;
        }

        long count(Class<? extends BattleEvent> type) {
            return log.stream().filter(type::isInstance).count();
        }
    }

    private static Spell spell(Fighter f, String id) {
        return f.fighterClass().spells().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    public static void run() {
        pathfinding();
        lineOfSight();
        movement();
        stunStrike();
        paAndCooldowns();
        charge();
        pushAndCollision();
        grapple();
        fireball();
        heal();
        iceZone();
        teleportAndLeap();
        poison();
        traps();
        destructibles();
        shield();
        initiative();
        teamVictory();
        druid();
        simulation();
    }

    private static void pathfinding() {
        Check.suite("Pathfinder");
        Board board = new Board(5);
        var reach = Pathfinder.compute(board, new Cell(0, 0), 2, c -> false);
        Check.equal(5, reach.cells().size(), "portée 2 depuis un coin : 5 cases");
        Check.equal(2, reach.costTo(new Cell(1, 1)), "coût diagonal = 2");
        Check.equal(-1, reach.costTo(new Cell(3, 3)), "case hors portée inatteignable");
        Check.equal(new Cell(1, 1), reach.pathTo(new Cell(1, 1)).get(1), "le chemin finit sur la cible");
        Board open = new Board(5);
        Check.equal(4, Pathfinder.compute(open, new Cell(0, 0), 4, c -> c.equals(new Cell(1, 0))).costTo(new Cell(2, 0)),
                "contournement d'une case occupée (coût 4)");
        Board lake = new Board(5);
        lake.setTerrain(1, 0, Terrain.WATER);
        Check.equal(-1, Pathfinder.compute(lake, new Cell(0, 0), 1, c -> false).costTo(new Cell(1, 0)), "l'eau est infranchissable");
    }

    private static void lineOfSight() {
        Check.suite("Ligne de vue");
        Board b = new Board(15);
        b.setObstacle(5, 5, true);
        Check.that(!LineOfSight.isClear(new Cell(3, 5), new Cell(7, 5), b::blocksSight), "obstacle en ligne droite : bloqué");
        Check.that(LineOfSight.isClear(new Cell(3, 5), new Cell(5, 5), b::blocksSight), "viser l'obstacle lui-même : visible");
        Check.that(LineOfSight.isClear(new Cell(3, 4), new Cell(7, 4), b::blocksSight), "ligne voisine : libre");
        Check.that(!LineOfSight.isClear(new Cell(3, 3), new Cell(7, 7), b::blocksSight), "diagonale passant par l'obstacle : bloquée");
        b.setObstacle(6, 5, true);
        Check.that(LineOfSight.isClear(new Cell(5, 4), new Cell(7, 6), c -> c.equals(new Cell(6, 4))),
                "coin touché par une seule case bloquante : visible (règle permissive)");
        Check.that(!LineOfSight.isClear(new Cell(5, 4), new Cell(7, 6), c -> c.equals(new Cell(6, 4)) || c.equals(new Cell(5, 5))),
                "coin entre deux cases bloquantes : bloqué");
        Board water = new Board(15);
        water.setTerrain(5, 5, Terrain.WATER);
        Check.that(LineOfSight.isClear(new Cell(3, 5), new Cell(7, 5), water::blocksSight), "l'eau ne bloque pas la vue");

        Setup s = new Setup();
        Fighter mage = s.add(FighterCatalog.MAGE, Team.RED, 2, 5);
        Fighter warrior = s.add(FighterCatalog.WARRIOR, Team.BLUE, 6, 5);
        s.add(FighterCatalog.WARRIOR, Team.BLUE, 4, 5);
        Battle battle = s.start(1);
        Check.that(!battle.canCast(spell(mage, "arcane"), warrior.position()), "un combattant bloque la ligne de vue");
        Check.that(battle.canCast(spell(mage, "arcane"), new Cell(4, 5)), "le combattant devant est visible");
        Check.that(!battle.canCast(spell(mage, "fireball"), new Cell(6, 6)) || battle.hasLineOfSight(mage.position(), new Cell(6, 6)),
                "zone : la ligne de vue vers le centre est exigée");
    }

    private static void movement() {
        Check.suite("Déplacement");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 1, 1);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 2, 1);
        Battle b = s.start(1);
        Check.that(!b.move(new Cell(1, 6)), "case hors de portée refusée");
        Check.that(!b.move(m.position()), "impossible d'aller sur un combattant");
        Check.that(b.move(new Cell(2, 2)), "contournement d'un combattant");
        Check.equal(1, w.pm(), "2 PM dépensés");
        Check.equal(2, w.stats().cellsMoved(), "statistique de déplacement");
    }

    private static void stunStrike() {
        Check.suite("Frappe assommante (silence)");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Battle b = s.start(2);
        Spell stun = spell(w, "stun");
        Check.that(!b.canCast(stun, new Cell(5, 6)), "case vide refusée");
        Check.that(b.cast(stun, m.position()), "frappe lancée");
        Check.inRange(60 - m.hp(), 11, 16, "dégâts 11-16");
        Check.that(m.isSilenced(), "cible réduite au silence");
        b.endTurn();
        Check.equal(m, b.active(), "tour du mage");
        Check.equal("Réduit au silence", b.unavailableReason(spell(m, "arcane")), "le mage ne peut lancer aucun sort");
        Check.that(b.move(new Cell(7, 5)), "mais il peut se déplacer");
        b.endTurn();
        Check.that(!m.isSilenced(), "silence levé à la fin de son tour");
        Check.that(m.has(StatusKind.SILENCE_IMMUNITY), "immunité accordée");
        Check.that(b.move(new Cell(6, 5)), "le guerrier revient au contact");
        Check.that(b.cast(stun, m.position()), "nouvelle frappe");
        Check.that(!m.isSilenced(), "immunisé : pas de silence deux tours de suite");
    }

    private static void paAndCooldowns() {
        Check.suite("PA et relances");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Battle b = s.start(3);
        Spell push = spell(w, "push");
        Check.that(b.cast(push, m.position()), "Repousser lancé (3 PA)");
        Check.equal("Relance dans 1 tour", b.unavailableReason(push), "relance de 1 tour affichée");
        Check.equal(3, w.pa(), "3 PA restants");
        b.endTurn();
        b.endTurn();
        Check.equal(null, b.unavailableReason(push), "de nouveau disponible au tour suivant");
        Check.equal(6, w.pa(), "PA restaurés");
        Spell shield = spell(w, "shield");
        b.cast(shield, w.position());
        b.endTurn();
        b.endTurn();
        Check.that(b.unavailableReason(shield) != null, "bouclier (relance 2) indisponible au tour suivant");
        b.endTurn();
        b.endTurn();
        Check.equal(null, b.unavailableReason(shield), "disponible deux tours plus tard");
    }

    private static void charge() {
        Check.suite("Charge");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 1, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 5, 5);
        Battle b = s.start(4);
        ChargeSpell charge = (ChargeSpell) spell(w, "charge");
        Check.that(!b.canCast(charge, new Cell(2, 4)), "cible non alignée refusée");
        Check.that(b.cast(charge, m.position()), "charge lancée");
        Check.equal(new Cell(4, 5), w.position(), "arrêt devant l'ennemi");
        Check.inRange(60 - m.hp(), 14, 20, "dégâts de charge");

        Setup s2 = new Setup();
        Fighter w2 = s2.add(FighterCatalog.WARRIOR, Team.RED, 1, 5);
        s2.add(FighterCatalog.WARRIOR, Team.RED, 3, 5);
        Fighter e2 = s2.add(FighterCatalog.MAGE, Team.BLUE, 5, 5);
        Battle b2 = s2.start(4);
        Check.that(!b2.canCast(spell(w2, "charge"), e2.position()) || ((ChargeSpell) spell(w2, "charge")).plan(b2, w2, e2.position()).hitFighter() == null,
                "jamais de dégâts sur un allié percuté");

        Setup s3 = new Setup();
        Fighter w3 = s3.add(FighterCatalog.WARRIOR, Team.RED, 1, 5);
        s3.add(FighterCatalog.MAGE, Team.BLUE, 10, 10);
        s3.board.setObstacle(4, 5, true, 24);
        Battle b3 = s3.start(4);
        Check.that(b3.cast(spell(w3, "charge"), new Cell(5, 5)), "charge vers une caisse");
        Check.equal(new Cell(3, 5), w3.position(), "arrêt devant la caisse");
        Check.that(s3.board.obstacleHp(new Cell(4, 5)) < 24, "la caisse est abîmée");
    }

    private static void pushAndCollision() {
        Check.suite("Poussée et chocs");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Battle b = s.start(5);
        b.cast(spell(w, "push"), m.position());
        Check.equal(new Cell(8, 5), m.position(), "repoussé de 2 cases");
        Check.inRange(60 - m.hp(), 6, 9, "dégâts sans choc");

        Setup s2 = new Setup();
        Fighter w2 = s2.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m2 = s2.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        s2.board.setObstacle(7, 5, true);
        Battle b2 = s2.start(5);
        b2.cast(spell(w2, "push"), m2.position());
        Check.equal(new Cell(6, 5), m2.position(), "poussé contre un rocher : ne bouge pas");
        Check.inRange(60 - m2.hp(), 6 + 12, 9 + 12, "choc : 6 dégâts par case non parcourue (2)");
        Check.that(s2.count(BattleEvent.Damaged.class) == 2, "deux événements de dégâts (coup + choc)");

        Setup s3 = new Setup();
        Fighter w3 = s3.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m3 = s3.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Fighter a3 = s3.add(FighterCatalog.ARCHER, Team.BLUE, 8, 5);
        Battle b3 = s3.start(5);
        b3.cast(spell(w3, "push"), m3.position());
        Check.equal(new Cell(7, 5), m3.position(), "s'arrête contre un autre combattant");
        Check.equal(75 - 3, a3.hp(), "le combattant percuté prend la moitié du choc (6 / 2)");

        Setup s4 = new Setup();
        Fighter w4 = s4.add(FighterCatalog.WARRIOR, Team.RED, 12, 5);
        Fighter m4 = s4.add(FighterCatalog.MAGE, Team.BLUE, 13, 5);
        Battle b4 = s4.start(5);
        b4.cast(spell(w4, "push"), m4.position());
        Check.equal(new Cell(14, 5), m4.position(), "bord de carte : 1 case parcourue");
        Check.inRange(60 - m4.hp(), 6 + 6, 9 + 6, "puis choc contre le bord");
    }

    private static void grapple() {
        Check.suite("Grappin");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 2, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 7, 5);
        Battle b = s.start(6);
        Spell grapple = spell(w, "grapple");
        Check.that(!b.canCast(grapple, new Cell(7, 6)), "cible non alignée refusée");
        Check.that(b.cast(grapple, m.position()), "grappin lancé");
        Check.equal(new Cell(4, 5), m.position(), "attiré de 3 cases");
        Setup s2 = new Setup();
        Fighter w2 = s2.add(FighterCatalog.WARRIOR, Team.RED, 2, 5);
        Fighter m2 = s2.add(FighterCatalog.MAGE, Team.BLUE, 4, 5);
        Battle b2 = s2.start(6);
        b2.cast(spell(w2, "grapple"), m2.position());
        Check.equal(new Cell(3, 5), m2.position(), "s'arrête au contact du lanceur");
    }

    private static void fireball() {
        Check.suite("Boule de feu (zone, tir allié)");
        Setup s = new Setup();
        Fighter m = s.add(FighterCatalog.MAGE, Team.RED, 5, 9);
        Fighter ally = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 4);
        Fighter enemy = s.add(FighterCatalog.WARRIOR, Team.BLUE, 5, 5);
        Battle b = s.start(7);
        Check.that(b.cast(spell(m, "fireball"), new Cell(5, 5)), "boule de feu lancée");
        Check.that(enemy.hp() < 100, "l'ennemi au centre est touché");
        Check.that(ally.hp() < 100, "l'allié dans la croix aussi");
        Check.equal(60, m.hp(), "le lanceur n'est jamais touché");
        Check.equal(enemy.maxHp() - enemy.hp(), m.stats().damageDealt(), "seuls les dégâts aux ennemis comptent dans les stats");
        Check.that(!b.canCast(spell(m, "fireball"), new Cell(5, 6)), "relance 1 : une seule par tour");
    }

    private static void heal() {
        Check.suite("Bouclier de pierre");
        Setup s = new Setup();
        Fighter m = s.add(FighterCatalog.MAGE, Team.RED, 5, 5);
        Fighter ally = s.add(FighterCatalog.ARCHER, Team.RED, 7, 5);
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.BLUE, 8, 5);
        Battle b = s.start(8);
        Check.that(!b.canCast(spell(m, "stoneshield"), w.position()), "pas de bouclier de pierre sur un ennemi");
        Check.that(b.cast(spell(m, "stoneshield"), ally.position()), "bouclier de pierre sur l'Archère");
        b.endTurn();
        b.endTurn();
        Check.that(b.cast(spell(w, "stun"), ally.position()), "le Guerrier frappe l'Archère protégée");
        Check.equal(75, ally.hp(), "attaque entièrement bloquée");
        Check.that(!ally.has(StatusKind.STONE_SKIN), "le bouclier s'est brisé");
        Check.equal(1L, s.count(BattleEvent.Blocked.class), "événement de blocage");
        b.endTurn();
        b.endTurn();
        b.endTurn();
        Check.that(b.cast(spell(w, "stun"), ally.position()), "seconde attaque, au tour suivant");
        Check.that(ally.hp() < 75, "la seconde attaque passe");
    }

    private static void iceZone() {
        Check.suite("Zone de glace");
        Setup s = new Setup();
        Fighter m = s.add(FighterCatalog.MAGE, Team.RED, 2, 5);
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.BLUE, 6, 5);
        Fighter a = s.add(FighterCatalog.ARCHER, Team.RED, 6, 6);
        Battle b = s.start(9);
        Check.that(b.cast(spell(m, "icezone"), new Cell(6, 5)), "zone de glace lancée");
        Check.inRange(100 - w.hp(), 8, 11, "dégâts immédiats sur l'ennemi");
        Check.equal(75, a.hp(), "l'allié dans la zone n'est pas touché");
        int before = w.hp();
        b.endTurn();
        Check.equal(before - 6, w.hp(), "6 dégâts au début du tour sur le sol gelé");
        Check.equal(w.fighterClass().maxPm() - 2, w.pm(), "2 PM de moins ce tour-là");
        Check.equal(1, b.iceZones().size(), "la glace persiste");
        b.endTurn();                                    // Archère
        b.endTurn();                                    // Mage : 1er tour écoulé
        Check.equal(1, b.iceZones().size(), "encore là au tour suivant du Mage");
        b.endTurn();
        b.endTurn();
        b.endTurn();                                    // Mage : 2e tour, la glace fond
        Check.equal(0, b.iceZones().size(), "la glace fond après 2 tours du Mage");
        Check.equal(1L, s.count(BattleEvent.IceZoneMelted.class), "événement de fonte");
    }

    private static void teleportAndLeap() {
        Check.suite("Téléportation et bond");
        Setup s = new Setup();
        Fighter m = s.add(FighterCatalog.MAGE, Team.RED, 5, 5);
        Fighter e = s.add(FighterCatalog.WARRIOR, Team.BLUE, 6, 5);
        s.board.setObstacle(5, 6, true);
        Battle b = s.start(9);
        Spell tp = spell(m, "teleport");
        Check.that(!b.canCast(tp, e.position()), "pas sur une case occupée");
        Check.that(!b.canCast(tp, new Cell(5, 6)), "pas sur un obstacle");
        Check.that(b.cast(tp, new Cell(5, 8)), "téléportation par-dessus un obstacle");
        Check.equal(new Cell(5, 8), m.position(), "nouvelle position");

        Setup s2 = new Setup();
        Fighter a = s2.add(FighterCatalog.ARCHER, Team.RED, 5, 5);
        s2.add(FighterCatalog.WARRIOR, Team.BLUE, 12, 12);
        s2.board.setObstacle(6, 5, true);
        Battle b2 = s2.start(9);
        Spell leap = spell(a, "leap");
        Check.that(!b2.canCast(leap, new Cell(6, 6)), "bond : en ligne droite seulement");
        Check.that(b2.cast(leap, new Cell(7, 5)), "bond par-dessus un rocher");
        Check.equal(new Cell(7, 5), a.position(), "atterrissage");
    }

    private static void poison() {
        Check.suite("Dégâts sur la durée (nuée d'abeilles)");
        Setup s = new Setup();
        Fighter m = s.add(FighterCatalog.DRUID, Team.RED, 2, 5);
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.BLUE, 6, 5);
        Battle b = s.start(10);
        Check.that(b.cast(spell(m, "bees"), w.position()), "nuée lancée");
        Check.equal(100, w.hp(), "pas de dégâts immédiats");
        for (int i = 0; i < 3; i++) {
            b.endTurn();
            Check.equal(100 - 6 * (i + 1), w.hp(), "6 dégâts au début du tour " + (i + 1) + " de la cible");
            b.endTurn();
        }
        b.endTurn();
        Check.equal(82, w.hp(), "plus de dégâts après 3 tours");
        Check.equal(18, m.stats().damageDealt(), "dégâts sur la durée attribués au lanceur");
    }

    private static void druid() {
        Check.suite("Druide");
        // corbeau : ligne de vue nécessaire ; éclair : tombe du ciel, pas besoin de ligne de vue
        Setup s = new Setup();
        Fighter d = s.add(FighterCatalog.DRUID, Team.RED, 2, 5);
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.BLUE, 6, 5);
        s.board.setObstacle(4, 5, true);
        Battle b = s.start(3);
        Check.that(!b.canCast(spell(d, "crow"), w.position()), "corbeau bloqué par un rocher (ligne de vue)");
        Check.that(b.cast(spell(d, "lightning"), w.position()), "éclair lancé par-dessus le rocher");
        Check.inRange(100 - w.hp(), 18, 24, "éclair : 18-24 dégâts");

        // nuée d'abeilles : statut propre
        Setup s2 = new Setup();
        Fighter d2 = s2.add(FighterCatalog.DRUID, Team.RED, 2, 5);
        Fighter w2 = s2.add(FighterCatalog.WARRIOR, Team.BLUE, 5, 5);
        Battle b2 = s2.start(4);
        Check.that(b2.cast(spell(d2, "bees"), w2.position()), "nuée lancée");
        b2.endTurn();
        Check.equal(100 - 6, w2.hp(), "la nuée pique au début du tour de la cible");
        Check.that(w2.status(StatusKind.BEES) != null && w2.status(StatusKind.POISON) == null, "statut « nuée », distinct du poison");

        // tornade 3×3 : dégâts puis éjection hors de la zone (le centre est chassé à l'opposé du lanceur)
        Setup s3 = new Setup();
        Fighter d3 = s3.add(FighterCatalog.DRUID, Team.RED, 2, 7);
        Fighter c3 = s3.add(FighterCatalog.WARRIOR, Team.BLUE, 6, 7);
        Fighter e3 = s3.add(FighterCatalog.MAGE, Team.BLUE, 7, 7);
        Fighter n3 = s3.add(FighterCatalog.ARCHER, Team.BLUE, 6, 6);
        Battle b3 = s3.start(6);
        Check.that(b3.cast(spell(d3, "tornado"), c3.position()), "tornade lancée");
        Check.equal(new Cell(8, 7), e3.position(), "voisin de droite éjecté vers la droite");
        Check.equal(new Cell(6, 5), n3.position(), "voisin du haut éjecté vers le haut");
        Check.equal(new Cell(7, 7), c3.position(), "centre chassé à l'opposé du lanceur, dans la case libérée");
        Check.that(c3.hp() < 100 && e3.hp() < 60 && n3.hp() < 75, "tous touchés");
        Check.equal(80, d3.hp(), "le lanceur hors de la zone n'est pas touché");

        // aqua vita : soigne le lanceur ou un allié proche, pas un ennemi
        Setup s4 = new Setup();
        Fighter d4 = s4.add(FighterCatalog.DRUID, Team.RED, 2, 5);
        Fighter a4 = s4.add(FighterCatalog.ARCHER, Team.RED, 4, 5);
        Fighter w4 = s4.add(FighterCatalog.WARRIOR, Team.BLUE, 3, 6);
        Battle b4 = s4.start(8);
        a4.takeDamage(30);
        Check.that(!b4.canCast(spell(d4, "aquavita"), w4.position()), "pas de soin sur un ennemi");
        Check.that(b4.cast(spell(d4, "aquavita"), a4.position()), "aqua vita sur l'Archère");
        Check.inRange(a4.hp() - 45, 14, 20, "soin de 14 à 20 PV");
    }

    private static void traps() {
        Check.suite("Pièges");
        Setup s = new Setup();
        Fighter a = s.add(FighterCatalog.ARCHER, Team.RED, 5, 2);
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.BLUE, 5, 7);
        Battle b = s.start(11);
        Spell trap = spell(a, "trap");
        Check.that(b.cast(trap, new Cell(5, 5)), "piège posé");
        Check.that(!b.canCast(trap, new Cell(5, 6)), "relance 1 : un piège par tour");
        b.endTurn();
        Check.that(b.move(new Cell(5, 4)), "l'ennemi traverse la case piégée");
        Check.equal(new Cell(5, 5), w.position(), "il est arrêté net sur le piège");
        Check.inRange(100 - w.hp(), 12, 16, "dégâts du piège");
        Check.equal(1, w.pm(), "seuls les PM des cases parcourues sont dépensés");
        Check.that(b.traps().isEmpty(), "le piège disparaît");
        b.endTurn();
        b.cast(trap, new Cell(6, 3));
        b.endTurn();
        b.endTurn();
        b.cast(trap, new Cell(7, 3));
        b.endTurn();
        b.endTurn();
        b.cast(trap, new Cell(8, 3));
        Check.equal(2, b.traps().size(), "2 pièges maximum : le plus ancien disparaît");
        Check.that(b.trapAt(new Cell(6, 3)) == null, "c'est bien le plus ancien qui est retiré");
        b.endTurn();
        Check.that(b.reach().cells().size() > 0, "les pièges ne bloquent pas le calcul de chemin");
    }

    private static void destructibles() {
        Check.suite("Obstacles destructibles");
        Setup s = new Setup();
        Fighter a = s.add(FighterCatalog.ARCHER, Team.RED, 2, 5);
        s.add(FighterCatalog.WARRIOR, Team.BLUE, 9, 5);
        s.board.setObstacle(5, 5, true, 10);
        s.board.setObstacle(5, 6, true, 0);
        Battle b = s.start(12);
        Check.that(!b.canCast(spell(a, "aimed"), new Cell(9, 5)), "la caisse bloque la vue");
        Check.that(b.canCast(spell(a, "aimed"), new Cell(5, 5)), "la caisse peut être visée");
        Check.that(!b.canCast(spell(a, "aimed"), new Cell(5, 6)), "un rocher ne peut pas être visé");
        b.cast(spell(a, "aimed"), new Cell(5, 5));
        Check.that(!s.board.hasObstacle(new Cell(5, 5)), "caisse détruite (10 PV, tir 10-14)");
        Check.equal(1L, s.count(BattleEvent.ObstacleDestroyed.class), "événement de destruction");
        Check.that(s.board.isWalkable(new Cell(5, 5)), "la case devient praticable");
        Check.that(b.hasLineOfSight(a.position(), new Cell(9, 5)), "et ne bloque plus la vue");
        Check.equal(1, a.stats().obstaclesDestroyed(), "statistique de destruction");
    }

    private static void shield() {
        Check.suite("Bouclier");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Battle b = s.start(13);
        b.cast(spell(w, "shield"), w.position());
        b.endTurn();
        int before = w.hp();
        b.cast(spell(m, "arcane"), w.position());
        Check.inRange(before - w.hp(), 4, 6, "dégâts 8-12 réduits de moitié");
        b.endTurn();
        Check.that(!w.hasShield(), "expiré au début du tour suivant du guerrier");
    }

    private static void initiative() {
        Check.suite("Initiative et ordre de jeu");
        List<Fighter> list = new ArrayList<>();
        Fighter r1 = new Fighter(FighterCatalog.WARRIOR, Team.RED, 0, new Cell(1, 1));
        Fighter r2 = new Fighter(FighterCatalog.ARCHER, Team.RED, 1, new Cell(3, 1));
        Fighter b1 = new Fighter(FighterCatalog.MAGE, Team.BLUE, 0, new Cell(13, 13));
        Fighter b2 = new Fighter(FighterCatalog.WARRIOR, Team.BLUE, 1, new Cell(11, 13));
        list.add(r1);
        list.add(r2);
        list.add(b1);
        list.add(b2);
        List<Fighter> order = Battle.computeTurnOrder(list);
        Check.equal(List.of(r2, b1, r1, b2), order, "équipes alternées, la plus rapide (Archère 12) commence");

        Board board = new Board(15);
        Battle b = new Battle(board, list, new Random(14));
        List<BattleEvent> log = new ArrayList<>();
        b.addListener(log::add);
        b.start();
        Check.that(log.get(0) instanceof BattleEvent.TurnOrderSet, "l'ordre est annoncé avant le premier tour");
        for (int i = 0; i < b.turnOrder().size() - 1; i++) {
            Check.that(b.turnOrder().get(i).team() != b.turnOrder().get(i + 1).team(), "alternance des équipes");
        }
        Fighter first = b.active();
        for (int i = 0; i < 4; i++) b.endTurn();
        Check.equal(first, b.active(), "après un tour complet, retour au premier");
        Check.equal(2, b.round(), "manche 2");
        b.turnOrder().get(1).takeDamage(1000);
        b.endTurn();
        Check.that(b.active() != b.turnOrder().get(1), "un combattant mort est sauté");
    }

    private static void teamVictory() {
        Check.suite("Victoire par équipe");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Fighter a = s.add(FighterCatalog.ARCHER, Team.BLUE, 12, 12);
        Battle b = s.start(15);
        m.takeDamage(55);
        b.cast(spell(w, "stun"), m.position());
        Check.that(!m.isAlive(), "le mage tombe");
        Check.that(!b.isOver(), "l'équipe bleue a encore l'Archère");
        Check.equal(1, w.stats().kills(), "statistique d'élimination");
        Check.equal(1L, s.count(BattleEvent.Died.class), "événement de mort");
        b.endTurn();
        Check.equal(a, b.active(), "le mage mort est sauté");
        b.endTurn();
        Check.equal(w, b.active(), "retour au guerrier");
        b.dealDamage(w, a, 1000, CastStyle.MELEE);
        Check.that(b.isOver(), "toute l'équipe bleue est tombée");
        Check.equal(Team.RED, b.winner(), "victoire des Rouges");
        Check.equal(1L, s.count(BattleEvent.BattleEnded.class), "fin de combat annoncée une seule fois");
        Check.that(!b.move(new Cell(5, 6)), "plus aucune action après la fin");
    }

    private static void simulation() {
        Check.suite("Simulation (IA)");
        Setup s = new Setup();
        Fighter w = s.add(FighterCatalog.WARRIOR, Team.RED, 5, 5);
        Fighter m = s.add(FighterCatalog.MAGE, Team.BLUE, 6, 5);
        Battle b = s.start(16);
        int events = s.log.size();
        Battle sim = b.simulate();
        Fighter simMage = sim.counterpart(b, m);
        sim.cast(spell(w, "stun"), simMage.position());
        Check.equal(60, m.hp(), "le combat réel n'est pas touché");
        Check.equal(60 - 14, simMage.hp(), "la simulation utilise la moyenne exacte (11-16 → 14)");
        Check.equal(events, s.log.size(), "aucun événement émis par la simulation");
    }
}
