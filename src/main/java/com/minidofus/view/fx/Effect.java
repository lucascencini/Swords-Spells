package com.minidofus.view.fx;

import com.minidofus.view.Camera;

import java.awt.Graphics2D;

/** Effet visuel éphémère. Les coordonnées sont celles du monde isométrique. */
public interface Effect {

    /** @return {@code true} quand l'effet est terminé et peut être retiré */
    boolean update(double dt);

    /** Dessin dans le repère du monde (transformation caméra déjà appliquée). */
    default void drawWorld(Graphics2D g) { }

    /** Dessin dans le repère de l'écran (textes à taille constante). */
    default void drawScreen(Graphics2D g, Camera camera) { }
}
