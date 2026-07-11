package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.controller.OrderObserver;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.Stall;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Main dashboard for the Kitchen Worker showing a three-column board layout (Section 9.2).
 */
public class KitchenView extends JFrame implements OrderObserver {

    private final Stall stall;
    private final OrderController controller;
    private final StallDataSource dataSource;
    private final AppState appState;

    private OrderTableModel tableModel;
    private JPanel bannerPanel;
    private JLabel lblBanner;
    private JLabel lblPill;
    private Timer statusTimer;
    private Timer elapsedTimer;

    // Three-column layout components
    private JPanel colPendingList;
    private JPanel colAcceptedList;
    private JPanel colReadyList;
    private JLabel lblPendingCount;
    private JLabel lblAcceptedCount;
    private JLabel lblReadyCount;

    // Completed strip components
    private JPanel completedPanel;
    private JLabel lblCompletedInfo;
    private JPanel completedBodyPanel;
    private JLabel lblCompletedChevron;
    private JScrollPane scrollCompleted;
    private boolean completedExpanded = false;

    /**
     * Constructs a KitchenView window.
     */
    public KitchenView(Stall stall, OrderController controller, StallDataSource dataSource, AppState appState) {
        this.stall = stall;
        this.controller = controller;
        this.dataSource = dataSource;
        this.appState = appState;

        setTitle("Vendor Engine — Kitchen (" + stall.getStallName() + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 650);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        // Root container
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(UiTheme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        // 1. Header panel (Stall Info & Pill & Logout)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UiTheme.PANEL);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel lblStallName = new JLabel(stall.getStallName() + " (" + stall.getStallId() + ")");
        lblStallName.setFont(UiTheme.TITLE);
        lblStallName.setForeground(UiTheme.FG);
        headerPanel.add(lblStallName, BorderLayout.WEST);

        JPanel eastHeader = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 15, 0));
        eastHeader.setBackground(UiTheme.PANEL);

        // Status indicator/pill
        lblPill = new JLabel("Online");
        lblPill.setFont(UiTheme.BODY);
        lblPill.setForeground(UiTheme.OK);
        eastHeader.add(lblPill);

