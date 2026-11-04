package com.minidofus.view.anim;

/** Animation pilotée par la boucle de jeu. */
@FunctionalInterface
public interface Animation {

    /**
     * Fait avancer l'animation.
     *
     * @param dt secondes écoulées depuis l'image précédente
     * @return {@code true} quand l'animation est terminée
     */
    boolean update(double dt);
}
