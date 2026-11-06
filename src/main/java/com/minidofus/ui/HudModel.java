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

    /** Partie en ligne ? */
    default boolean online() {
        return false;
    }

    /** Secondes restantes au combattant actif (chrono en ligne), ou -1. */
    default int secondsLeft() {
        return -1;
    }

    /** Joueur qui contrôle le combattant (en ligne), ou {@code null}. */
    default String controllerLabel(Fighter f) {
        return null;
    }

    /** Message affiché sur la barre d'actions quand ce n'est pas au joueur d'agir. */
    default String waitingLabel() {
        return "L'ordinateur joue…";
    }

    /** Lignes de récompense de fin de combat (expérience, niveaux gagnés), en ligne avec un compte. */
    default List<String> rewardLines() {
        return List.of();
    }

    /** Libellé du bouton de retour en fin de combat en ligne. */
    default String backLabel() {
        return "Retour au salon (Échap)";
    }
}
