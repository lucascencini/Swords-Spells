package com.minidofus.view.fx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/** Flèche dessinée (le pack n'en contient pas) : trajectoire en cloche, orientée dans le sens du vol. */
public final class Arrow implements Effect {

    private final double fromX;
    private final double fromY;
    private final double toX;
    private final double toY;
    private final double arc;
    private final Color fletching;
    private double t;
    private boolean finished;

    public Arrow(double fromX, double fromY, double toX, double toY, double arc, Color fletching) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.arc = arc;
        this.fletching = fletching;
    }

    public void setProgress(double progress) {
        t = progress;
        if (progress >= 1) finished = true;
    }

    @Override
    public boolean update(double dt) {
        return finished;
    }

    @Override
    public void drawWorld(Graphics2D g) {
        if (finished || t <= 0) return;
        double x = fromX + (toX - fromX) * t;
        double y = fromY + (toY - fromY) * t - Math.sin(t * Math.PI) * arc;
        double vx = toX - fromX;
        double vy = (toY - fromY) - Math.cos(t * Math.PI) * Math.PI * arc;
        AffineTransform saved = g.getTransform();
        Stroke savedStroke = g.getStroke();
        g.translate(x, y);
        g.rotate(Math.atan2(vy, vx));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x6B4423));
        g.drawLine(-16, 0, 6, 0);
        Path2D head = new Path2D.Double();
        head.moveTo(11, 0);
        head.lineTo(4, -3.5);
        head.lineTo(4, 3.5);
        head.closePath();
        g.setColor(new Color(0xD8DEE4));
        g.fill(head);
        g.setColor(fletching);
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine(-16, 0, -20, -4);
        g.drawLine(-16, 0, -20, 4);
        g.drawLine(-12, 0, -16, -4);
        g.drawLine(-12, 0, -16, 4);
        g.setStroke(savedStroke);
        g.setTransform(saved);
    }
}
