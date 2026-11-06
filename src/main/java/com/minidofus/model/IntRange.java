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

    /** Intervalle multiplié (affichage des dégâts améliorés). */
    public IntRange scaled(double m) {
        if (m == 1) return this;
        return new IntRange((int) Math.round(min * m), (int) Math.round(max * m));
    }

    @Override
    public String toString() {
        return min + "-" + max;
    }
}
