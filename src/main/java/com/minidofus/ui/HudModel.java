package com.minidofus.ui;

import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.view.FighterView;

import java.util.List;

/** Ce que le HUD a besoin de lire de la partie (il ne modifie jamais rien lui-même). */
public interface HudModel {

    /** Combattant de gauche puis de droite. */
    List<FighterView> fighterViews();

    Fighter active();

    int turn();

    boolean isBusy();

    boolean isOver();

    Fighter winner();

    /** Sort sélectionné, ou {@code null} en mode déplacement. */
    Spell selectedSpell();

    String mapName();

    long seed();

    boolean isMuted();

    /** Phrase d'aide contextuelle sous la barre d'actions. */
    String hint();
}
