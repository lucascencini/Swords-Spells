package com.minidofus.ui.menu;

import java.awt.Graphics2D;

/** Écran de menu (titre, configuration) : reçoit les entrées et se dessine en Java2D. */
public interface Screen {

    void layout(int width, int height);

    void update(double dt);

    void draw(Graphics2D g);

    void mouseMoved(int x, int y);

    void mousePressed(int x, int y);

    /** Touche validée : « ENTER », « ESCAPE », « LEFT », « RIGHT »… */
    void key(String key);
}
