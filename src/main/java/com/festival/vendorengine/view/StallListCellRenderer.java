package com.festival.vendorengine.view;

import com.festival.vendorengine.model.Stall;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

/**
 * Custom cell renderer for the stall {@link JList} in {@link AdminView}.
 *
 * <p>Each row renders:
 * <ol>
 *   <li>A filled circle ("status dot") colored by queue-depth thresholds using
 *       {@link UiTheme#FRESH}, {@link UiTheme#WARM}, or {@link UiTheme#HOT}.</li>
 *   <li>The stall name (bold body font).</li>
 *   <li>The stall ID in muted monospace below the name.</li>
 *   <li>A rounded queue-depth badge pinned to the right edge.</li>
 * </ol>
 *
 * <p>Queue-depth thresholds (chosen to match KitchenView's card urgency feel):
 * <ul>
 *   <li>FRESH — queue &lt; 5 orders</li>
 *   <li>WARM  — queue 5–9 orders</li>
 *   <li>HOT   — queue ≥ 10 orders</li>
 * </ul>
 */
public class StallListCellRenderer extends DefaultListCellRenderer {

    /** Queue depth at or above which a stall is considered WARM. */
    private static final int WARM_THRESHOLD = 5;
    /** Queue depth at or above which a stall is considered HOT. */
    private static final int HOT_THRESHOLD = 10;

    @Override
    public Component getListCellRendererComponent(
            JList<?> list, Object value, int index,
            boolean isSelected, boolean cellHasFocus) {

        // Expect Stall objects in the model
        if (!(value instanceof Stall stall)) {
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

        int queueDepth = stall.getOrderQueue().size();
        Color dotColor = dotColor(queueDepth);

        // ── Outer row panel ───────────────────────────────────────────────────
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(isSelected ? UiTheme.ACCENT.darker().darker() : UiTheme.PANEL);
        row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // ── Left: status dot ──────────────────────────────────────────────────
        StatusDot dot = new StatusDot(dotColor);
        dot.setPreferredSize(new Dimension(10, 10));
        JPanel dotWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        dotWrapper.setOpaque(false);
        dotWrapper.add(dot);
        row.add(dotWrapper, BorderLayout.WEST);

        // ── Center: name + ID stack ───────────────────────────────────────────
        JPanel nameStack = new JPanel(new BorderLayout(0, 1));
        nameStack.setOpaque(false);

        JLabel lblName = new JLabel(stall.getStallName());
        lblName.setFont(UiTheme.BODY.deriveFont(java.awt.Font.BOLD));
        lblName.setForeground(isSelected ? UiTheme.FG : UiTheme.FG);

        JLabel lblId = new JLabel(stall.getStallId());
        lblId.setFont(UiTheme.MONOSPACE.deriveFont(10f));
        lblId.setForeground(UiTheme.FG_MUTED);

        nameStack.add(lblName, BorderLayout.NORTH);
        nameStack.add(lblId, BorderLayout.SOUTH);
        row.add(nameStack, BorderLayout.CENTER);

        // ── Right: queue badge ────────────────────────────────────────────────
        QueueBadge badge = new QueueBadge(queueDepth, dotColor);
        row.add(badge, BorderLayout.EAST);

        return row;
    }

    /** Maps a queue depth to the appropriate urgency color. */
    private static Color dotColor(int depth) {
        if (depth >= HOT_THRESHOLD)  return UiTheme.HOT;
        if (depth >= WARM_THRESHOLD) return UiTheme.WARM;
        return UiTheme.FRESH;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // StatusDot — small filled circle
    // ─────────────────────────────────────────────────────────────────────────

    private static class StatusDot extends JPanel {
        private final Color color;

        StatusDot(Color color) {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(10, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int size = Math.min(getWidth(), getHeight());
            g2.fillOval(0, 0, size, size);
            g2.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QueueBadge — rounded pill with queue count
    // ─────────────────────────────────────────────────────────────────────────

    private static class QueueBadge extends JPanel {
        private final int count;
        private final Color bgColor;

        QueueBadge(int count, Color bgColor) {
            this.count = count;
            this.bgColor = bgColor;
            setOpaque(false);
            setPreferredSize(new Dimension(30, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Pill background
            g2.setColor(bgColor.darker());
            g2.fillRoundRect(0, 1, getWidth() - 1, getHeight() - 2, 10, 10);

            // Count text
            String text = count > 99 ? "99+" : String.valueOf(count);
            g2.setFont(UiTheme.MONOSPACE.deriveFont(java.awt.Font.BOLD, 10f));
            g2.setColor(UiTheme.FG);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }
}
