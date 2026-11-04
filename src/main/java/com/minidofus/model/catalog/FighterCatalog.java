package com.minidofus.model.catalog;

import com.minidofus.model.CastStyle;
import com.minidofus.model.FighterClass;
import com.minidofus.model.IntRange;
import com.minidofus.model.spell.AreaStrikeSpell;
import com.minidofus.model.spell.ChargeSpell;
import com.minidofus.model.spell.HealSpell;
import com.minidofus.model.spell.ShieldSpell;
import com.minidofus.model.spell.StrikeSpell;

import java.util.List;

/**
 * Catalogue des classes jouables. C'est ici (et uniquement ici) que se règle l'équilibrage :
 * PV, PA, PM, coûts, portées et dégâts.
 */
public final class FighterCatalog {

    private FighterCatalog() { }

    public static final FighterClass WARRIOR = new FighterClass("warrior", "Guerrier", 100, 6, 3, List.of(
            new StrikeSpell("melee", "Attaque de mêlée", 4, 1, 1, new IntRange(12, 18), CastStyle.MELEE),
            new ChargeSpell("charge", "Coup de charge", 5, 1, 4, new IntRange(14, 20)),
            new ShieldSpell("shield", "Bouclier", 3, 50, 1)));

    public static final FighterClass MAGE = new FighterClass("mage", "Mage", 60, 7, 4, List.of(
            new StrikeSpell("ranged", "Attaque à distance", 3, 1, 5, new IntRange(8, 12), CastStyle.ARCANE_BOLT),
            new AreaStrikeSpell("fireball", "Boule de feu", 4, 1, 6, new IntRange(14, 20), CastStyle.FIREBALL),
            new HealSpell("heal", "Soin", 3, new IntRange(12, 18))));
}
