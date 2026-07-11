package com.festival.vendorengine.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.swing.border.AbstractBorder;

/**
 * A custom border that draws circular cutouts (punch-notches) on the left
 * and right sides of the card, along with a dashed divider separating the header.
 */
public class TicketCardBorder extends AbstractBorder {
    private static final int NOTCH_RADIUS = 8; // 16px diameter cutouts
    private final Color borderColor;
    private final Color backgroundColor;

    public TicketCardBorder(Color borderColor, Color backgroundColor) {
        this.borderColor = borderColor;
        this.backgroundColor = backgroundColor;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Draw rounded outer border
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, width - 1, height - 1, 14, 14);

        // 2. Punch left and right notch circles (filled with parent background color)
        g2.setColor(UiTheme.BG);
        int midY = y + height / 2;
        g2.fillOval(x - NOTCH_RADIUS, midY - NOTCH_RADIUS, NOTCH_RADIUS * 2, NOTCH_RADIUS * 2);
        g2.fillOval(x + width - NOTCH_RADIUS, midY - NOTCH_RADIUS, NOTCH_RADIUS * 2, NOTCH_RADIUS * 2);

        // Draw border arcs for the punch cutouts
        g2.setColor(borderColor);
        g2.drawArc(x - NOTCH_RADIUS, midY - NOTCH_RADIUS, NOTCH_RADIUS * 2, NOTCH_RADIUS * 2, -90, 180);
        g2.drawArc(x + width - NOTCH_RADIUS, midY - NOTCH_RADIUS, NOTCH_RADIUS * 2, NOTCH_RADIUS * 2, 90, 180);

        // 3. Draw dashed divider under header (approx y = 42)
        int dividerY = y + 42;
        g2.setColor(borderColor);
        Stroke dashed = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0);
        g2.setStroke(dashed);
        g2.drawLine(x + NOTCH_RADIUS, dividerY, x + width - NOTCH_RADIUS, dividerY);

        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(12, 16, 12, 16);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = 16;
        insets.top = 12;
        insets.right = 16;
        insets.bottom = 12;
        return insets;
    }
}
