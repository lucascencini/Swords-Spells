package com.minidofus.view.fx;

import com.minidofus.view.Camera;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Ensemble des effets actifs, mis à jour et dessinés par la scène. */
public final class EffectLayer {

    private final List<Effect> effects = new ArrayList<>();

    public void add(Effect effect) {
        effects.add(effect);
    }

    public void update(double dt) {
        effects.removeIf(e -> e.update(dt));
    }

    public void drawWorld(Graphics2D g) {
        for (Effect e : effects) e.drawWorld(g);
    }

    public void drawScreen(Graphics2D g, Camera camera) {
        for (Effect e : effects) e.drawScreen(g, camera);
    }

    public void clear() {
        effects.clear();
    }
}
