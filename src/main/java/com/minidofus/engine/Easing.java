package com.minidofus.engine;

/** Courbes d'interpolation (t dans [0, 1]). */
public final class Easing {

    private Easing() { }

    public static double inOutQuad(double t) {
        return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    public static double outCubic(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double outBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    public static double clamp01(double t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Lissage exponentiel indépendant du nombre d'images par seconde. */
    public static double approach(double current, double target, double speed, double dt) {
        return target + (current - target) * Math.exp(-speed * dt);
    }
}
