package com.minidofus.engine;

import java.util.Arrays;

/** Catalogue des sons du jeu, avec leur volume (mêmes réglages que le prototype). */
public enum Sfx {
    MOVE("wood_01", 0.35),
    MELEE("blade_02", 0.6),
    CHARGE("blade_03", 0.55),
    CHARGE_IMPACT("metal_02", 0.45),
    PROJECTILE("spell_01", 0.5),
    FIREBALL("spell_fire_03", 0.55),
    FIREBALL_IMPACT("spell_fire_06", 0.6),
    HIT("creature_hurt_01", 0.5),
    GRAPPLE("metal_02", 0.4),
    TELEPORT("spell_01", 0.45),
    POISON("spell_01", 0.35),
    ARROW("blade_01", 0.5),
    TRAP("wood_01", 0.5),
    COLLISION("metal_02", 0.5),
    BREAK("wood_01", 0.6),
    DEATH("creature_hurt_01", 0.6),
    TURN("misc_01", 0.4),
    BUFF("misc_01", 0.5),
    VICTORY("item_gem_02", 0.55),
    VICTORY_COINS("item_coins_02", 0.5),
    CROW("druid_crow", 0.45),
    BEES("druid_bees", 0.4),
    WIND("druid_wind", 0.5),
    THUNDER("druid_thunder", 0.6),
    CHIME("druid_chime", 0.4);

    private final String file;
    private final double volume;

    Sfx(String file, double volume) {
        this.file = file;
        this.volume = volume;
    }

    public String file() { return file; }
    public double volume() { return volume; }

    public void play(SoundPlayer player) {
        player.play(file, volume);
    }

    public void play(SoundPlayer player, double delaySeconds) {
        player.play(file, volume, delaySeconds);
    }

    public static String[] allFiles() {
        return Arrays.stream(values()).map(Sfx::file).distinct().toArray(String[]::new);
    }
}
