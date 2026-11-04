package com.minidofus;

import java.util.Objects;

/** Mini-harnais de test sans dépendance (pas besoin de JUnit pour compiler et lancer les tests). */
public final class Check {

    private static int passed;
    private static int failed;
    private static String suite = "";

    private Check() { }

    public static void suite(String name) {
        suite = name;
        System.out.println("== " + name);
    }

    public static void that(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  ECHEC [" + suite + "] " + message);
        }
    }

    public static void equal(Object expected, Object actual, String message) {
        that(Objects.equals(expected, actual), message + " (attendu " + expected + ", obtenu " + actual + ")");
    }

    public static void inRange(int value, int min, int max, String message) {
        that(value >= min && value <= max, message + " (" + value + " hors de " + min + ".." + max + ")");
    }

    /** Affiche le bilan et renvoie le code de sortie. */
    public static int report() {
        System.out.println(passed + " vérifications réussies, " + failed + " échec(s)");
        return failed == 0 ? 0 : 1;
    }
}
