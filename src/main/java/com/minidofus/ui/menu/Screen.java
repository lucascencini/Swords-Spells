package com.minidofus.ui.menu;

import java.awt.Graphics2D;

/** Écran de menu (titre, configuration) : reçoit les entrées et se dessine en Java2D. */
public interface Screen {

    void layout(int width, int height);

    void update(double dt);

    void draw(Graphics2D g);

    void mouseMoved(int x, int y);

    void mousePressed(int x, int y);

    /** Touche validée : « ENTER », « ESCAPE », « LEFT », « RIGHT », « TAB », « BACK_SPACE »… */
    void key(String key);

    /** L'écran attend-il du texte (un champ de saisie a le focus) ? Les raccourcis clavier sont alors coupés. */
    default boolean wantsText() {
        return false;
    }

    /** Caractère tapé ou texte collé, quand {@link #wantsText()} est vrai. */
    default void text(String typed) {
    }
}
