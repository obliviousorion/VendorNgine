package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.controller.OrderObserver;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.Stall;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
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
 *
 * <p>Phase 3 additions:
 * <ul>
 *   <li>Stall {@code JList} now uses {@link StallListCellRenderer} (status dot +
 *       name + ID + queue badge).</li>
 *   <li>Selected-stall detail panel shows a live mini order list instead of four
 *       static labels.</li>
 *   <li>{@link RevenueChartPanel} gains gridlines with ₹ labels, a pulsing
 *       latest-point dot, and ₹ formatting throughout.</li>
 *   <li>New {@link StallRankingPanel} renders a horizontal-bar revenue ranking
 *       sourced from {@link StallDataSource}.</li>
 *   <li>Metric cards now carry an icon label, a monospace numeral, and a small
 *       delta/status caption.</li>
 * </ul>
 */
public class AdminView extends JFrame implements OrderObserver {

    private final OrderController controller;
    private final StallDataSource dataSource;
    private final AppState appState;

    // ── Stall list ────────────────────────────────────────────────────────────
    private JList<Stall> lstStalls;
    private DefaultListModel<Stall> listModel;

    // ── Metric cards — value numerals ─────────────────────────────────────────
    private JLabel lblStallCount;
    private JLabel lblTotalQueue;
    private JLabel lblTotalRevenue;
    private JLabel lblSyncState;

    // ── Metric cards — delta / status captions ────────────────────────────────
    private JLabel lblStallDelta;
    private JLabel lblQueueDelta;
    private JLabel lblRevenueDelta;
    private JLabel lblSyncCaption;

    // Previous-tick values for delta computation
    private int prevStallCount  = -1;
    private int prevQueueDepth  = -1;
    private double prevRevenue  = -1.0;

    // ── Selected-stall mini order list ────────────────────────────────────────
    private DefaultListModel<String> miniOrderModel;
    private JLabel lblSelectedStallHeader;

    // ── Chart + ranking ───────────────────────────────────────────────────────
    private RevenueChartPanel chartPanel;
    private StallRankingPanel rankingPanel;

    // ── Timers ────────────────────────────────────────────────────────────────
    private Timer refreshTimer;

