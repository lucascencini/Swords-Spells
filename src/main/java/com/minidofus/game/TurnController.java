package com.minidofus.game;

import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Pathfinder;
import com.minidofus.model.Spell;
import com.minidofus.model.TargetMode;
import com.minidofus.model.Trap;
import com.minidofus.ui.UiTheme;
import com.minidofus.view.Highlights;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Traduit les intentions du joueur humain (survol, clic, raccourcis) en actions sur le combat, et calcule
 * les cases à mettre en surbrillance. Les calculs ne sont refaits que si quelque chose a changé.
 */
final class TurnController {

    private static final double NOTICE_SECONDS = 2.2;

    /**
     * Exécute les actions décidées par le joueur. En local : directement sur le combat. En ligne : la
     * commande est envoyée au serveur et le combat n'avance qu'à la réception de sa diffusion.
     */
    interface Executor {
        boolean move(Cell cell);

        boolean cast(Spell spell, Cell cell);

        void endTurn();
    }

    private final Battle battle;
    private final Executor executor;
    private final Highlights highlights = new Highlights();
    private Spell selected;
    private Cell hover;
    private String notice;
    private double noticeTime;

    private int cachedRevision = -1;
    private Spell cachedSpell;
    private Cell cachedHover;
    private boolean cachedLocked;
    private Pathfinder.Reach reach;

    TurnController(Battle battle) {
        this(battle, null);
    }

    TurnController(Battle battle, Executor executor) {
        this.battle = battle;
        this.executor = executor != null ? executor : new Executor() {
            @Override
            public boolean move(Cell cell) {
                return battle.move(cell);
            }

            @Override
            public boolean cast(Spell spell, Cell cell) {
                return battle.cast(spell, cell);
            }

            @Override
            public void endTurn() {
                battle.endTurn();
            }
        };
    }

    Highlights highlights() { return highlights; }
    Spell selected() { return selected; }

    void setHover(Cell cell) {
        this.hover = cell;
    }

    void update(double dt) {
        if (notice != null && (noticeTime -= dt) <= 0) notice = null;
    }

    private void notify(String message) {
        notice = message;
        noticeTime = NOTICE_SECONDS;
    }

    void selectMove() {
        selected = null;
    }

    /** Sélectionne le sort n ; ré-appuyer sur un sort personnel le lance, sur un autre sort l'annule. */
    void selectSpell(int index) {
        List<Spell> spells = battle.active().fighterClass().spells();
        if (index < 0 || index >= spells.size()) return;
        Spell spell = spells.get(index);
        String reason = battle.unavailableReason(spell);
        if (reason != null) {
            notify(spell.name() + " : " + reason.toLowerCase());
            return;
        }
        if (selected == spell) {
            if (spell.targetMode() == TargetMode.SELF) cast(battle.active().position());
            else selected = null;
            return;
        }
        selected = spell;
        if (spell.targetMode() == TargetMode.SELF) hover = battle.active().position();
    }

    void cancel() {
        selected = null;
    }

    void click(Cell cell) {
        if (cell == null || battle.isOver()) return;
        if (selected == null) {
            if (!executor.move(cell) && battle.fighterAt(cell) == null && battle.active().pm() > 0) {
                notify("Case hors de portée de déplacement");
            }
            return;
        }
        if (cast(cell)) return;
        String why = whyNot(selected, cell);
        if (why == null) selected = null; // clic hors de la zone : on annule le sort
        else notify(why);
    }

    void endTurn() {
        selected = null;
        executor.endTurn();
    }

    private boolean cast(Cell cell) {
        if (!battle.canCast(selected, cell)) return false;
        executor.cast(selected, cell);
        selected = null;
        return true;
    }

    /** Explication d'un refus pour une case dans la zone de portée ; {@code null} si la case est hors zone. */
    private String whyNot(Spell s, Cell cell) {
        Fighter caster = battle.active();
        if (!s.isInRange(caster.position(), cell) && !(s.targetMode() == TargetMode.ALLY && cell.equals(caster.position()))) {
            return null;
        }
        if (s.needsLineOfSight() && !battle.hasLineOfSight(caster.position(), cell)) return "Pas de ligne de vue";
        Fighter occupant = battle.fighterAt(cell);
        return switch (s.targetMode()) {
            case ENEMY -> occupant != null && !occupant.isEnemyOf(caster) ? "Ciblez un ennemi" : "Aucune cible sur cette case";
            case ALLY -> occupant == null ? "Ciblez un allié" : "Impossible de soigner un ennemi";
            case FREE_CELL, LINE -> occupant != null || !battle.board().isWalkable(cell) ? "Case occupée"
                    : battle.trapAt(cell) != null ? "Il y a déjà un piège ici" : "Cible invalide";
            default -> "Cible invalide";
        };
    }

