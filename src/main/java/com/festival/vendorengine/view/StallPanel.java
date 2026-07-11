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

    // Action buttons
    private JButton btnAccept;
    private JButton btnReady;
    private JButton btnServe;
    private JButton btnCancel;

    /**
     * Constructs a panel for the given stall.
     */
    public StallPanel(Stall stall, OrderController controller) {
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

        JLabel lblStallId = new JLabel("Stall ID: " + stall.getStallId());
        lblStallId.setFont(UiTheme.BODY);
        lblStallId.setForeground(UiTheme.FG_MUTED);

        headerPanel.add(lblStallName, BorderLayout.WEST);
        headerPanel.add(lblStallId, BorderLayout.EAST);
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

        // 3. Right/Bottom (Operations Panel)
        JPanel operationsPanel = new JPanel(new BorderLayout(5, 5));
        operationsPanel.setBackground(UiTheme.BG);

        JPanel buttonGrid = new JPanel(new GridLayout(4, 1, 10, 10));
        buttonGrid.setBackground(UiTheme.BG);

        btnAccept = createStyledButton("Accept Order");
        btnReady = createStyledButton("Mark Ready");
        btnServe = createStyledButton("Serve Order");
        btnCancel = createStyledButton("Cancel Order");

        buttonGrid.add(btnAccept);
        buttonGrid.add(btnReady);
        buttonGrid.add(btnServe);
        buttonGrid.add(btnCancel);

        // Add a title to the actions panel
        TitledBorder actionsBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.PANEL), "Actions");
        actionsBorder.setTitleColor(UiTheme.FG_MUTED);
        actionsBorder.setTitleFont(UiTheme.BODY);
        JPanel actionsContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actionsContainer.setBackground(UiTheme.BG);
        actionsContainer.setBorder(actionsBorder);
        actionsContainer.add(buttonGrid);
        
        add(actionsContainer, BorderLayout.EAST);

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
                } else {
                    lastSelectedOrderId = null;
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
                updateButtonStates(order.getStatus());
                return;
            }
        }
        disableAllButtons();
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
