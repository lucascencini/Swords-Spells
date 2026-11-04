package com.minidofus.model;

import java.util.function.Predicate;

/**
 * Ligne de vue entre deux centres de case. On parcourt exactement les cases traversées par le
 * segment (algorithme « supercover ») ; quand le segment passe pile par un coin, il n'est bloqué
 * que si les deux cases qui se touchent à ce coin bloquent (règle permissive, comme dans Dofus).
 * Les cases de départ et d'arrivée ne bloquent jamais.
 */
public final class LineOfSight {

    private LineOfSight() { }

    public static boolean isClear(Cell from, Cell to, Predicate<Cell> blocks) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int nx = Math.abs(dx);
        int ny = Math.abs(dy);
        int sx = Integer.signum(dx);
        int sy = Integer.signum(dy);
        int px = from.x();
        int py = from.y();
        int ix = 0;
        int iy = 0;
        while (ix < nx || iy < ny) {
            long decision = (1L + 2L * ix) * ny - (1L + 2L * iy) * nx;
            if (decision == 0) {
                Cell a = new Cell(px + sx, py);
                Cell b = new Cell(px, py + sy);
                if (!a.equals(to) && !b.equals(to) && blocks.test(a) && blocks.test(b)) return false;
                px += sx;
                py += sy;
                ix++;
                iy++;
            } else if (decision < 0) {
                px += sx;
                ix++;
            } else {
                py += sy;
                iy++;
            }
            Cell c = new Cell(px, py);
            if (c.equals(to)) return true;
            if (blocks.test(c)) return false;
        }
        return true;
    }
}
