package com.minidofus.ui;

/** Élément d'interface cliqué. {@code index} sert pour les sorts (0, 1, 2...). */
public record HudAction(Type type, int index) {

    public enum Type { MOVE, SPELL, END_TURN, REPLAY, NEW_MAP, MENU, MUTE }

    public static HudAction of(Type type) {
        return new HudAction(type, -1);
    }
}
