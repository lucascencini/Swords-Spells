package com.minidofus.ui;

import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.model.Team;
import com.minidofus.view.FighterView;

import java.util.List;

/** Ce que le HUD a besoin de lire de la partie (il ne modifie jamais rien lui-même). */
public interface HudModel {

    /** Tous les combattants (équipe rouge puis bleue). */
    List<FighterView> fighterViews();

    Fighter active();

    /** Ordre de jeu fixé par l'initiative. */
    List<Fighter> turnOrder();

    int round();

    boolean isBusy();

    /** Faux pendant le tour de l'ordinateur. */
    boolean isHumanTurn();

    boolean isOver();

    Team winner();

    /** Équipe du joueur quand il affronte l'ordinateur, {@code null} à deux joueurs. */
    Team soloHumanTeam();

    /** Sort sélectionné, ou {@code null} en mode déplacement. */
    Spell selectedSpell();

    /** Pourquoi le sort ne peut pas être lancé maintenant (relance, PA, silence), ou {@code null}. */
    String unavailableReason(Spell spell);

    String mapName();

    long seed();

    boolean isMuted();

    /** Durée réelle du combat (secondes), pour le récapitulatif de fin. */
    double battleSeconds();

    /** Phrase d'aide contextuelle sous la barre d'actions. */
    String hint();
}
