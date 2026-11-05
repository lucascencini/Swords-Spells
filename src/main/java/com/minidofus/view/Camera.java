package com.minidofus.view;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** Caméra 2D : déplacement et zoom centré sur le curseur, bornés pour ne jamais perdre l'arène. */
public final class Camera {

    private double offsetX;
    private double offsetY;
    private double zoom = 1;
    private double fitZoom = 1;
    private Rectangle2D world = new Rectangle2D.Double(0, 0, 1, 1);
    private int viewW = 1;
    private int viewH = 1;
    private double targetX = Double.NaN;
    private double targetY = Double.NaN;

    /** Cadre l'arène dans la zone utile de l'écran (en tenant compte des marges du HUD). */
    public void fit(Rectangle2D worldBounds, int viewWidth, int viewHeight, int marginTop, int marginBottom) {
        this.world = worldBounds;
        this.targetX = Double.NaN; // un recadrage annule tout glissement en cours
        this.targetY = Double.NaN;
        this.viewW = viewWidth;
        this.viewH = viewHeight;
        double usableH = Math.max(100, viewHeight - marginTop - marginBottom);
        fitZoom = Math.min((viewWidth - 40) / worldBounds.getWidth(), usableH / worldBounds.getHeight());
        zoom = fitZoom;
        offsetX = viewWidth / 2.0 - worldBounds.getCenterX() * zoom;
        offsetY = marginTop + usableH / 2.0 - worldBounds.getCenterY() * zoom;
    }

    /** Conserve le point central lors d'un redimensionnement de la fenêtre. */
    public void resize(int newW, int newH) {
        offsetX += (newW - viewW) / 2.0;
        offsetY += (newH - viewH) / 2.0;
        viewW = newW;
        viewH = newH;
        clamp();
    }

    /**
     * Glisse en douceur pour amener un point du monde dans la zone centrale de l'écran, seulement s'il
     * en sort (pas de mouvement inutile quand le combattant actif est déjà bien visible).
     */
    public void focus(double wx, double wy, int marginTop, int marginBottom) {
        double sx = offsetX + wx * zoom;
        double sy = offsetY + wy * zoom;
        double left = viewW * 0.22;
        double right = viewW * 0.78;
        double top = marginTop + 40;
        double bottom = viewH - marginBottom - 20;
        double dx = sx < left ? left - sx : sx > right ? right - sx : 0;
        double dy = sy < top ? top - sy : sy > bottom ? bottom - sy : 0;
        if (dx == 0 && dy == 0) return;
        targetX = offsetX + dx;
        targetY = offsetY + dy;
    }

    /** Avance le glissement de la caméra. */
    public void update(double dt) {
        if (Double.isNaN(targetX)) return;
        double k = 1 - Math.exp(-6 * dt);
        offsetX += (targetX - offsetX) * k;
        offsetY += (targetY - offsetY) * k;
        if (Math.abs(targetX - offsetX) < 0.5 && Math.abs(targetY - offsetY) < 0.5) {
            targetX = Double.NaN;
            targetY = Double.NaN;
        }
        clamp();
    }

    public void zoomAt(double screenX, double screenY, double factor) {
        targetX = Double.NaN;
        double newZoom = Math.max(fitZoom * 0.75, Math.min(fitZoom * 2.4, zoom * factor));
        double wx = (screenX - offsetX) / zoom;
        double wy = (screenY - offsetY) / zoom;
        zoom = newZoom;
        offsetX = screenX - wx * zoom;
        offsetY = screenY - wy * zoom;
        clamp();
    }

    public void pan(double dx, double dy) {
        targetX = Double.NaN;
        offsetX += dx;
        offsetY += dy;
        clamp();
    }

    /** Le centre de l'arène reste toujours visible. */
    private void clamp() {
        double cx = offsetX + world.getCenterX() * zoom;
        double cy = offsetY + world.getCenterY() * zoom;
        double limitX = viewW / 2.0 + world.getWidth() * zoom / 2.0 - 80;
        double limitY = viewH / 2.0 + world.getHeight() * zoom / 2.0 - 80;
        double mx = viewW / 2.0;
        double my = viewH / 2.0;
        offsetX += Math.max(mx - limitX, Math.min(mx + limitX, cx)) - cx;
        offsetY += Math.max(my - limitY, Math.min(my + limitY, cy)) - cy;
    }

    public AffineTransform transform() {
        AffineTransform t = new AffineTransform();
        t.translate(offsetX, offsetY);
        t.scale(zoom, zoom);
        return t;
    }

    public Point2D.Double toScreen(double wx, double wy) {
        return new Point2D.Double(offsetX + wx * zoom, offsetY + wy * zoom);
    }

    public Point2D.Double toWorld(double sx, double sy) {
        return new Point2D.Double((sx - offsetX) / zoom, (sy - offsetY) / zoom);
    }

    public double zoom() {
        return zoom;
    }
}
