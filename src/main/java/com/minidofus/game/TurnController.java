package com.minidofus.game;

import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Pathfinder;
import com.minidofus.model.Spell;
import com.minidofus.model.TargetMode;
import com.minidofus.view.Highlights;

import java.util.List;
import java.util.Objects;
import com.minidofus.model.CastStyle;

/**
 * Traduit les intentions du joueur (survol, clic, raccourcis) en actions sur le combat, et calcule
 * les cases à mettre en surbrillance. Les calculs ne sont refaits que si quelque chose a changé
 * (le prototype recréait tous les graphismes à chaque mouvement de souris).
 */
final class TurnController {

    private final Battle battle;
    private final Highlights highlights = new Highlights();
    private Spell selected;
    private Cell hover;

    private int cachedRevision = -1;
    private Spell cachedSpell;
    private Cell cachedHover;
    private boolean cachedBusy;
    private Pathfinder.Reach reach;

    TurnController(Battle battle) {
        this.battle = battle;
    }

    Highlights highlights() { return highlights; }
    Spell selected() { return selected; }

    void setHover(Cell cell) {
        this.hover = cell;
    }

    void selectMove() {
        selected = null;
    }

    /** Sélectionne le sort n ; ré-appuyer sur un sort personnel le lance, sur un autre sort l'annule. */
    void selectSpell(int index) {
        List<Spell> spells = battle.active().fighterClass().spells();
        if (index < 0 || index >= spells.size()) return;
        Spell spell = spells.get(index);
        if (battle.active().pa() < spell.cost()) return;
        if (selected == spell) {
            if (spell.targetMode() == TargetMode.SELF) cast(battle.active().position());
            else selected = null;
            return;
        }
        selected = spell;
    }

    void cancel() {
        selected = null;
    }

    void click(Cell cell) {
        if (cell == null || battle.isOver()) return;
        if (selected != null) {
            if (!cast(cell)) selected = null; // clic hors cible : on revient au déplacement
        } else {
            battle.move(cell);
        }
    }

    void endTurn() {
        selected = null;
        battle.endTurn();
    }

    private boolean cast(Cell cell) {
        if (!battle.canCast(selected, cell)) return false;
        battle.cast(selected, cell);
        selected = null;
        return true;
    }

    /** Recalcule les surbrillances si l'état, la sélection ou la case survolée ont changé. */
    void refresh(boolean busy) {
        if (battle.revision() == cachedRevision && Objects.equals(selected, cachedSpell)
                && Objects.equals(hover, cachedHover) && busy == cachedBusy) {
            return;
        }
        if (battle.revision() != cachedRevision) reach = battle.reach();
        cachedRevision = battle.revision();
        cachedSpell = selected;
        cachedHover = hover;
        cachedBusy = busy;

        highlights.clear();
        highlights.setHover(busy ? null : hover);
        if (busy || battle.isOver()) return;
        if (selected == null) buildMoveHighlights();
        else buildSpellHighlights();
    }

    private void buildMoveHighlights() {
        for (Cell c : reach.cells()) highlights.put(c, Highlights.Style.REACH);
        if (hover != null && reach.canReach(hover)) {
            List<Cell> path = reach.pathTo(hover);
            for (Cell c : path) highlights.put(c, Highlights.Style.PATH);
            highlights.setLabel(hover, path.size() + " PM");
        }
    }

    private void buildSpellHighlights() {
        Fighter caster = battle.active();
        Spell s = selected;
        if (s.targetMode() == TargetMode.SELF) {
            highlights.put(caster.position(), s.style() == CastStyle.HEAL
                    ? Highlights.Style.SELF_HEAL : Highlights.Style.SELF_BUFF);
            return;
        }
        int size = battle.board().size();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Cell c = new Cell(x, y);
                int d = caster.position().distanceTo(c);
                if (d < s.minRange() || d > s.maxRange()) continue;
                if (s.targetMode() == TargetMode.LINE && !caster.position().isAlignedWith(c)) continue;
                Fighter occupant = battle.fighterAt(c);
                boolean enemy = occupant != null && occupant != caster;
                highlights.put(c, enemy ? Highlights.Style.RANGE_ENEMY : Highlights.Style.RANGE);
            }
        }
        if (hover != null && battle.canCast(s, hover)) {
            for (Cell c : s.impactCells(battle, caster, hover)) {
                Fighter occupant = battle.fighterAt(c);
                highlights.put(c, occupant != null && occupant != caster
                        ? Highlights.Style.IMPACT_ENEMY : Highlights.Style.IMPACT);
            }
        }
    }

    String hint(boolean busy) {
        if (busy || battle.isOver()) return "";
        Fighter f = battle.active();
        if (selected == null) {
            return f.pm() > 0
                    ? "Cliquez sur une case verte pour vous déplacer, ou choisissez un sort (touches 1 à 3)"
                    : "Plus de PM : lancez un sort ou terminez votre tour (Espace)";
        }
        return switch (selected.targetMode()) {
            case ENEMY -> "Ciblez un ennemi dans la zone orange · clic droit ou Échap pour annuler";
            case CELL -> "Choisissez une case : la croix touche tout ennemi qui s'y trouve";
            case LINE -> "Choisissez une direction : vous foncez jusqu'au premier obstacle ou ennemi";
            case SELF -> "Cliquez sur votre personnage ou appuyez de nouveau sur la touche pour lancer";
        };
    }
}
