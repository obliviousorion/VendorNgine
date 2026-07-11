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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Main dashboard for the Kitchen Worker showing a single Stall's queue (Section 9.2).
 */
public class KitchenView extends JFrame implements OrderObserver {

    private final Stall stall;
    private final OrderController controller;
    private final StallDataSource dataSource;
    private final AppState appState;

    private StallPanel stallPanel;
    private JPanel bannerPanel;
    private JLabel lblBanner;
    private Timer statusTimer;

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
        setSize(800, 600);
        setMinimumSize(new Dimension(600, 450));
        setLocationRelativeTo(null);

        // Root container
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BG);
        setContentPane(root);

        // 1. Offline Banner Panel (Section 9.2)
        bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBackground(UiTheme.WARN);
        bannerPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        
        lblBanner = new JLabel("OFFLINE — orders cached locally", SwingConstants.CENTER);
        lblBanner.setFont(UiTheme.SUBTITLE);
        lblBanner.setForeground(Color.WHITE);
        bannerPanel.add(lblBanner, BorderLayout.CENTER);
        bannerPanel.setVisible(!appState.isOnline()); // default visibility based on current state
        root.add(bannerPanel, BorderLayout.NORTH);

        // 2. Center Stall Panel
        stallPanel = new StallPanel(stall, controller, () -> {
            cleanup();
            dispose();
            new LoginView(controller, dataSource, appState).setVisible(true);
        });
        root.add(stallPanel, BorderLayout.CENTER);

        // 3. Register as controller observer for updates
        this.controller.registerObserver(this);

        // 4. Polling Timer for Network Offline Banner Status (updates dynamically)
        statusTimer = new Timer(500, e -> updateNetworkBanner());
        statusTimer.start();

        // 5. Cleanup observer on close to avoid thread leaks
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private void updateNetworkBanner() {
        boolean online = appState.isOnline();
        boolean shouldBeVisible = !online;
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
        if (stallPanel != null) {
            stallPanel.cleanup();
        }
        controller.unregisterObserver(this);
    }

    // -------------------------------------------------------------------------
    // OrderObserver implementation (can be used to refresh on updates)
    // -------------------------------------------------------------------------

    @Override
    public void onOrderUpdated(Order order) {
        // Handled by StallPanel's OrderTableModel internally
    }

    @Override
    public void onStallSnapshotChanged(Stall stall) {
        // Handled by StallPanel's OrderTableModel internally
    }
}
