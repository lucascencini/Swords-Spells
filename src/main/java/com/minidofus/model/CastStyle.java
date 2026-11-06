package com.minidofus.model;

/**
 * Indication de mise en scène : le modèle décrit, la vue choisit les effets. Les quatre dernières valeurs
 * ne sont pas des sorts mais des sources de dégâts indirectes.
 */
public enum CastStyle {
    MELEE,
    STUN_STRIKE,
    PUSH_STRIKE,
    DASH,
    SHIELD,
    GRAPPLE,
    ARCANE_BOLT,
    FIREBALL,
    HEAL,
    TELEPORT,
    POISON,
    ARROW,
    RECOIL_ARROW,
    TRAP,
    ARROW_RAIN,
    LEAP,
    CROW,
    BEES,
    TORNADO,
    LIGHTNING,
    AQUA_VITA,
    ICE_ZONE,
    STONE_SHIELD,
    FROST_BOLT,
    ICE_PRISON,
    ARCANE_RAY,
    OVERLOAD,
    NOVA,
    METEOR,
    /** Choc contre un mur, un obstacle ou un combattant après une poussée. */
    COLLISION,
    /** Déclenchement d'un piège. */
    TRAP_TRIGGER,
    /** Dégâts de poison en début de tour. */
    POISON_TICK,
    /** Piqûres de la nuée d'abeilles en début de tour. */
    BEES_TICK,
    /** Morsure du sol gelé en début de tour. */
    ICE_TICK,
    /** Brûlure en début de tour. */
    BURN_TICK
}
