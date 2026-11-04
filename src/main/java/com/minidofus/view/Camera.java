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

    /** Cadre l'arène dans la zone utile de l'écran (en tenant compte des marges du HUD). */
    public void fit(Rectangle2D worldBounds, int viewWidth, int viewHeight, int marginTop, int marginBottom) {
        this.world = worldBounds;
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

    public void zoomAt(double screenX, double screenY, double factor) {
        double newZoom = Math.max(fitZoom * 0.75, Math.min(fitZoom * 2.4, zoom * factor));
        double wx = (screenX - offsetX) / zoom;
        double wy = (screenY - offsetY) / zoom;
        zoom = newZoom;
        offsetX = screenX - wx * zoom;
        offsetY = screenY - wy * zoom;
        clamp();
    }

    public void pan(double dx, double dy) {
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