    /** Recalcule les surbrillances si l'état, la sélection ou la case survolée ont changé. */
    void refresh(boolean locked) {
        if (battle.revision() == cachedRevision && Objects.equals(selected, cachedSpell)
                && Objects.equals(hover, cachedHover) && locked == cachedLocked) {
            return;
        }
        if (battle.revision() != cachedRevision) {
            reach = battle.isOver() ? null : battle.reach();
            if (selected != null && battle.unavailableReason(selected) != null) selected = null;
        }
        cachedRevision = battle.revision();
        cachedSpell = selected;
        cachedHover = hover;
        cachedLocked = locked;

        highlights.clear();
        highlights.setHover(locked ? null : hover);
        if (locked || battle.isOver()) return;
        if (selected == null) buildMoveHighlights();
        else buildSpellHighlights();
    }

    private void buildMoveHighlights() {
        for (Cell c : reach.cells()) highlights.put(c, Highlights.Style.REACH);
        if (hover != null && reach.canReach(hover)) {
            List<Cell> path = reach.pathTo(hover);
            List<Cell> shown = new ArrayList<>();
            Trap stop = null;
            for (Cell c : path) {
                shown.add(c);
                Trap t = battle.trapAt(c);
                if (t != null && t.owner().isEnemyOf(battle.active())) {
                    stop = t;
                    break;
                }
            }
            for (Cell c : shown) highlights.put(c, Highlights.Style.PATH);
            if (stop != null) {
                highlights.put(stop.cell(), Highlights.Style.IMPACT_ENEMY);
                highlights.setLabel(stop.cell(), "Piège !", UiTheme.DAMAGE);
            } else {
                highlights.setLabel(hover, path.size() + " PM");
            }
        }
    }

    private void buildSpellHighlights() {
        Fighter caster = battle.active();
        Spell s = selected;
        if (s.targetMode() == TargetMode.SELF) {
            highlights.put(caster.position(), Highlights.Style.SELF_BUFF);
            return;
        }
        int size = battle.board().size();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Cell c = new Cell(x, y);
                if (!s.isInRange(caster.position(), c)) continue;
                if (s.needsLineOfSight() && !battle.hasLineOfSight(caster.position(), c)) {
                    highlights.put(c, Highlights.Style.RANGE_BLOCKED);
                    continue;
                }
                Fighter occupant = battle.fighterAt(c);
                Highlights.Style style = Highlights.Style.RANGE;
                if (occupant != null && occupant.isEnemyOf(caster) && s.targetMode() != TargetMode.ALLY) {
                    style = Highlights.Style.RANGE_ENEMY;
                } else if (occupant != null && s.targetMode() == TargetMode.ALLY) {
                    style = Highlights.Style.ALLY_TARGET;
                }
                highlights.put(c, style);
            }
        }
        if (s.targetMode() == TargetMode.ALLY) highlights.put(caster.position(), Highlights.Style.ALLY_TARGET);
        if (hover != null && battle.canCast(s, hover)) {
            for (Cell c : s.impactCells(battle, caster, hover)) {
                Fighter occupant = battle.fighterAt(c);
                highlights.put(c, occupant != null && occupant != caster
                        ? Highlights.Style.IMPACT_ENEMY : Highlights.Style.IMPACT);
            }
        }
    }

    String hint() {
        if (battle.isOver()) return "";
        if (notice != null) return notice;
        Fighter f = battle.active();
        if (selected == null) {
            return f.pm() > 0
                    ? "Cliquez sur une case verte pour vous déplacer, ou choisissez un sort (touches 1 à " + f.fighterClass().spells().size() + ")"
                    : "Plus de PM : lancez un sort ou terminez votre tour (Espace)";
        }
        String base = switch (selected.targetMode()) {
            case ENEMY -> "Ciblez un ennemi ou une caisse";
            case ALLY -> "Ciblez un allié ou vous-même";
            case CELL -> "Choisissez une case : la zone touche aussi vos alliés";
            case LINE -> "Choisissez une case en ligne droite";
            case FREE_CELL -> "Choisissez une case libre";
            case SELF -> "Cliquez sur votre personnage ou rappuyez sur la touche";
        };
        return base + " · zone grise : pas de ligne de vue · Échap pour annuler";
    }
}
