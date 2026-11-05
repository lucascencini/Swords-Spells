package com.minidofus.net;

import com.minidofus.model.Battle;
import com.minidofus.model.Command;

/**
 * Copie locale d'un combat en ligne : rejoue dans l'ordre les commandes diffusées par le serveur et
 * vérifie l'empreinte d'état après chacune. En cas d'écart (ou de commande manquante), {@link #apply}
 * renvoie {@link Result#DESYNC} : il faut alors redemander tout le combat (RESYNC).
 */
public final class MatchReplica {

    public enum Result { APPLIED, IGNORED, DESYNC }

    private final MatchInfo info;
    private final Battle battle;
    private int applied;

    public MatchReplica(MatchInfo info, Battle battle) {
        this.info = info;
        this.battle = battle;
    }

    public MatchReplica(MatchInfo info) {
        this(info, info.createBattle());
    }

    public MatchInfo info() { return info; }
    public Battle battle() { return battle; }

    /** Nombre de commandes appliquées : c'est aussi l'index attendu de la prochaine. */
    public int applied() { return applied; }

    /** Applique le message CMD {@code index commande empreinte}. */
    public Result apply(Message cmd) {
        int index = cmd.intArg(0, -1);
        if (index < applied) return Result.IGNORED;  // déjà reçue (rattrapage après reconnexion)
        if (index > applied) return Result.DESYNC;   // il en manque
        Command c = Command.decode(cmd.arg(1));
        if (c == null || !battle.apply(c)) return Result.DESYNC;
        applied++;
        return battle.stateHash() == cmd.longArg(2, 0) ? Result.APPLIED : Result.DESYNC;
    }

    /** Index du combattant actif (pour savoir si c'est à nous de jouer). */
    public int activeIndex() {
        return battle.fighters().indexOf(battle.active());
    }

    public int controllerOfActive() {
        return info.controllers()[activeIndex()];
    }
}