    // ── Formatting ────────────────────────────────────────────────────────────
    /** ₹ currency formatter — shared by chart and metric cards. */
    static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("₹#,##0.00");

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructs the AdminView window.
     *
     * @param controller the order controller (used only for observer registration)
     * @param dataSource the read-only stall façade
     * @param appState   the shared application state (online/offline flag)
     */
    public AdminView(OrderController controller, StallDataSource dataSource, AppState appState) {
        this.controller = controller;
        this.dataSource = dataSource;
        this.appState   = appState;

        setTitle("Vendor Engine — Merchant Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(null);

        // Root
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(UiTheme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildStallList(), BorderLayout.WEST);
        root.add(buildCenter(), BorderLayout.CENTER);

        // Register observer & timer
        controller.registerObserver(this);
        refreshTimer = new Timer(1000, e -> {
            updateDashboardMetrics();
            chartPanel.appendRevenue(dataSource.getTotalRevenue());
            rankingPanel.refresh(dataSource);
        });
        refreshTimer.start();

        updateStallList();
        updateDashboardMetrics();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { cleanup(); }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout builders
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.PANEL, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)));

        JLabel title = new JLabel("Merchant Operations Center", SwingConstants.LEFT);
        title.setFont(UiTheme.TITLE);
        title.setForeground(UiTheme.FG);
        header.add(title, BorderLayout.WEST);

        JButton btnLogout = new JButton("Logout");
        UiTheme.styleButton(btnLogout);
        btnLogout.addActionListener(e -> {
            if (ConfirmLogoutDialog.confirm(this)) {
                cleanup();
                dispose();
                new LoginView(controller, dataSource, appState).setVisible(true);
            }
        });
        header.add(btnLogout, BorderLayout.EAST);
        return header;
    }

    private JPanel buildStallList() {
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBackground(UiTheme.BG);
        left.setPreferredSize(new Dimension(220, -1));

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.PANEL), "Registered Stalls");
        border.setTitleColor(UiTheme.FG_MUTED);
        border.setTitleFont(UiTheme.SUBTITLE);
        left.setBorder(border);

        listModel = new DefaultListModel<>();
        lstStalls = new JList<>(listModel);
        lstStalls.setFont(UiTheme.BODY);
        lstStalls.setBackground(UiTheme.PANEL);
        lstStalls.setForeground(UiTheme.FG);
        lstStalls.setSelectionBackground(UiTheme.ACCENT.darker().darker());
        lstStalls.setSelectionForeground(UiTheme.FG);
        lstStalls.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstStalls.setCellRenderer(new StallListCellRenderer());
        lstStalls.setFixedCellHeight(50);

        lstStalls.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateSelectionDetails();
        });

        JScrollPane scroll = new JScrollPane(lstStalls);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        left.add(scroll, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setBackground(UiTheme.BG);

        // ── Metric cards row ─────────────────────────────────────────────────
        JPanel metricsGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        metricsGrid.setBackground(UiTheme.BG);
        metricsGrid.setPreferredSize(new Dimension(-1, 110));

        MetricCard mcStalls  = createMetricCard("Active Stalls", "🏪");
        lblStallCount = mcStalls.valueLabel;
        lblStallDelta = mcStalls.captionLabel;
        metricsGrid.add(mcStalls.panel);

        MetricCard mcQueue   = createMetricCard("Total Queue Depth", "📋");
        lblTotalQueue = mcQueue.valueLabel;
        lblQueueDelta = mcQueue.captionLabel;
        metricsGrid.add(mcQueue.panel);

        MetricCard mcRevenue = createMetricCard("Total Revenue", "₹");
        lblTotalRevenue = mcRevenue.valueLabel;
        lblRevenueDelta = mcRevenue.captionLabel;
        metricsGrid.add(mcRevenue.panel);

        MetricCard mcSync    = createMetricCard("Network Status", "📡");
        lblSyncState   = mcSync.valueLabel;
        lblSyncCaption = mcSync.captionLabel;
        metricsGrid.add(mcSync.panel);

        center.add(metricsGrid, BorderLayout.NORTH);

        // ── Middle row: detail panel + chart ─────────────────────────────────
        JPanel middle = new JPanel(new GridLayout(1, 2, 12, 0));
        middle.setBackground(UiTheme.BG);
        middle.add(buildDetailPanel());

        chartPanel = new RevenueChartPanel();
        middle.add(chartPanel);

        center.add(middle, BorderLayout.CENTER);

        // ── Bottom: stall revenue ranking ────────────────────────────────────
        rankingPanel = new StallRankingPanel();
        rankingPanel.setPreferredSize(new Dimension(-1, 120));
        center.add(rankingPanel, BorderLayout.SOUTH);

        return center;
    }

    private JPanel buildDetailPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(UiTheme.PANEL);
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.BG, 2), "Selected Stall — Live Orders");
        tb.setTitleColor(UiTheme.ACCENT);
        tb.setTitleFont(UiTheme.SUBTITLE);
        card.setBorder(tb);

        // Header line showing stall name + id
        lblSelectedStallHeader = new JLabel("Select a stall →", SwingConstants.LEFT);
        lblSelectedStallHeader.setFont(UiTheme.BODY);
        lblSelectedStallHeader.setForeground(UiTheme.FG_MUTED);
        lblSelectedStallHeader.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        card.add(lblSelectedStallHeader, BorderLayout.NORTH);

        // Mini order JList
        miniOrderModel = new DefaultListModel<>();
        JList<String> miniList = new JList<>(miniOrderModel);
        miniList.setFont(UiTheme.MONOSPACE);
        miniList.setBackground(UiTheme.PANEL);
        miniList.setForeground(UiTheme.FG);
        miniList.setSelectionBackground(UiTheme.BG);
        miniList.setFixedCellHeight(22);

        JScrollPane scrollMini = new JScrollPane(miniList);
        scrollMini.setBorder(BorderFactory.createLineBorder(UiTheme.BG, 1));
        scrollMini.setBackground(UiTheme.PANEL);
        card.add(scrollMini, BorderLayout.CENTER);

        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metric card factory
    // ─────────────────────────────────────────────────────────────────────────

    /** Lightweight record bundling the three labels that make up a metric card. */
    private static class MetricCard {
        final JPanel panel;
        final JLabel valueLabel;
        final JLabel captionLabel;

        MetricCard(JPanel panel, JLabel valueLabel, JLabel captionLabel) {
            this.panel        = panel;
            this.valueLabel   = valueLabel;
            this.captionLabel = captionLabel;
        }
    }

    private MetricCard createMetricCard(String title, String icon) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setBackground(UiTheme.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BG, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        // Top row: icon + title
        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.setOpaque(false);

        JLabel lblIcon = new JLabel(icon);
        lblIcon.setFont(UiTheme.BODY.deriveFont(16f));
        topRow.add(lblIcon, BorderLayout.WEST);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UiTheme.BODY);
        lblTitle.setForeground(UiTheme.FG_MUTED);
        topRow.add(lblTitle, BorderLayout.CENTER);
        card.add(topRow, BorderLayout.NORTH);

        // Monospace numeral
        JLabel lblValue = new JLabel("--", SwingConstants.LEFT);
        lblValue.setFont(UiTheme.MONOSPACE.deriveFont(Font.BOLD, 22f));
        lblValue.setForeground(UiTheme.FG);
        card.add(lblValue, BorderLayout.CENTER);

        // Delta / status caption
        JLabel lblCaption = new JLabel(" ", SwingConstants.LEFT);
        lblCaption.setFont(UiTheme.MONOSPACE.deriveFont(10f));
        lblCaption.setForeground(UiTheme.FG_MUTED);
        card.add(lblCaption, BorderLayout.SOUTH);

        return new MetricCard(card, lblValue, lblCaption);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data refresh methods
    // ─────────────────────────────────────────────────────────────────────────

    private void updateStallList() {
        // Retain selection by stallId so a refresh doesn't lose the cursor
        Stall selectedBefore = lstStalls.getSelectedValue();
        String selectedIdBefore = (selectedBefore != null) ? selectedBefore.getStallId() : null;

        listModel.clear();
        List<Stall> sorted = new ArrayList<>(dataSource.getAllStalls());
        sorted.sort(Comparator.comparing(Stall::getStallId));
        for (Stall s : sorted) {
            listModel.addElement(s);
        }

        // Restore selection
        if (selectedIdBefore != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getStallId().equals(selectedIdBefore)) {
                    lstStalls.setSelectedIndex(i);
                    break;
                }
            }
        } else if (!listModel.isEmpty()) {
            lstStalls.setSelectedIndex(0);
        }
    }

    private void updateDashboardMetrics() {
        int  stallCount  = dataSource.getStallCount();
        int  queueDepth  = dataSource.getTotalQueueDepth();
        double revenue   = dataSource.getTotalRevenue();
        boolean online   = appState.isOnline();

        // ── Active Stalls ─────────────────────────────────────────────────────
        lblStallCount.setText(String.valueOf(stallCount));
        if (prevStallCount >= 0) {
            int delta = stallCount - prevStallCount;
            lblStallDelta.setText(delta == 0 ? "no change" : (delta > 0 ? "▲ +" : "▼ ") + delta);
            lblStallDelta.setForeground(delta > 0 ? UiTheme.FRESH : delta < 0 ? UiTheme.HOT : UiTheme.FG_MUTED);
        }
        prevStallCount = stallCount;

        // ── Total Queue Depth ─────────────────────────────────────────────────
        lblTotalQueue.setText(String.valueOf(queueDepth));
        if (prevQueueDepth >= 0) {
            int delta = queueDepth - prevQueueDepth;
            lblQueueDelta.setText(delta == 0 ? "stable" : (delta > 0 ? "▲ +" : "▼ ") + delta);
            lblQueueDelta.setForeground(delta > 0 ? UiTheme.WARM : delta < 0 ? UiTheme.FRESH : UiTheme.FG_MUTED);
        }
        prevQueueDepth = queueDepth;

        // ── Total Revenue ─────────────────────────────────────────────────────
        lblTotalRevenue.setText(CURRENCY_FORMAT.format(revenue));
        if (prevRevenue >= 0) {
            double delta = revenue - prevRevenue;
            if (Math.abs(delta) < 0.005) {
                lblRevenueDelta.setText("no change");
                lblRevenueDelta.setForeground(UiTheme.FG_MUTED);
            } else {
                lblRevenueDelta.setText((delta > 0 ? "▲ " : "▼ ") + CURRENCY_FORMAT.format(Math.abs(delta)));
                lblRevenueDelta.setForeground(delta > 0 ? UiTheme.FRESH : UiTheme.HOT);
            }
        }
        prevRevenue = revenue;

        // ── Network Status ────────────────────────────────────────────────────
        if (online) {
            lblSyncState.setText("ONLINE");
            lblSyncState.setForeground(UiTheme.OK);
            lblSyncCaption.setText("synced");
            lblSyncCaption.setForeground(UiTheme.FRESH);
        } else {
            lblSyncState.setText("OFFLINE");
            lblSyncState.setForeground(UiTheme.HOT);
            lblSyncCaption.setText("orders queuing locally");
            lblSyncCaption.setForeground(UiTheme.WARN);
        }

        // Refresh renderer for dot color changes and selection detail
        lstStalls.repaint();
        updateSelectionDetails();
    }

    private void updateSelectionDetails() {
        Stall stall = lstStalls.getSelectedValue();
        if (stall == null) {
            lblSelectedStallHeader.setText("Select a stall →");
            miniOrderModel.clear();
            return;
        }

        lblSelectedStallHeader.setText(
                stall.getStallName() + "  [" + stall.getStallId() + "]   "
                + "Queue: " + stall.getOrderQueue().size()
                + "   Revenue: " + CURRENCY_FORMAT.format(stall.getRevenueTotal()));

        // Snapshot the priority queue without draining it
        List<Order> snapshot = new ArrayList<>(stall.getOrderQueue());
        snapshot.sort(Comparator.comparingInt(Order::getPriority).reversed());

        miniOrderModel.clear();
        if (snapshot.isEmpty()) {
            miniOrderModel.addElement("  (no active orders)");
        } else {
            for (Order o : snapshot) {
                String shortId = o.getOrderId().length() > 8
                        ? o.getOrderId().substring(0, 8) : o.getOrderId();
                String itemSummary = o.getItems().isEmpty() ? "(no items)"
                        : o.getItems().get(0).getItemName()
                          + (o.getItems().size() > 1 ? " +" + (o.getItems().size() - 1) + " more" : "");
                miniOrderModel.addElement(String.format("  #%-8s  %-4s  %s",
                        shortId, o.getStatus().name().substring(0, Math.min(4, o.getStatus().name().length())),
                        itemSummary));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup & observer
    // ─────────────────────────────────────────────────────────────────────────

    private void cleanup() {
        if (refreshTimer != null && refreshTimer.isRunning()) refreshTimer.stop();
        controller.unregisterObserver(this);
    }

    @Override
    public void onOrderUpdated(Order order) {
        updateDashboardMetrics();
    }

    @Override
    public void onStallSnapshotChanged(Stall stall) {
        updateStallList();
        updateDashboardMetrics();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RevenueChartPanel  (Phase 3 upgraded)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Custom {@code Graphics2D} line chart showing total revenue over time.
     *
     * <p>Phase 3 additions over the original:
     * <ul>
     *   <li>Y-axis gridlines with ₹-formatted labels at each gridline.</li>
     *   <li>Pulsing "latest point" indicator — a semi-transparent halo that
     *       oscillates via a secondary {@link Timer} at 60 ms, giving the
     *       illusion of a heartbeat without re-drawing the full chart.</li>
     *   <li>₹ formatting on the current-value string.</li>
     * </ul>
     */
    private static class RevenueChartPanel extends JPanel {

        private final List<Double> revenuePoints = new ArrayList<>();
        private static final int MAX_POINTS = 30;

        /** Halo pulse: 0.0 → 1.0 → 0.0, driven by pulseTimer. */
        private float pulseAlpha = 0f;
        private boolean pulseRising = true;
        private final Timer pulseTimer;

        RevenueChartPanel() {
            setBackground(UiTheme.PANEL);
            TitledBorder border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 2), "Total Revenue (Live)");
            border.setTitleColor(UiTheme.ACCENT);
            border.setTitleFont(UiTheme.SUBTITLE);
            setBorder(border);

            pulseTimer = new Timer(80, e -> {
                pulseAlpha += pulseRising ? 0.06f : -0.06f;
                if (pulseAlpha >= 1f) { pulseAlpha = 1f; pulseRising = false; }
                if (pulseAlpha <= 0f) { pulseAlpha = 0f; pulseRising = true;  }
                repaint();
            });
            pulseTimer.start();
        }

        synchronized void appendRevenue(double revenue) {
            revenuePoints.add(revenue);
            if (revenuePoints.size() > MAX_POINTS) revenuePoints.remove(0);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            final int PAD_LEFT   = 55;
            final int PAD_RIGHT  = 12;
            final int PAD_TOP    = 22;
            final int PAD_BOTTOM = 14;
            final int chartW = getWidth()  - PAD_LEFT - PAD_RIGHT;
            final int chartH = getHeight() - PAD_TOP  - PAD_BOTTOM;

            List<Double> pts;
            synchronized (this) { pts = new ArrayList<>(revenuePoints); }

            // ── Gridlines & Y labels ─────────────────────────────────────────
            final int GRID_LINES = 4;
            double max = 0, min = Double.MAX_VALUE;
            for (double v : pts) { if (v > max) max = v; if (v < min) min = v; }
            if (pts.isEmpty()) { min = 0; max = 100; }
            if (max == min)    { max += 50; min = Math.max(0, min - 50); }
            double range = max - min;

            g2.setFont(UiTheme.MONOSPACE.deriveFont(9f));
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= GRID_LINES; i++) {
                int y = PAD_TOP + (chartH * i / GRID_LINES);
                g2.setColor(new Color(0x3A3A50));
                g2.drawLine(PAD_LEFT, y, PAD_LEFT + chartW, y);

                double labelVal = max - (range * i / GRID_LINES);
                String labelStr = CURRENCY_FORMAT.format(labelVal);
                g2.setColor(UiTheme.FG_MUTED);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(labelStr, PAD_LEFT - fm.stringWidth(labelStr) - 4,
                              y + fm.getAscent() / 2);
            }

            if (pts.size() < 2) {
                g2.setColor(UiTheme.FG_MUTED);
                g2.setFont(UiTheme.BODY);
                g2.drawString("Gathering data...", PAD_LEFT + chartW / 2 - 50, PAD_TOP + chartH / 2);
                g2.dispose();
                return;
            }

            // ── Compute pixel coords ─────────────────────────────────────────
            int n = pts.size();
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = PAD_LEFT + (chartW * i / (n - 1));
                ys[i] = PAD_TOP + chartH - (int) (chartH * (pts.get(i) - min) / range);
            }

            // ── Fill gradient under line ─────────────────────────────────────
            java.awt.GradientPaint grad = new java.awt.GradientPaint(
                    0, PAD_TOP,           new Color(UiTheme.OK.getRed(), UiTheme.OK.getGreen(), UiTheme.OK.getBlue(), 60),
                    0, PAD_TOP + chartH,  new Color(0, 0, 0, 0));
            g2.setPaint(grad);
            int[] fillXs = new int[n + 2];
            int[] fillYs = new int[n + 2];
            System.arraycopy(xs, 0, fillXs, 0, n);
            System.arraycopy(ys, 0, fillYs, 0, n);
            fillXs[n] = xs[n - 1]; fillYs[n] = PAD_TOP + chartH;
            fillXs[n + 1] = xs[0]; fillYs[n + 1] = PAD_TOP + chartH;
            g2.fillPolygon(fillXs, fillYs, n + 2);

            // ── Line ─────────────────────────────────────────────────────────
            g2.setPaint(UiTheme.OK);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(xs, ys, n);

            // ── Regular dots ─────────────────────────────────────────────────
            g2.setColor(UiTheme.ACCENT);
            for (int i = 0; i < n - 1; i++) {
                g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
            }

            // ── Pulsing latest-point halo ─────────────────────────────────────
            int lx = xs[n - 1], ly = ys[n - 1];
            Composite oldComp = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulseAlpha * 0.5f));
            g2.setColor(UiTheme.OK);
            g2.fillOval(lx - 9, ly - 9, 18, 18);
            g2.setComposite(oldComp);

            // Solid inner dot
            g2.setColor(UiTheme.OK);
            g2.fillOval(lx - 4, ly - 4, 8, 8);
            g2.setColor(UiTheme.FG);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(lx - 4, ly - 4, 8, 8);

            // ── Current-value label ───────────────────────────────────────────
            g2.setColor(UiTheme.FG);
            g2.setFont(UiTheme.MONOSPACE.deriveFont(Font.BOLD, 11f));
            g2.drawString("Current: " + CURRENCY_FORMAT.format(pts.get(n - 1)),
                    PAD_LEFT + 6, PAD_TOP + 14);

            g2.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // StallRankingPanel  (new in Phase 3)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Horizontal-bar revenue ranking panel, sorted highest-revenue-first.
     *
     * <p>Data is read via {@link StallDataSource#getAllStalls()} so no mutation
     * methods are required. The panel is refreshed by the parent's 1-second timer.
     */
    private static class StallRankingPanel extends JPanel {

        /** Snapshot of (stallName, revenue) pairs, sorted descending. */
        private List<double[]>  revenueValues = new ArrayList<>();
        private List<String>    stallLabels   = new ArrayList<>();

        StallRankingPanel() {
            setBackground(UiTheme.PANEL);
            TitledBorder border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 2), "Revenue by Stall");
            border.setTitleColor(UiTheme.ACCENT);
            border.setTitleFont(UiTheme.SUBTITLE);
            setBorder(border);
        }

        /** Called from the Swing timer — refreshes data and triggers repaint. */
        void refresh(StallDataSource dataSource) {
            List<Stall> stalls = new ArrayList<>(dataSource.getAllStalls());
            stalls.sort(Comparator.comparingDouble(Stall::getRevenueTotal).reversed());

            List<String> labels = new ArrayList<>();
            List<double[]> values = new ArrayList<>();
            for (Stall s : stalls) {
                labels.add(s.getStallName());
                values.add(new double[]{s.getRevenueTotal()});
            }
            this.stallLabels   = labels;
            this.revenueValues = values;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (stallLabels.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            final int PAD_L  = 12;
            final int PAD_R  = 12;
            final int PAD_T  = 22;  // below titled border
            final int PAD_B  = 8;
            final int LABEL_W = 90;

            int n     = stallLabels.size();
            int totalH = getHeight() - PAD_T - PAD_B;
            int barH   = Math.max(6, (totalH / Math.max(n, 1)) - 4);
            int maxBarW = getWidth() - PAD_L - PAD_R - LABEL_W - 60; // 60 reserved for ₹ value

            // Find max revenue for relative scaling
            double maxRev = 0;
            for (double[] v : revenueValues) if (v[0] > maxRev) maxRev = v[0];
            if (maxRev == 0) maxRev = 1; // avoid / 0

            // Colour cycle: top stall gets FRESH, rest descend
            Color[] palette = {UiTheme.FRESH, UiTheme.WARM, UiTheme.ACCENT,
                               UiTheme.HOT,   UiTheme.FG_MUTED};

            g2.setFont(UiTheme.MONOSPACE.deriveFont(10f));
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < n; i++) {
                int y = PAD_T + i * (barH + 6);
                double rev = revenueValues.get(i)[0];
                int filledW = (int) (maxBarW * rev / maxRev);

                // Stall name label
                g2.setColor(UiTheme.FG_MUTED);
                String label = stallLabels.get(i);
                if (fm.stringWidth(label) > LABEL_W - 4)
                    label = label.substring(0, Math.min(label.length(), 10)) + "…";
                g2.drawString(label, PAD_L, y + barH - 2);

                // Background track
                int barX = PAD_L + LABEL_W;
                g2.setColor(UiTheme.BG);
                g2.fillRoundRect(barX, y, maxBarW, barH, 4, 4);

                // Filled bar
                Color barColor = palette[Math.min(i, palette.length - 1)];
                g2.setColor(barColor);
                if (filledW > 0)
                    g2.fillRoundRect(barX, y, filledW, barH, 4, 4);

                // Revenue label to the right of bar
                String revStr = CURRENCY_FORMAT.format(rev);
                g2.setColor(UiTheme.FG);
                g2.drawString(revStr, barX + maxBarW + 6, y + barH - 2);
            }

            g2.dispose();
        }
    }
}
