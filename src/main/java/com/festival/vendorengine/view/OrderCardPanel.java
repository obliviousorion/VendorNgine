package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.PriorityToken;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * A panel representing a single order in card/docket form (Section 9.2).
 */
public class OrderCardPanel extends JPanel {
    private final Order order;
    private final OrderController controller;
    private final String stallId;

    public OrderCardPanel(Order order, OrderController controller, String stallId) {
        this.order = order;
        this.controller = controller;
        this.stallId = stallId;

        setLayout(new BorderLayout(8, 8));
        setBackground(UiTheme.PANEL);
        setOpaque(false); // Translucent notches cutout visibility

        // Apply custom docket-style ticket border
        setBorder(new TicketCardBorder(UiTheme.BG.brighter(), UiTheme.PANEL));

        // Fixed sizing to stack cards cleanly in columns
        setMaximumSize(new Dimension(320, 165));
        setPreferredSize(new Dimension(280, 145));

        // 1. Header (Top Panel)
        JPanel headPanel = new JPanel(new BorderLayout());
        headPanel.setOpaque(false);

        String shortId = order.getOrderId();
        if (shortId.length() > 8) {
            shortId = shortId.substring(0, 8);
        }
        JLabel lblId = new JLabel("#" + shortId);
        lblId.setFont(UiTheme.MONOSPACE.deriveFont(java.awt.Font.BOLD, 12f));
        lblId.setForeground(UiTheme.FG_MUTED);
        headPanel.add(lblId, BorderLayout.WEST);

        // Header Right Panel (contains VIP badge and Radial Ring)
        JPanel rightHead = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightHead.setOpaque(false);

        if (order.getPriorityToken() == PriorityToken.VIP) {
            JPanel vipBadge = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, UiTheme.ACCENT, getWidth(), getHeight(), new Color(0xE8583F));
                    g2.setPaint(gp);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    g2.dispose();
                }
            };
            vipBadge.setOpaque(false);
            vipBadge.setLayout(new BorderLayout());
            vipBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            JLabel vipText = new JLabel("VIP");
            vipText.setFont(UiTheme.MONOSPACE.deriveFont(java.awt.Font.BOLD, 9f));
            vipText.setForeground(new Color(0x241705));
            vipBadge.add(vipText, BorderLayout.CENTER);
            rightHead.add(vipBadge);
        }

        // Docket Ring component (max 90s SLA)
        DocketRingComponent ring = new DocketRingComponent(order, 90000);
        rightHead.add(ring);

        headPanel.add(rightHead, BorderLayout.EAST);
        add(headPanel, BorderLayout.NORTH);

        // 2. Items list in the center
        StringBuilder sb = new StringBuilder("<html>");
        List<Order.LineItem> items = order.getItems();
        for (int i = 0; i < items.size(); i++) {
            Order.LineItem item = items.get(i);
            sb.append("<b>").append(item.getItemName()).append("</b> x").append(item.getQuantity());
            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("</html>");

        JLabel lblItems = new JLabel(sb.toString());
        lblItems.setFont(UiTheme.BODY);
        lblItems.setForeground(UiTheme.FG);
        add(lblItems, BorderLayout.CENTER);

        // 3. Actions Panel
        JPanel actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.setOpaque(false);

        JButton btnAction = new JButton();
        UiTheme.styleButton(btnAction);
        btnAction.setPreferredSize(new Dimension(130, 30));

        if (order.getStatus() == OrderStatus.PENDING) {
            btnAction.setText("Accept order");
            btnAction.addActionListener(e -> transitionOrder(OrderStatus.ACCEPTED));
            actionsPanel.add(btnAction, BorderLayout.WEST);
        } else if (order.getStatus() == OrderStatus.ACCEPTED) {
            btnAction.setText("Mark ready");
            btnAction.addActionListener(e -> transitionOrder(OrderStatus.READY));
            actionsPanel.add(btnAction, BorderLayout.WEST);
        } else if (order.getStatus() == OrderStatus.READY) {
            btnAction.setText("Serve order");
            btnAction.addActionListener(e -> transitionOrder(OrderStatus.SERVED));
            actionsPanel.add(btnAction, BorderLayout.WEST);
        }

        // Cancel Text link button (rare/destructive path)
        if (order.getStatus() != OrderStatus.SERVED && order.getStatus() != OrderStatus.CANCELLED) {
            JButton btnCancel = new JButton("Cancel");
            btnCancel.setBorderPainted(false);
            btnCancel.setContentAreaFilled(false);
            btnCancel.setFocusPainted(false);
            btnCancel.setForeground(UiTheme.HOT);
            btnCancel.setFont(UiTheme.BODY.deriveFont(java.awt.Font.BOLD));
            btnCancel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            btnCancel.addActionListener(e -> transitionOrder(OrderStatus.CANCELLED));
            actionsPanel.add(btnCancel, BorderLayout.EAST);
        }

        add(actionsPanel, BorderLayout.SOUTH);
    }

    private void transitionOrder(OrderStatus next) {
        try {
            controller.transitionStatus(stallId, order.getOrderId(), next);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Transition failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
        super.paintComponent(g);
    }
}
