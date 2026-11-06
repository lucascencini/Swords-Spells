package com.minidofus.view;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * Passage des coordonnées de grille (cases, éventuellement fractionnaires) au repère « monde » du rendu, et
 * retour. Une carte en tuiles utilise la projection isométrique régulière ({@link #ISO}). Une carte illustrée
 * utilise une perspective (homographie) calée sur les quatre coins du plateau peint : la grille épouse
 * alors exactement les bords de l'illustration, avec des cases un peu plus petites au fond qu'à l'avant.
 */
public final class Projection {

    /** Projection isométrique régulière (cases de 70 × 42). */
    public static final Projection ISO = new Projection(new double[] {
            Iso.HALF_W, -Iso.HALF_W, 0,
            Iso.HALF_H, Iso.HALF_H, 0,
            0, 0, 1});

    /** Grille → monde (matrice 3×3, ligne par ligne), et son inverse. */
    private final double[] m;
    private final double[] inv;

    private Projection(double[] m) {
        this.m = m;
        this.inv = invert(m);
    }

    /**
     * Perspective qui envoie les coins extérieurs d'une grille de {@code n} × {@code n} cases sur les points
     * donnés (en unités du monde) : coin du fond, de droite, de devant et de gauche.
     */
    public static Projection fromCorners(Point2D top, Point2D right, Point2D bottom, Point2D left, int n) {
        double lo = -0.5;
        double hi = n - 0.5;
        double[][] src = {{lo, lo}, {hi, lo}, {hi, hi}, {lo, hi}};
        Point2D[] dst = {top, right, bottom, left};
        // 8 inconnues (h33 = 1) : u = (a gx + b gy + c) / (g gx + h gy + 1), idem pour v
        double[][] a = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double x = src[i][0];
            double y = src[i][1];
            double u = dst[i].getX();
            double v = dst[i].getY();
            a[2 * i] = new double[] {x, y, 1, 0, 0, 0, -x * u, -y * u, u};
            a[2 * i + 1] = new double[] {0, 0, 0, x, y, 1, -x * v, -y * v, v};
        }
        double[] h = solve(a);
        return new Projection(new double[] {h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1});
    }

    public double x(double gx, double gy) {
        return (m[0] * gx + m[1] * gy + m[2]) / (m[6] * gx + m[7] * gy + m[8]);
    }

    public double y(double gx, double gy) {
        return (m[3] * gx + m[4] * gy + m[5]) / (m[6] * gx + m[7] * gy + m[8]);
    }

    public Point2D.Double point(double gx, double gy) {
        return new Point2D.Double(x(gx, gy), y(gx, gy));
    }

    /** Coordonnées de grille fractionnaires correspondant à un point du monde. */
    public Point2D.Double toGrid(double wx, double wy) {
        double w = inv[6] * wx + inv[7] * wy + inv[8];
        return new Point2D.Double((inv[0] * wx + inv[1] * wy + inv[2]) / w, (inv[3] * wx + inv[4] * wy + inv[5]) / w);
    }

    /**
     * Taille apparente d'une case à cet endroit, relativement à une case isométrique normale (1 partout en
     * isométrique ; moins de 1 au fond d'une carte en perspective). Sert à réduire personnages et décor.
     */
    public double scaleAt(double gx, double gy) {
        double dx = x(gx + 0.5, gy - 0.5) - x(gx - 0.5, gy + 0.5);
        double dy = y(gx + 0.5, gy - 0.5) - y(gx - 0.5, gy + 0.5);
        return Math.hypot(dx, dy) / (2 * Iso.HALF_W);
    }

    /** Contour d'une case, rétréci de {@code inset} unités du monde (environ). */
    public Path2D cell(int gx, int gy, double inset) {
        double k = 0.5 * (1 - inset / (Iso.HALF_W * scaleAt(gx, gy)));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x(gx - k, gy - k), y(gx - k, gy - k));
        p.lineTo(x(gx + k, gy - k), y(gx + k, gy - k));
        p.lineTo(x(gx + k, gy + k), y(gx + k, gy + k));
        p.lineTo(x(gx - k, gy + k), y(gx - k, gy + k));
        p.closePath();
        return p;
    }

    /** Contour de toute la grille de {@code n} × {@code n} cases. */
    public Path2D outline(int n) {
        double lo = -0.5;
        double hi = n - 0.5;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x(lo, lo), y(lo, lo));
        p.lineTo(x(hi, lo), y(hi, lo));
        p.lineTo(x(hi, hi), y(hi, hi));
        p.lineTo(x(lo, hi), y(lo, hi));
        p.closePath();
        return p;
    }

    // ------------------------------------------------------------------ algèbre

    /** Élimination de Gauss avec pivot partiel sur une matrice augmentée n × (n+1). */
    private static double[] solve(double[][] a) {
        int n = a.length;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            double[] t = a[col];
            a[col] = a[pivot];
            a[pivot] = t;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = a[r][col] / a[col][col];
                for (int c = col; c <= n; c++) a[r][c] -= f * a[col][c];
            }
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = a[i][n] / a[i][i];
        return out;
    }

    private static double[] invert(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        return new double[] {
                A / det, -(b * i - c * h) / det, (b * f - c * e) / det,
                B / det, (a * i - c * g) / det, -(a * f - c * d) / det,
                C / det, -(a * h - b * g) / det, (a * e - b * d) / det};
    }
}
