package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.exception.OrderProcessingException;
import com.festival.vendorengine.exception.StallNotFoundException;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.PriorityToken;
import com.festival.vendorengine.model.Stall;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * A panel showing the queue and operations for a single stall (Section 9.2).
 */
public class StallPanel extends JPanel {

    private final Stall stall;
    private final OrderController controller;
    private final OrderTableModel tableModel;
    private final JTable table;
    private final Timer elapsedTimer;
    private String lastSelectedOrderId = null;

    // Detail Panel Components
    private JPanel detailCard;
    private JLabel lblDetailTitle;
    private JLabel lblDetailItems;

    // Action buttons
    private JButton btnAccept;
    private JButton btnReady;
    private JButton btnServe;
    private JButton btnCancel;

    /**
     * Constructs a panel for the given stall.
     */
    public StallPanel(Stall stall, OrderController controller, Runnable logoutCallback) {
        this.stall = stall;
        this.controller = controller;
        this.tableModel = new OrderTableModel(stall);
        this.controller.registerObserver(this.tableModel);

        setLayout(new BorderLayout(10, 10));
        setBackground(UiTheme.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Header (Stall Info)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UiTheme.PANEL);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel lblStallName = new JLabel(stall.getStallName());
        lblStallName.setFont(UiTheme.TITLE);
        lblStallName.setForeground(UiTheme.FG);

        // East header panel for Stall ID and Logout button
        JPanel eastHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        eastHeader.setBackground(UiTheme.PANEL);

        JLabel lblStallId = new JLabel("Stall ID: " + stall.getStallId());
        lblStallId.setFont(UiTheme.BODY);
        lblStallId.setForeground(UiTheme.FG_MUTED);
        eastHeader.add(lblStallId);

        JButton btnLogout = new JButton("Logout");
        UiTheme.styleButton(btnLogout);
        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to log out?",
                    "Confirm Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                logoutCallback.run();
            }
        });
        eastHeader.add(btnLogout);

        headerPanel.add(lblStallName, BorderLayout.WEST);
        headerPanel.add(eastHeader, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Center (Order Table)
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(UiTheme.BODY);
        table.getTableHeader().setFont(UiTheme.SUBTITLE);
        table.getTableHeader().setBackground(UiTheme.PANEL);
        table.getTableHeader().setForeground(UiTheme.FG);
        table.setBackground(UiTheme.PANEL);
        table.setForeground(UiTheme.FG);
        table.setGridColor(UiTheme.BG);
        table.setSelectionBackground(UiTheme.ACCENT.darker().darker());
        table.setSelectionForeground(UiTheme.FG);

        // Apply custom row renderer
        table.setDefaultRenderer(Object.class, new OrderRowRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(UiTheme.PANEL));
        scrollPane.getViewport().setBackground(UiTheme.BG);
        add(scrollPane, BorderLayout.CENTER);

        // 3. Expandable Detail Card (Bottom Panel)
        detailCard = new JPanel(new BorderLayout(20, 10));
        detailCard.setBackground(UiTheme.PANEL);
        detailCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, UiTheme.ACCENT),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        detailCard.setVisible(false); // Hidden by default; slides up on row selection

        // Left section: Title & Ordered Items list
        JPanel infoPanel = new JPanel(new java.awt.GridBagLayout());
        infoPanel.setBackground(UiTheme.PANEL);
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        lblDetailTitle = new JLabel("Order Details");
        lblDetailTitle.setFont(UiTheme.SUBTITLE);
        lblDetailTitle.setForeground(UiTheme.ACCENT);
        gbc.gridy = 0;
        gbc.insets = new java.awt.Insets(0, 0, 4, 0);
        infoPanel.add(lblDetailTitle, gbc);

        lblDetailItems = new JLabel("No items selected");
        lblDetailItems.setFont(UiTheme.BODY);
        lblDetailItems.setForeground(UiTheme.FG);
        gbc.gridy = 1;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        infoPanel.add(lblDetailItems, gbc);

        detailCard.add(infoPanel, BorderLayout.CENTER);

        // Right section: Horizontal row of action buttons
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        actionsPanel.setBackground(UiTheme.PANEL);

        btnAccept = createStyledButton("Accept Order");
        btnReady = createStyledButton("Mark Ready");
        btnServe = createStyledButton("Serve Order");
        btnCancel = createStyledButton("Cancel Order");

        actionsPanel.add(btnAccept);
        actionsPanel.add(btnReady);
        actionsPanel.add(btnServe);
        actionsPanel.add(btnCancel);

        detailCard.add(actionsPanel, BorderLayout.EAST);

        add(detailCard, BorderLayout.SOUTH);

        // 4. Wire selection listener for button state toggling and tracking selected ID
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < table.getRowCount()) {
                    int modelRow = table.convertRowIndexToModel(selectedRow);
                    Order order = tableModel.getOrderAt(modelRow);
                    if (order != null) {
                        lastSelectedOrderId = order.getOrderId();
                    }
                }
                handleSelectionChanged();
            }
        });

        // Restore selection by unique Order ID whenever table data is updated/reordered
        tableModel.addTableModelListener(e -> {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (lastSelectedOrderId != null) {
                    int targetRow = -1;
                    for (int i = 0; i < table.getRowCount(); i++) {
                        int modelRow = table.convertRowIndexToModel(i);
                        Order order = tableModel.getOrderAt(modelRow);
                        if (order != null && lastSelectedOrderId.equals(order.getOrderId())) {
                            targetRow = i;
                            break;
                        }
                    }
                    if (targetRow >= 0) {
                        table.setRowSelectionInterval(targetRow, targetRow);
                    }
                }
            });
        });

        // 5. Wire action listeners for status transitions
        btnAccept.addActionListener(e -> transitionSelected(OrderStatus.ACCEPTED));
        btnReady.addActionListener(e -> transitionSelected(OrderStatus.READY));
        btnServe.addActionListener(e -> transitionSelected(OrderStatus.SERVED));
        btnCancel.addActionListener(e -> transitionSelected(OrderStatus.CANCELLED));

        // Disable all by default
        disableAllButtons();

        // 6. Swing Timer to refresh the "elapsed" column live every 1 second (EDT safe)
        elapsedTimer = new Timer(1000, e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            tableModel.refreshOrders();
        });
        elapsedTimer.start();
    }

    /**
     * Stop background timers to prevent resource leaks when this panel is discarded.
     */
    public void cleanup() {
        if (elapsedTimer != null && elapsedTimer.isRunning()) {
            elapsedTimer.stop();
        }
        controller.unregisterObserver(tableModel);
    }

    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        UiTheme.styleButton(btn);
        btn.setPreferredSize(new Dimension(140, 36));
        return btn;
    }

    private void handleSelectionChanged() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < table.getRowCount()) {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            Order order = tableModel.getOrderAt(modelRow);
            if (order != null) {
                // 1. Update button states
                updateButtonStates(order.getStatus());

                // 2. Populate detail panel info
                String shortId = order.getOrderId();
                if (shortId.length() > 8) {
                    shortId = shortId.substring(0, 8) + "...";
                }
                lblDetailTitle.setText("Order Detail: #" + shortId + " (" + order.getPriority() + " Priority) — [" + order.getStatus() + "]");
                
                StringBuilder sb = new StringBuilder("<html><b>Items Summary:</b> ");
                for (int i = 0; i < order.getItems().size(); i++) {
                    Order.LineItem item = order.getItems().get(i);
                    sb.append(item.getItemName()).append(" x").append(item.getQuantity());
                    if (i < order.getItems().size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append("</html>");
                lblDetailItems.setText(sb.toString());

                // 3. Expand card dynamically
                if (!detailCard.isVisible()) {
                    detailCard.setVisible(true);
                    revalidate();
                    repaint();
                }
                return;
            }
        }
        
        // No row selected: collapse details card
        disableAllButtons();
        if (detailCard.isVisible()) {
            detailCard.setVisible(false);
            revalidate();
            repaint();
        }
    }

    private void updateButtonStates(OrderStatus status) {
        // Reset states based on allowable transitions:
        // PENDING -> ACCEPTED
        // ACCEPTED -> READY
        // READY -> SERVED
        // CANCELLED from any non-terminal state
        switch (status) {
            case PENDING -> {
                btnAccept.setEnabled(true);
                btnReady.setEnabled(false);
                btnServe.setEnabled(false);
                btnCancel.setEnabled(true);
            }
            case ACCEPTED -> {
                btnAccept.setEnabled(false);
                btnReady.setEnabled(true);
                btnServe.setEnabled(false);
                btnCancel.setEnabled(true);
            }
            case READY -> {
                btnAccept.setEnabled(false);
                btnReady.setEnabled(false);
                btnServe.setEnabled(true);
                btnCancel.setEnabled(true);
            }
            case SERVED, CANCELLED -> disableAllButtons();
        }
    }

    private void disableAllButtons() {
        btnAccept.setEnabled(false);
        btnReady.setEnabled(false);
        btnServe.setEnabled(false);
        btnCancel.setEnabled(false);
    }

    private void transitionSelected(OrderStatus nextStatus) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) return;

        int modelRow = table.convertRowIndexToModel(selectedRow);
        Order order = tableModel.getOrderAt(modelRow);
        if (order == null) return;

        try {
            controller.transitionStatus(stall.getStallId(), order.getOrderId(), nextStatus);
            // Refresh table and re-evaluate selection
            handleSelectionChanged();
        } catch (StallNotFoundException ex) {
            JOptionPane.showMessageDialog(this,
                    "Stall not found: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (OrderProcessingException ex) {
            JOptionPane.showMessageDialog(this,
                    "Cannot process order transition: " + ex.getMessage(),
                    "Processing Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Non-static Inner Class: OrderRowRenderer (Section 4.4 & 9.2)
    // -------------------------------------------------------------------------
    
    /**
     * Non-static cell renderer that colors rows based on VIP status and latency.
     * Closes over enclosing StallPanel fields (e.g. {@code stall}) for simple integration.
     */
    private class OrderRowRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            
            // Invoke super to establish standard sizing, alignment, and selections
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            Order order = tableModel.getOrderAt(modelRow);

            if (order != null && !isSelected) {
                OrderStatus status = order.getStatus();
                
                // Color configuration for pending/accepted/ready active orders
                if (status != OrderStatus.SERVED && status != OrderStatus.CANCELLED) {
                    if (order.getPriorityToken() == PriorityToken.VIP) {
                        // Highlight VIP rows in an amber-accent style
                        c.setBackground(new java.awt.Color(0x4C3214)); // Deep amber background
                        c.setForeground(UiTheme.FG);
                    } else if (order.getElapsedMs() > 30000) {
                        // Warning state: order waiting over 30 seconds
                        c.setBackground(new java.awt.Color(0x521F1F)); // Deep red-ish warning background
                        c.setForeground(UiTheme.FG);
                    } else {
                        c.setBackground(UiTheme.PANEL);
                        c.setForeground(UiTheme.FG);
                    }
                } else if (status == OrderStatus.SERVED) {
                    c.setBackground(new java.awt.Color(0x1F3E22)); // Muted green background for served
                    c.setForeground(UiTheme.FG_MUTED);
                } else {
                    c.setBackground(new java.awt.Color(0x2D2D2D)); // Muted gray background for cancelled
                    c.setForeground(UiTheme.FG_MUTED);
                }
            } else if (isSelected) {
                c.setBackground(UiTheme.ACCENT.darker().darker());
                c.setForeground(UiTheme.FG);
            } else {
                c.setBackground(UiTheme.PANEL);
                c.setForeground(UiTheme.FG);
            }

            return c;
        }
    }
}
