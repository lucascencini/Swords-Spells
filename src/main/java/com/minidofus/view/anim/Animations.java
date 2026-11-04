package com.minidofus.view.anim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** Briques de base pour composer des animations (séquence, parallèle, attente, interpolation). */
public final class Animations {

    private Animations() { }

    /** Exécute une action puis se termine immédiatement. */
    public static Animation call(Runnable action) {
        return dt -> {
            action.run();
            return true;
        };
    }

    public static Animation delay(double seconds) {
        double[] left = {seconds};
        return dt -> (left[0] -= dt) <= 0;
    }

    /** Interpolation : {@code progress} reçoit t de 0 à 1 (1 garanti à la fin). */
    public static Animation tween(double seconds, DoubleConsumer progress) {
        double[] elapsed = {0};
        return dt -> {
            elapsed[0] += dt;
            double t = seconds <= 0 ? 1 : Math.min(1, elapsed[0] / seconds);
            progress.accept(t);
            return t >= 1;
        };
    }

    /** Construit l'animation au moment où elle démarre (pour lire les positions à jour). */
    public static Animation defer(Supplier<Animation> factory) {
        Animation[] inner = {null};
        return dt -> {
            if (inner[0] == null) inner[0] = factory.get();
            return inner[0].update(dt);
        };
    }

    public static Animation sequence(Animation... steps) {
        Deque<Animation> queue = new ArrayDeque<>(Arrays.asList(steps));
        return dt -> {
            while (!queue.isEmpty()) {
                if (!queue.peekFirst().update(dt)) return false;
                queue.pollFirst();
                dt = 0; // l'étape suivante démarre dans la même image
            }
            return true;
        };
    }

    public static Animation parallel(Animation... parts) {
        List<Animation> running = new ArrayList<>(Arrays.asList(parts));
        return dt -> {
            running.removeIf(a -> a.update(dt));
            return running.isEmpty();
        };
    }
}
