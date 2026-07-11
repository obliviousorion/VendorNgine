package com.festival.vendorengine.view;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;
import com.festival.vendorengine.model.Order;

/**
 * A custom component that draws a radial timer (docket ring) representing the
 * elapsed wait time of an order relative to its SLA threshold.
 */
public class DocketRingComponent extends JComponent {
    private final Order order;
    private final long maxSlaMs;

    public DocketRingComponent(Order order, long maxSlaMs) {
        this.order = order;
        this.maxSlaMs = maxSlaMs;
        setPreferredSize(new Dimension(34, 34));
        setMinimumSize(new Dimension(34, 34));
        setMaximumSize(new Dimension(34, 34));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int strokeWidth = 3;
        int padding = 2;

        int diameter = Math.min(w, h) - strokeWidth - padding * 2;
        int x = (w - diameter) / 2;
        int y = (h - diameter) / 2;

        // 1. Paint underlying gray track
        g2.setColor(new java.awt.Color(0x3A3450));
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.draw(new Ellipse2D.Double(x, y, diameter, diameter));

        // 2. Paint active colored progress arc
        long elapsedMs = order.getElapsedMs();
        double progress = Math.min((double) elapsedMs / maxSlaMs, 1.0);
        double angleExtent = -360.0 * progress;

        g2.setColor(UiTheme.urgencyColor(elapsedMs, maxSlaMs));
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Double(x, y, diameter, diameter, 90, angleExtent, Arc2D.OPEN));

        // 3. Paint center label (elapsed seconds)
        g2.setFont(UiTheme.MONOSPACE.deriveFont(9f));
        g2.setColor(UiTheme.urgencyColor(elapsedMs, maxSlaMs));

        String text = (elapsedMs / 1000) + "s";
        FontMetrics fm = g2.getFontMetrics();
        int textX = (w - fm.stringWidth(text)) / 2;
        int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, textX, textY);

        g2.dispose();
    }
}
