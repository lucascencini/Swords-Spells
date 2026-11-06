package com.minidofus.model.catalog;

import com.minidofus.model.CastStyle;
import com.minidofus.model.StatusKind;
import com.minidofus.model.FighterClass;
import com.minidofus.model.IntRange;
import com.minidofus.model.MoveStyle;
import com.minidofus.model.spell.AbstractSpell.Base;
import com.minidofus.model.spell.AreaStrikeSpell;
import com.minidofus.model.spell.ChargeSpell;
import com.minidofus.model.spell.GrappleSpell;
import com.minidofus.model.spell.HealSpell;
import com.minidofus.model.spell.IceZoneSpell;
import com.minidofus.model.spell.StoneShieldSpell;
import com.minidofus.model.spell.MobilitySpell;
import com.minidofus.model.spell.PoisonSpell;
import com.minidofus.model.spell.ShieldSpell;
import com.minidofus.model.spell.StrikeSpell;
import com.minidofus.model.spell.TrapSpell;

import java.util.List;

/**
 * Catalogue des classes jouables. C'est ici (et uniquement ici) que se règle l'équilibrage :
 * PV, PA, PM, initiative, coûts, portées, dégâts et délais de relance.
 */
public final class FighterCatalog {

    private FighterCatalog() { }

    /** Corps à corps et contrôle : silence, poussée, attraction. */
    public static final FighterClass WARRIOR = new FighterClass("warrior", "Guerrier", 100, 6, 3, 8, 1, List.of(
            new StrikeSpell(Base.of("stun", "Frappe assommante", 4, 1, 1), new IntRange(11, 16), CastStyle.STUN_STRIKE, 0, 1),
            new ChargeSpell(Base.of("charge", "Coup de charge", 5, 1, 4).cooldown(1), new IntRange(14, 20)),
            new ShieldSpell(Base.of("shield", "Bouclier", 3, 0, 0).cooldown(2), 50),
            new StrikeSpell(Base.of("push", "Repousser", 3, 1, 1).cooldown(1), new IntRange(6, 9), CastStyle.PUSH_STRIKE, 2, 0),
            new GrappleSpell(Base.of("grapple", "Grappin", 3, 2, 5).cooldown(2), new IntRange(4, 6), 3)));

    /** Distance et contrôle : trait arcanique, boule de feu, zone de glace, téléportation, bouclier de pierre. */
    public static final FighterClass MAGE = new FighterClass("mage", "Mage", 60, 7, 4, 10, 4, List.of(
            new StrikeSpell(Base.of("arcane", "Trait arcanique", 3, 1, 5), new IntRange(8, 12), CastStyle.ARCANE_BOLT),
            new AreaStrikeSpell(Base.of("fireball", "Boule de feu", 4, 1, 6).cooldown(1), new IntRange(14, 20),
                    AreaStrikeSpell.Shape.CROSS, CastStyle.FIREBALL),
            new IceZoneSpell(Base.of("icezone", "Zone de glace", 4, 2, 6).cooldown(3), new IntRange(8, 11), 2, 6, 2),
            new MobilitySpell(Base.of("teleport", "Téléportation", 2, 1, 4).cooldown(3), CastStyle.TELEPORT, MoveStyle.TELEPORT),
            new StoneShieldSpell(Base.of("stoneshield", "Bouclier de pierre", 3, 0, 3).cooldown(3), 2)));

    /** Longue portée et contrôle du terrain : tir précis, recul, pièges, pluie de flèches, bond. */
    public static final FighterClass ARCHER = new FighterClass("archer", "Archère", 75, 6, 3, 12, 5, List.of(
            new StrikeSpell(Base.of("aimed", "Tir précis", 3, 2, 7), new IntRange(10, 14), CastStyle.ARROW),
            new StrikeSpell(Base.of("recoil", "Flèche de recul", 3, 1, 4).inLine().cooldown(1), new IntRange(6, 9),
                    CastStyle.RECOIL_ARROW, 2, 0),
            new TrapSpell(Base.of("trap", "Piège", 2, 1, 4).cooldown(1), new IntRange(12, 16), 2),
            new AreaStrikeSpell(Base.of("rain", "Pluie de flèches", 5, 3, 8).cooldown(2), new IntRange(7, 10),
                    AreaStrikeSpell.Shape.SQUARE, CastStyle.ARROW_RAIN),
            new MobilitySpell(Base.of("leap", "Bond", 2, 1, 3).inLine().cooldown(2), CastStyle.LEAP, MoveStyle.LEAP)));

    /** Soutien et contrôle : corbeau, nuée d'abeilles, tornade qui éjecte, éclair sans ligne de vue, soin. */
    public static final FighterClass DRUID = new FighterClass("druid", "Druide", 80, 6, 3, 9, 3, List.of(
            new StrikeSpell(Base.of("crow", "Lancer de corbeau", 3, 1, 6), new IntRange(9, 13), CastStyle.CROW),
            new PoisonSpell(Base.of("bees", "Nuée d'abeilles", 3, 1, 5).cooldown(2), 6, 3, CastStyle.BEES, StatusKind.BEES),
            new AreaStrikeSpell(Base.of("tornado", "Tornade", 4, 2, 6).cooldown(2), new IntRange(8, 12),
                    AreaStrikeSpell.Shape.SQUARE, CastStyle.TORNADO, 1),
            new StrikeSpell(Base.of("lightning", "Éclair", 4, 2, 7).noLineOfSight().cooldown(2), new IntRange(18, 24),
                    CastStyle.LIGHTNING),
            new HealSpell(Base.of("aquavita", "Aqua vita", 3, 0, 3).cooldown(2), new IntRange(14, 20), CastStyle.AQUA_VITA)));

    public static final List<FighterClass> ALL = List.of(WARRIOR, MAGE, ARCHER, DRUID);

    public static FighterClass byId(String id) {
        for (FighterClass c : ALL) if (c.id().equals(id)) return c;
        throw new IllegalArgumentException("Classe inconnue : " + id);
    }
}
