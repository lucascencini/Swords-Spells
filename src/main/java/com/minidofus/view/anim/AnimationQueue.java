package com.minidofus.view.anim;

import java.util.ArrayDeque;
import java.util.Deque;

/** File d'animations jouées l'une après l'autre. Tant qu'elle n'est pas vide, le joueur ne peut pas agir. */
public final class AnimationQueue {

    private final Deque<Animation> queue = new ArrayDeque<>();

    public void add(Animation animation) {
        queue.addLast(animation);
    }

    public void update(double dt) {
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 64) {
            if (!queue.peekFirst().update(dt)) return;
            queue.pollFirst();
            dt = 0;
        }
    }

    public boolean isBusy() {
        return !queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}
