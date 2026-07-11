package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.controller.OrderObserver;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.Stall;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

/**
 * Admin Panel providing aggregate revenue and queue metrics (Section 9.3).
 */
public class AdminView extends JFrame implements OrderObserver {

    private final OrderController controller;
    private final StallDataSource dataSource;
    private final AppState appState;

    private JList<String> lstStalls;
    private DefaultListModel<String> listModel;

    // Metric Cards
    private JLabel lblStallCount;
    private JLabel lblTotalQueue;
    private JLabel lblTotalRevenue;
    private JLabel lblSyncState;

    // Selection Details
    private JLabel lblDetailName;
    private JLabel lblDetailId;
    private JLabel lblDetailRevenue;
    private JLabel lblDetailQueue;

    private Timer refreshTimer;
    private RevenueChartPanel chartPanel;

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("$#,##0.00");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    /**
     * Constructs the AdminView window.
     */
    public AdminView(OrderController controller, StallDataSource dataSource, AppState appState) {
        this.controller = controller;
        this.dataSource = dataSource;
        this.appState = appState;

        setTitle("Vendor Engine — Merchant Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 650);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        // Root Panel
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(UiTheme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setContentPane(root);

        // Header Title
        JLabel lblTitle = new JLabel("Merchant Operations Center", SwingConstants.LEFT);
        lblTitle.setFont(UiTheme.TITLE);
        lblTitle.setForeground(UiTheme.FG);
        root.add(lblTitle, BorderLayout.NORTH);

        // Left Panel: Stall List (JList)
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBackground(UiTheme.BG);
        leftPanel.setPreferredSize(new Dimension(200, -1));

        TitledBorder listBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.PANEL), "Registered Stalls");
        listBorder.setTitleColor(UiTheme.FG_MUTED);
        listBorder.setTitleFont(UiTheme.SUBTITLE);
        leftPanel.setBorder(listBorder);

        listModel = new DefaultListModel<>();
        lstStalls = new JList<>(listModel);
        lstStalls.setFont(UiTheme.BODY);
        lstStalls.setBackground(UiTheme.PANEL);
        lstStalls.setForeground(UiTheme.FG);
        lstStalls.setSelectionBackground(UiTheme.ACCENT);
        lstStalls.setSelectionForeground(UiTheme.BG);
        lstStalls.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstStalls.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionDetails();
            }
        });

        JScrollPane listScroller = new JScrollPane(lstStalls);
        listScroller.setBorder(BorderFactory.createEmptyBorder());
        leftPanel.add(listScroller, BorderLayout.CENTER);
        root.add(leftPanel, BorderLayout.WEST);

        // Center Panel (Dashboard Metrics + Charts + Details)
        JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
        centerPanel.setBackground(UiTheme.BG);

        // Top inside center: Metrics Grid
        JPanel metricsGrid = new JPanel(new GridLayout(1, 4, 15, 15));
        metricsGrid.setBackground(UiTheme.BG);
        metricsGrid.setPreferredSize(new Dimension(-1, 100));

        JPanel cardStalls = createMetricCard("Active Stalls");
        lblStallCount = (JLabel) cardStalls.getClientProperty("valueLabel");
        metricsGrid.add(cardStalls);

        JPanel cardQueue = createMetricCard("Total Queue Depth");
        lblTotalQueue = (JLabel) cardQueue.getClientProperty("valueLabel");
        metricsGrid.add(cardQueue);

        JPanel cardRevenue = createMetricCard("Total Revenue");
        lblTotalRevenue = (JLabel) cardRevenue.getClientProperty("valueLabel");
        metricsGrid.add(cardRevenue);

        JPanel cardSync = createMetricCard("Network Status");
        lblSyncState = (JLabel) cardSync.getClientProperty("valueLabel");
        metricsGrid.add(cardSync);

        centerPanel.add(metricsGrid, BorderLayout.NORTH);

        // Middle inside center: Details and Live Chart
        JPanel middleGrid = new JPanel(new GridLayout(1, 2, 15, 15));
        middleGrid.setBackground(UiTheme.BG);

        // Selected Stall Details Panel
        JPanel detailsCard = new JPanel(new GridBagLayout());
        detailsCard.setBackground(UiTheme.PANEL);
        TitledBorder detailsBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.BG, 2), "Selected Stall Details");
        detailsBorder.setTitleColor(UiTheme.ACCENT);
        detailsBorder.setTitleFont(UiTheme.SUBTITLE);
        detailsCard.setBorder(detailsBorder);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new java.awt.Insets(10, 15, 10, 15);
        gbc.gridx = 0;

        gbc.gridy = 0;
        lblDetailName = createDetailLabel("Stall Name: Select a Stall");
        detailsCard.add(lblDetailName, gbc);

        gbc.gridy = 1;
        lblDetailId = createDetailLabel("Stall ID: --");
        detailsCard.add(lblDetailId, gbc);

        gbc.gridy = 2;
        lblDetailRevenue = createDetailLabel("Revenue: --");
        detailsCard.add(lblDetailRevenue, gbc);

        gbc.gridy = 3;
        lblDetailQueue = createDetailLabel("Queue Size: --");
        detailsCard.add(lblDetailQueue, gbc);

        middleGrid.add(detailsCard);

        // Live Revenue Chart Panel (Section 9.3 Bonus)
        chartPanel = new RevenueChartPanel();
        middleGrid.add(chartPanel);

        centerPanel.add(middleGrid, BorderLayout.CENTER);
        root.add(centerPanel, BorderLayout.CENTER);

        // Register as controller observer for real-time updates
        controller.registerObserver(this);

        // Timed loop to check flag files and trigger GUI repaint/sync stats
        refreshTimer = new Timer(1000, e -> {
            updateDashboardMetrics();
            chartPanel.appendRevenue(dataSource.getTotalRevenue());
        });
        refreshTimer.start();

        // Populate lists and cards initially
        updateStallList();
        updateDashboardMetrics();

        // Cleanup listener on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private JPanel createMetricCard(String title) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(UiTheme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BG, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UiTheme.BODY);
        lblTitle.setForeground(UiTheme.FG_MUTED);
        card.add(lblTitle, BorderLayout.NORTH);

        JLabel lblValue = new JLabel("--", SwingConstants.LEFT);
        lblValue.setFont(UiTheme.TITLE);
        lblValue.setForeground(UiTheme.FG);
        card.add(lblValue, BorderLayout.CENTER);

        card.putClientProperty("valueLabel", lblValue);
        return card;
    }

    private JLabel createDetailLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UiTheme.BODY);
        lbl.setForeground(UiTheme.FG);
        return lbl;
    }

    private void updateStallList() {
        String selected = lstStalls.getSelectedValue();
        listModel.clear();
        for (String id : dataSource.getAllStallIds()) {
            listModel.addElement(id);
        }
        if (selected != null && listModel.contains(selected)) {
            lstStalls.setSelectedValue(selected, true);
        } else if (!listModel.isEmpty()) {
            lstStalls.setSelectedIndex(0);
        }
    }

    private void updateDashboardMetrics() {
        lblStallCount.setText(String.valueOf(dataSource.getStallCount()));
        lblTotalQueue.setText(String.valueOf(dataSource.getTotalQueueDepth()));
        lblTotalRevenue.setText(CURRENCY_FORMAT.format(dataSource.getTotalRevenue()));

        boolean online = appState.isOnline();
        if (online) {
            lblSyncState.setText("ONLINE");
            lblSyncState.setForeground(UiTheme.OK);
        } else {
            lblSyncState.setText("OFFLINE");
            lblSyncState.setForeground(UiTheme.WARN);
        }

        // Live refresh for selection info
        updateSelectionDetails();
    }

    private void updateSelectionDetails() {
        String selectedStallId = lstStalls.getSelectedValue();
        if (selectedStallId == null) {
            lblDetailName.setText("Stall Name: Select a Stall");
            lblDetailId.setText("Stall ID: --");
            lblDetailRevenue.setText("Revenue: --");
            lblDetailQueue.setText("Queue Size: --");
            return;
        }

        Stall stall = dataSource.getStall(selectedStallId);
        if (stall != null) {
            lblDetailName.setText("Stall Name: " + stall.getStallName());
            lblDetailId.setText("Stall ID: " + stall.getStallId());
            lblDetailRevenue.setText("Revenue: " + CURRENCY_FORMAT.format(stall.getRevenueTotal()));
            lblDetailQueue.setText("Queue Size: " + stall.getOrderQueue().size() + " order(s)");
        }
    }

    private void cleanup() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
        }
        controller.unregisterObserver(this);
    }

    // -------------------------------------------------------------------------
    // OrderObserver implementation (receives EDT event updates)
    // -------------------------------------------------------------------------

    @Override
    public void onOrderUpdated(Order order) {
        updateDashboardMetrics();
    }

    @Override
    public void onStallSnapshotChanged(Stall stall) {
        updateStallList();
        updateDashboardMetrics();
    }

    // -------------------------------------------------------------------------
    // Custom Chart component for Admin view (Section 9.3 Bonus)
    // -------------------------------------------------------------------------

    private static class RevenueChartPanel extends JPanel {
        private final List<Double> revenuePoints = new ArrayList<>();
        private static final int MAX_POINTS = 20;

        public RevenueChartPanel() {
            setBackground(UiTheme.PANEL);
            TitledBorder border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 2), "Total Revenue (Live Chart)");
            border.setTitleColor(UiTheme.ACCENT);
            border.setTitleFont(UiTheme.SUBTITLE);
            setBorder(border);
        }

        public synchronized void appendRevenue(double revenue) {
            revenuePoints.add(revenue);
            if (revenuePoints.size() > MAX_POINTS) {
                revenuePoints.remove(0);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth() - 30;
            int height = getHeight() - 40;
            int xOffset = 15;
            int yOffset = 25;

            // Draw grid background lines
            g2.setColor(new Color(0x35354A));
            for (int i = 0; i <= 4; i++) {
                int y = yOffset + (height * i / 4);
                g2.drawLine(xOffset, y, xOffset + width, y);
            }

            List<Double> pointsCopy;
            synchronized (this) {
                pointsCopy = new ArrayList<>(revenuePoints);
            }

            if (pointsCopy.size() < 2) {
                g2.setColor(UiTheme.FG_MUTED);
                g2.drawString("Gathering data...", getWidth() / 2 - 50, getHeight() / 2);
                return;
            }

            // Find min/max for scale
            double max = 0;
            double min = Double.MAX_VALUE;
            for (double val : pointsCopy) {
                if (val > max) max = val;
                if (val < min) min = val;
            }

            if (max == min) {
                max += 10.0;
                min -= 10.0;
            }
            double range = max - min;

            // Draw Line
            g2.setColor(UiTheme.OK);
            g2.setStroke(new java.awt.BasicStroke(2.5f));

            int[] xPoints = new int[pointsCopy.size()];
            int[] yPoints = new int[pointsCopy.size()];

            for (int i = 0; i < pointsCopy.size(); i++) {
                xPoints[i] = xOffset + (width * i / (pointsCopy.size() - 1));
                double normValue = (pointsCopy.get(i) - min) / range;
                yPoints[i] = yOffset + height - (int) (height * normValue);
            }

            g2.drawPolyline(xPoints, yPoints, pointsCopy.size());

            // Draw Dots
            g2.setColor(UiTheme.ACCENT);
            for (int i = 0; i < pointsCopy.size(); i++) {
                g2.fillOval(xPoints[i] - 4, yPoints[i] - 4, 8, 8);
            }

            // Render current total label
            g2.setColor(UiTheme.FG);
            g2.setFont(UiTheme.BODY);
            String currentVal = CURRENCY_FORMAT.format(pointsCopy.get(pointsCopy.size() - 1));
            g2.drawString("Current: " + currentVal, xOffset + 10, yOffset + 20);
        }
    }
}