        JButton btnLogout = new JButton("Logout");
        UiTheme.styleButton(btnLogout);
        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to log out?",
                    "Confirm Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                cleanup();
                dispose();
                new LoginView(controller, dataSource, appState).setVisible(true);
            }
        });
        eastHeader.add(btnLogout);
        headerPanel.add(eastHeader, BorderLayout.EAST);

        // 2. Offline Banner Panel
        bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBackground(UiTheme.HOT); // Restyled red banner
        bannerPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        
        lblBanner = new JLabel("Working offline — orders are queueing on this device", SwingConstants.CENTER);
        lblBanner.setFont(UiTheme.SUBTITLE);
        lblBanner.setForeground(Color.WHITE);
        bannerPanel.add(lblBanner, BorderLayout.CENTER);
        bannerPanel.setVisible(!appState.isOnline());
        
        // Put header and banner together in NORTH
        JPanel northContainer = new JPanel(new BorderLayout(5, 5));
        northContainer.setBackground(UiTheme.BG);
        northContainer.add(headerPanel, BorderLayout.NORTH);
        northContainer.add(bannerPanel, BorderLayout.SOUTH);
        root.add(northContainer, BorderLayout.NORTH);

        // 3. Center Board Panel (3 columns)
        JPanel boardPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        boardPanel.setBackground(UiTheme.BG);

        // Column 1: Pending
        JPanel colPending = createColumnPanel("PENDING", lblPendingCount = new JLabel("0"), colPendingList = new JPanel());
        boardPanel.add(colPending);

        // Column 2: Accepted
        JPanel colAccepted = createColumnPanel("ACCEPTED", lblAcceptedCount = new JLabel("0"), colAcceptedList = new JPanel());
        boardPanel.add(colAccepted);

        // Column 3: Ready
        JPanel colReady = createColumnPanel("READY TO SERVE", lblReadyCount = new JLabel("0"), colReadyList = new JPanel());
        boardPanel.add(colReady);

        root.add(boardPanel, BorderLayout.CENTER);

        // 4. South Completed Strip
        completedPanel = new JPanel(new BorderLayout());
        completedPanel.setBackground(UiTheme.PANEL);

        JPanel completedHeaderPanel = new JPanel(new BorderLayout());
        completedHeaderPanel.setBackground(UiTheme.PANEL);
        completedHeaderPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        completedHeaderPanel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        
        lblCompletedInfo = new JLabel("Recently Completed: 0 today");
        lblCompletedInfo.setFont(UiTheme.SUBTITLE);
        lblCompletedInfo.setForeground(UiTheme.FG);
        completedHeaderPanel.add(lblCompletedInfo, BorderLayout.WEST);

        lblCompletedChevron = new JLabel("▼");
        lblCompletedChevron.setFont(UiTheme.TITLE);
        lblCompletedChevron.setForeground(UiTheme.FG_MUTED);
        completedHeaderPanel.add(lblCompletedChevron, BorderLayout.EAST);

        completedHeaderPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                toggleCompletedPanel();
            }
        });

        completedPanel.add(completedHeaderPanel, BorderLayout.NORTH);

        completedBodyPanel = new JPanel();
        completedBodyPanel.setLayout(new javax.swing.BoxLayout(completedBodyPanel, javax.swing.BoxLayout.Y_AXIS));
        completedBodyPanel.setBackground(UiTheme.PANEL);
        completedBodyPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 10, 15));

        scrollCompleted = new JScrollPane(completedBodyPanel);
        scrollCompleted.setBorder(BorderFactory.createEmptyBorder());
        scrollCompleted.getViewport().setBackground(UiTheme.PANEL);
        scrollCompleted.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollCompleted.setPreferredSize(new Dimension(-1, 120));
        scrollCompleted.setVisible(completedExpanded); // collapsed by default

        completedPanel.add(scrollCompleted, BorderLayout.CENTER);
        root.add(completedPanel, BorderLayout.SOUTH);

        // 5. Initialize Table Model and register observers
        tableModel = new OrderTableModel(stall);
        controller.registerObserver(this);
        controller.registerObserver(tableModel);

        // Rebuild/refresh board whenever the model fires updates
        tableModel.addTableModelListener(e -> updateBoard());

        // 6. Polling Timer for Network Offline Banner Status
        statusTimer = new Timer(500, e -> updateNetworkBanner());
        statusTimer.start();

        // 7. Timer to periodically refresh elapsed timer in cards
        elapsedTimer = new Timer(1000, e -> {
            tableModel.refreshOrders();
        });
        elapsedTimer.start();

        // 8. Cleanup on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        // Initial board render
        updateBoard();
    }

    private JPanel createColumnPanel(String title, JLabel lblCount, JPanel listPanel) {
        JPanel col = new JPanel(new BorderLayout(5, 5));
        col.setBackground(UiTheme.BG);

        JPanel colHeader = new JPanel(new BorderLayout());
        colHeader.setBackground(UiTheme.BG);
        colHeader.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UiTheme.SUBTITLE);
        lblTitle.setForeground(UiTheme.FG_MUTED);
        colHeader.add(lblTitle, BorderLayout.WEST);

        lblCount.setFont(UiTheme.BODY);
        lblCount.setForeground(UiTheme.FG);
        colHeader.add(lblCount, BorderLayout.EAST);

        col.add(colHeader, BorderLayout.NORTH);

        listPanel.setLayout(new javax.swing.BoxLayout(listPanel, javax.swing.BoxLayout.Y_AXIS));
        listPanel.setBackground(UiTheme.BG);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UiTheme.BG);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        col.add(scrollPane, BorderLayout.CENTER);

        return col;
    }

    private JPanel createEmptyPlaceholder(String text) {
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setBackground(UiTheme.BG);
        placeholder.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.PANEL, 1),
                BorderFactory.createEmptyBorder(20, 10, 20, 10)
        ));
        
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(UiTheme.BODY);
        lbl.setForeground(UiTheme.FG_MUTED);
        placeholder.add(lbl, BorderLayout.CENTER);
        
        return placeholder;
    }

    private void updateBoard() {
        colPendingList.removeAll();
        colAcceptedList.removeAll();
        colReadyList.removeAll();

        List<Order> pending = tableModel.getPendingOrders();
        List<Order> accepted = tableModel.getAcceptedOrders();
        List<Order> ready = tableModel.getReadyOrders();
        List<Order> completed = tableModel.getCompletedOrders();

        lblPendingCount.setText(String.valueOf(pending.size()));
        lblAcceptedCount.setText(String.valueOf(accepted.size()));
        lblReadyCount.setText(String.valueOf(ready.size()));

        if (pending.isEmpty()) {
            colPendingList.add(createEmptyPlaceholder("No pending orders"));
        } else {
            for (Order o : pending) {
                colPendingList.add(new OrderCardPanel(o, controller, stall.getStallId()));
                colPendingList.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        }

        if (accepted.isEmpty()) {
            colAcceptedList.add(createEmptyPlaceholder("No active orders"));
        } else {
            for (Order o : accepted) {
                colAcceptedList.add(new OrderCardPanel(o, controller, stall.getStallId()));
                colAcceptedList.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        }

        if (ready.isEmpty()) {
            colReadyList.add(createEmptyPlaceholder("No orders ready"));
        } else {
            for (Order o : ready) {
                colReadyList.add(new OrderCardPanel(o, controller, stall.getStallId()));
                colReadyList.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        }

        lblCompletedInfo.setText("Recently Completed: " + completed.size() + " today");

        completedBodyPanel.removeAll();
        if (completed.isEmpty()) {
            JLabel lblEmpty = new JLabel("No completed orders yet today.", SwingConstants.LEFT);
            lblEmpty.setFont(UiTheme.BODY);
            lblEmpty.setForeground(UiTheme.FG_MUTED);
            completedBodyPanel.add(lblEmpty);
        } else {
            for (Order o : completed) {
                completedBodyPanel.add(createCompletedOrderRow(o));
            }
        }
        completedBodyPanel.revalidate();
        completedBodyPanel.repaint();

        colPendingList.revalidate();
        colPendingList.repaint();
        colAcceptedList.revalidate();
        colAcceptedList.repaint();
        colReadyList.revalidate();
        colReadyList.repaint();
    }

    private void toggleCompletedPanel() {
        completedExpanded = !completedExpanded;
        scrollCompleted.setVisible(completedExpanded);
        lblCompletedChevron.setText(completedExpanded ? "▲" : "▼");
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private JPanel createCompletedOrderRow(Order order) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(UiTheme.PANEL);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BG),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)
        ));

        String shortId = order.getOrderId();
        if (shortId.length() > 8) {
            shortId = shortId.substring(0, 8);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.getItems().size(); i++) {
            Order.LineItem item = order.getItems().get(i);
            sb.append(item.getItemName()).append(" x").append(item.getQuantity());
            if (i < order.getItems().size() - 1) {
                sb.append(", ");
            }
        }

        JLabel lblDetails = new JLabel("Order #" + shortId + " — " + sb.toString());
        lblDetails.setFont(UiTheme.BODY);
        lblDetails.setForeground(UiTheme.FG);
        row.add(lblDetails, BorderLayout.WEST);

        JLabel lblStatus = new JLabel(order.getStatus().toString());
        lblStatus.setFont(UiTheme.MONOSPACE.deriveFont(java.awt.Font.BOLD, 10f));
        if (order.getStatus() == OrderStatus.SERVED) {
            lblStatus.setForeground(UiTheme.OK);
        } else {
            lblStatus.setForeground(UiTheme.HOT);
        }
        row.add(lblStatus, BorderLayout.EAST);

        return row;
    }

    private void updateNetworkBanner() {
        boolean online = appState.isOnline();
        boolean shouldBeVisible = !online;
        
        if (online) {
            lblPill.setText("Online");
            lblPill.setForeground(UiTheme.OK);
        } else {
            lblPill.setText("Offline");
            lblPill.setForeground(UiTheme.HOT);
            int cachedCount = stall.getOrderQueue().size();
            lblBanner.setText("Working offline — orders are queueing on this device (" + cachedCount + " cached)");
        }

        if (bannerPanel.isVisible() != shouldBeVisible) {
            bannerPanel.setVisible(shouldBeVisible);
            revalidate();
            repaint();
        }
    }

    private void cleanup() {
        if (statusTimer != null && statusTimer.isRunning()) {
            statusTimer.stop();
        }
        if (elapsedTimer != null && elapsedTimer.isRunning()) {
            elapsedTimer.stop();
        }
        controller.unregisterObserver(this);
        controller.unregisterObserver(tableModel);
    }

    // -------------------------------------------------------------------------
    // OrderObserver implementation
    // -------------------------------------------------------------------------

    @Override
    public void onOrderUpdated(Order order) {
        // Handled by TableModel which updates itself and notifies this view
    }

    @Override
    public void onStallSnapshotChanged(Stall stall) {
        // Handled by TableModel which updates itself and notifies this view
    }
}
