package com.minidofus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Zone de glace persistante (3×3 autour de {@code center}) posée par un Mage. Un ennemi de son poseur qui
 * commence son tour dedans subit {@code damage} et perd {@code pmLoss} PM pour ce tour. La zone fond au début
 * du tour de son poseur, après {@code turnsLeft} tours de celui-ci.
 */
public final class IceZone {

    private final Cell center;
    private final Fighter owner;
    private final int damage;
    private final int pmLoss;
    private int turnsLeft;

    public IceZone(Cell center, Fighter owner, int turnsLeft, int damage, int pmLoss) {
        this.center = center;
        this.owner = owner;
        this.turnsLeft = turnsLeft;
        this.damage = damage;
        this.pmLoss = pmLoss;
    }

    public Cell center() { return center; }
    public Fighter owner() { return owner; }
    public int turnsLeft() { return turnsLeft; }
    public int damage() { return damage; }
    public int pmLoss() { return pmLoss; }

    public boolean contains(Cell c) {
        return Math.abs(c.x() - center.x()) <= 1 && Math.abs(c.y() - center.y()) <= 1;
    }

    public List<Cell> cells(Board board) {
        List<Cell> out = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Cell c = center.plus(dx, dy);
                if (board.inBounds(c)) out.add(c);
            }
        }
        return out;
    }

    /** Un tour du poseur s'est écoulé ; renvoie {@code true} si la zone a fondu. */
    boolean age() {
        turnsLeft--;
        return turnsLeft <= 0;
    }

    IceZone copy(Fighter mappedOwner) {
        return new IceZone(center, mappedOwner, turnsLeft, damage, pmLoss);
    }
}
