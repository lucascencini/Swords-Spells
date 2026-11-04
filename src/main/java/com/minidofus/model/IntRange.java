package com.minidofus.model;

import java.util.Random;

/** Intervalle d'entiers inclusif, utilisé pour les dégâts et les soins. */
public record IntRange(int min, int max) {

    public IntRange {
        if (min > max) throw new IllegalArgumentException("min > max : " + min + " > " + max);
    }

    public int roll(Random rng) {
        return min + rng.nextInt(max - min + 1);
    }

    @Override
    public String toString() {
        return min + "-" + max;
    }
}
