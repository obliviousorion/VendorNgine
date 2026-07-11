package com.festival.vendorengine.view;

import com.festival.vendorengine.concurrency.OrderProducer;
import com.festival.vendorengine.concurrency.PeakHourSimulator;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.model.AppState;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

/**
 * Floating developer/test tool window for controlling the order simulation.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Speed mode</b>: Slow (4 s/order), Normal (2 s/order), Peak (5/s),
 *       or a custom ms delay</li>
 *   <li><b>Pause / Resume</b>: manual global producer pause via
 *       {@link OrderProducer#setPaused(boolean)}</li>
 *   <li><b>Network toggle</b>: writes {@code "up"} or {@code "down"} to
 *       {@code network.flag}. The {@code NetworkMonitorDaemon} picks it up
 *       within its 1 s heartbeat and flips {@code AppState.online}.
 *       {@code OrderProducer} then automatically idles when the network goes
 *       down and resumes when it comes back — consistent across all views.</li>
 *   <li><b>Order injection</b>: immediately push N orders for a chosen stall
 *       (or all stalls at random) without waiting for the rate timer</li>
 *   <li><b>Live stats</b>: queue depth, generated counter, running state,
 *       and network state — all refreshed every second</li>
 * </ul>
 *
 * <h2>Visibility</h2>
 * Controlled by {@link com.festival.vendorengine.AppConfig#SHOW_SIMULATOR_PANEL}.
 *
 * <p>The window hides on close (does not exit JVM) so simulation continues
 * if the panel is dismissed.
 */
public class SimulatorControlPanel extends JFrame {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final PeakHourSimulator simulator;
    private final OrderProducer producer;
    private final BlockingQueue<String> queue;
    private final StallDataSource dataSource;
    private final AppState appState;

    /** Path to the flag file read by {@code NetworkMonitorDaemon}. */
    private static final Path FLAG_FILE = Paths.get("network.flag");

    // ── Live-stats labels ─────────────────────────────────────────────────────
    private JLabel lblStatus;
    private JLabel lblQueueDepth;
    private JLabel lblGenerated;

    // ── Mode radio buttons ────────────────────────────────────────────────────
    private JRadioButton rbSlow;
    private JRadioButton rbNormal;
    private JRadioButton rbPeak;
    private JRadioButton rbCustom;
    private JTextField txtCustomMs;

    // ── Pause/Resume button ───────────────────────────────────────────────────
    private JButton btnPauseResume;

    // ── Network toggle button ─────────────────────────────────────────────────
    private JButton btnNetwork;

    // ── Injection controls ────────────────────────────────────────────────────
    private JComboBox<String> comboStall;
    private JSpinner spinCount;

    // ── Refresh timer ─────────────────────────────────────────────────────────
    private Timer refreshTimer;

    private static final String ALL_STALLS = "All stalls (random)";

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates the Simulator Control Panel.
     *
     * @param simulator  the active peak-hour simulator (rate changes go here)
     * @param producer   the active order producer (pause/resume goes here)
     * @param queue      the shared ingestion queue (for live queue-depth display)
     * @param dataSource the stall registry (supplies stall names for injection)
     * @param appState   the shared application state (online flag + network toggle)
     */
    public SimulatorControlPanel(PeakHourSimulator simulator,
                                  OrderProducer producer,
                                  BlockingQueue<String> queue,
                                  StallDataSource dataSource,
                                  AppState appState) {
        this.simulator  = simulator;
        this.producer   = producer;
        this.queue      = queue;
        this.dataSource = dataSource;
        this.appState   = appState;

        setTitle("⚙ Simulator Control");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); // hide, don't exit JVM
        setResizable(false);
        setSize(480, 440);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(UiTheme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        setContentPane(root);

        root.add(buildStatsBar());
        root.add(Box.createRigidArea(new Dimension(0, 10)));
        root.add(buildModePanel());
        root.add(Box.createRigidArea(new Dimension(0, 8)));
        root.add(buildControlRow());
        root.add(Box.createRigidArea(new Dimension(0, 10)));
        root.add(buildInjectPanel());

        // Ensure flag file is in a known state on startup
        ensureFlagFile();

        // 1-second live-stats + network-state refresh
        refreshTimer = new Timer(1000, e -> refreshStats());
        refreshTimer.start();
        refreshStats(); // paint immediately before first tick
    }

    // =========================================================================
    // Panel builders
    // =========================================================================

    /** Top bar: producer status + queue depth + generated counter. */
    private JPanel buildStatsBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        bar.setBackground(UiTheme.PANEL);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.ACCENT, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        lblStatus = new JLabel("● RUNNING");
        lblStatus.setFont(UiTheme.MONOSPACE.deriveFont(Font.BOLD, 12f));
        lblStatus.setForeground(UiTheme.OK);

        JLabel sepA = sep();
        lblQueueDepth = new JLabel("Queue: 0");
        lblQueueDepth.setFont(UiTheme.MONOSPACE.deriveFont(12f));
        lblQueueDepth.setForeground(UiTheme.FG_MUTED);

        JLabel sepB = sep();
        lblGenerated = new JLabel("Generated: 0");
        lblGenerated.setFont(UiTheme.MONOSPACE.deriveFont(12f));
        lblGenerated.setForeground(UiTheme.FG_MUTED);

        bar.add(lblStatus);
        bar.add(sepA);
        bar.add(lblQueueDepth);
        bar.add(sepB);
        bar.add(lblGenerated);
        return bar;
    }

    /** Speed-mode radio button group + custom delay field. */
    private JPanel buildModePanel() {
        JPanel panel = styledSection("SPEED MODE");
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 4, 3, 16);

        ButtonGroup group = new ButtonGroup();
        rbSlow   = radio("Slow  (4 s/order)");
        rbNormal = radio("Normal  (2 s/order)");
        rbPeak   = radio("Peak  (5/sec)");
        rbCustom = radio("Custom delay:");

        rbNormal.setSelected(true); // default matches Main.java default

        group.add(rbSlow);
        group.add(rbNormal);
        group.add(rbPeak);
        group.add(rbCustom);

        gbc.gridx = 0; gbc.gridy = 0; panel.add(rbSlow, gbc);
        gbc.gridx = 1; panel.add(rbNormal, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(rbPeak, gbc);
        JPanel customRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        customRow.setOpaque(false);
        customRow.add(rbCustom);
        txtCustomMs = new JTextField("2000", 6);
        txtCustomMs.setBackground(UiTheme.BG);
        txtCustomMs.setForeground(UiTheme.FG);
        txtCustomMs.setFont(UiTheme.MONOSPACE.deriveFont(12f));
        txtCustomMs.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.ACCENT),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        customRow.add(txtCustomMs);
        JLabel msLabel = new JLabel("ms");
        msLabel.setFont(UiTheme.BODY);
        msLabel.setForeground(UiTheme.FG_MUTED);
        customRow.add(msLabel);
        gbc.gridx = 1; panel.add(customRow, gbc);

        JButton btnApply = new JButton("Apply");
        btnApply.setFont(UiTheme.MONOSPACE.deriveFont(11f));
        btnApply.setBackground(UiTheme.PANEL);
        btnApply.setForeground(UiTheme.ACCENT);
        btnApply.setFocusPainted(false);
        btnApply.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.ACCENT),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        btnApply.addActionListener(e -> applyMode());
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(6, 4, 2, 4);
        panel.add(btnApply, gbc);
        return panel;
    }

    /**
     * Row containing two large buttons side-by-side:
     * <ul>
     *   <li>Pause / Resume — manual producer pause</li>
     *   <li>Network Up / Network Down — writes {@code network.flag}</li>
     * </ul>
     */
    private JPanel buildControlRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        // ── Pause / Resume ────────────────────────────────────────────────────
        btnPauseResume = new JButton("⏸  Pause");
        btnPauseResume.setFont(UiTheme.SUBTITLE.deriveFont(Font.BOLD));
        btnPauseResume.setBackground(UiTheme.PANEL);
        btnPauseResume.setForeground(UiTheme.WARN);
        btnPauseResume.setFocusPainted(false);
        btnPauseResume.setPreferredSize(new Dimension(165, 36));
        btnPauseResume.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.WARN, 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btnPauseResume.addActionListener(e -> togglePause());

        // ── Network toggle ────────────────────────────────────────────────────
        // Label and color are updated by refreshStats() to match actual flag state.
        btnNetwork = new JButton("🌐  Network Down");
        btnNetwork.setFont(UiTheme.SUBTITLE.deriveFont(Font.BOLD));
        btnNetwork.setBackground(UiTheme.PANEL);
        btnNetwork.setForeground(UiTheme.HOT);
        btnNetwork.setFocusPainted(false);
        btnNetwork.setPreferredSize(new Dimension(190, 36));
        btnNetwork.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.HOT, 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btnNetwork.addActionListener(e -> toggleNetwork());

        row.add(btnPauseResume);
        row.add(btnNetwork);
        return row;
    }

    /** Manual order injection section. */
    private JPanel buildInjectPanel() {
        JPanel panel = styledSection("INJECT ORDERS");
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblStall = new JLabel("Target stall:");
        lblStall.setFont(UiTheme.BODY);
        lblStall.setForeground(UiTheme.FG_MUTED);
        panel.add(lblStall, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        comboStall = new JComboBox<>();
        comboStall.setBackground(UiTheme.BG);
        comboStall.setForeground(UiTheme.FG);
        comboStall.setFont(UiTheme.MONOSPACE.deriveFont(12f));
        refreshStallCombo();
        panel.add(comboStall, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel lblCount = new JLabel("Quantity:");
        lblCount.setFont(UiTheme.BODY);
        lblCount.setForeground(UiTheme.FG_MUTED);
        panel.add(lblCount, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        JPanel injectRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        injectRow.setOpaque(false);

        spinCount = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        spinCount.setFont(UiTheme.MONOSPACE.deriveFont(12f));
        ((JSpinner.DefaultEditor) spinCount.getEditor()).getTextField()
                .setBackground(UiTheme.BG);
        ((JSpinner.DefaultEditor) spinCount.getEditor()).getTextField()
                .setForeground(UiTheme.FG);
        spinCount.setPreferredSize(new Dimension(70, 28));
        injectRow.add(spinCount);

        JButton btnInject = new JButton("⚡  Inject Now");
        btnInject.setFont(UiTheme.BUTTON);
        btnInject.setBackground(UiTheme.ACCENT);
        btnInject.setForeground(new Color(0x241705));
        btnInject.setFocusPainted(false);
        btnInject.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.ACCENT),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        btnInject.addActionListener(e -> injectOrders());
        injectRow.add(btnInject);

        panel.add(injectRow, gbc);
        return panel;
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    /** Applies the selected speed mode to the simulator. */
    private void applyMode() {
        if (rbSlow.isSelected()) {
            simulator.setSlowMode();
        } else if (rbNormal.isSelected()) {
            simulator.setNormalMode();
        } else if (rbPeak.isSelected()) {
            simulator.setPeakMode();
        } else if (rbCustom.isSelected()) {
            try {
                long ms = Long.parseLong(txtCustomMs.getText().trim());
                if (ms > 0) {
                    simulator.setCustomDelayMs(ms);
                    // reset border in case it was in error state
                    txtCustomMs.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(UiTheme.ACCENT),
                            BorderFactory.createEmptyBorder(2, 4, 2, 4)));
                }
            } catch (NumberFormatException ex) {
                txtCustomMs.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UiTheme.HOT),
                        BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            }
        }
    }

    /** Toggles the manual producer pause flag. */
    private void togglePause() {
        boolean nowPaused = !producer.isPaused();
        producer.setPaused(nowPaused);
        syncPauseButton(nowPaused);
    }

    /**
     * Reads the current flag state and writes the opposite value to
     * {@code network.flag}. The {@code NetworkMonitorDaemon} reads this
     * file on its 1 s heartbeat and will flip {@code AppState.online}
     * accordingly within that window. {@code OrderProducer} reacts to the
     * {@code AppState} change and automatically pauses/resumes.
     */
    private void toggleNetwork() {
        boolean currentlyOnline = readFlagFile();
        writeFlagFile(!currentlyOnline);
        // Immediately update button appearance; stats refresh will confirm
        syncNetworkButton(!currentlyOnline);
    }

    /**
     * Injects {@code spinCount} orders into the queue immediately, bypassing
     * the rate timer. Works even when the producer is paused or the network
     * is down (useful for seeding the board with test data).
     */
    private void injectOrders() {
        String selected = (String) comboStall.getSelectedItem();
        int count = (int) spinCount.getValue();
        for (int i = 0; i < count; i++) {
            String payload;
            if (selected == null || selected.equals(ALL_STALLS)) {
                payload = simulator.nextPayloadNoSleep();
            } else {
                String stallId = extractStallId(selected);
                payload = simulator.nextPayloadForStall(stallId);
            }
            queue.offer(payload); // non-blocking; drops if queue is at capacity
        }
    }

    // =========================================================================
    // Refresh / sync helpers
    // =========================================================================

    /** Called every second by the refresh timer. Updates all live labels. */
    private void refreshStats() {
        // ── Producer status ───────────────────────────────────────────────────
        boolean online       = (appState != null) && appState.isOnline();
        boolean manualPaused = producer.isPaused();
        boolean effectivelyIdle = producer.isEffectivelyIdle();

        if (!online) {
            lblStatus.setText("⛔ NETWORK DOWN  (idle)");
            lblStatus.setForeground(UiTheme.HOT);
        } else if (manualPaused) {
            lblStatus.setText("⏸ PAUSED  (manual)");
            lblStatus.setForeground(UiTheme.WARN);
        } else {
            lblStatus.setText("● RUNNING");
            lblStatus.setForeground(UiTheme.OK);
        }

        lblQueueDepth.setText("Queue: " + queue.size());
        lblGenerated.setText("Generated: " + String.format("%,d", producer.getOrdersEnqueued()));

        // ── Button states ─────────────────────────────────────────────────────
        syncPauseButton(manualPaused);
        syncNetworkButton(online);
        refreshStallCombo();
    }

    /** Refreshes the stall combo with the current registry. */
    private void refreshStallCombo() {
        String current = (String) comboStall.getSelectedItem();
        List<String> ids = dataSource.getAllStallIds();

        java.util.List<String> desired = new java.util.ArrayList<>();
        desired.add(ALL_STALLS);
        for (String id : ids) {
            com.festival.vendorengine.model.Stall stall = dataSource.getStall(id);
            String label = stall != null
                    ? stall.getStallName() + "  (" + id + ")"
                    : id;
            desired.add(label);
        }

        boolean changed = comboStall.getItemCount() != desired.size();
        if (!changed) {
            for (int i = 0; i < desired.size(); i++) {
                if (!desired.get(i).equals(comboStall.getItemAt(i))) { changed = true; break; }
            }
        }
        if (changed) {
            comboStall.removeAllItems();
            for (String item : desired) comboStall.addItem(item);
            if (current != null) {
                for (int i = 0; i < comboStall.getItemCount(); i++) {
                    if (comboStall.getItemAt(i).equals(current)) {
                        comboStall.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    /** Updates pause/resume button to match current manual-pause state. */
    private void syncPauseButton(boolean paused) {
        if (paused) {
            btnPauseResume.setText("▶  Resume");
            btnPauseResume.setForeground(UiTheme.OK);
            btnPauseResume.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.OK, 1),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        } else {
            btnPauseResume.setText("⏸  Pause");
            btnPauseResume.setForeground(UiTheme.WARN);
            btnPauseResume.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.WARN, 1),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        }
    }

    /**
     * Updates the network button label/color to show what clicking it <em>will do</em>:
     * when online → button says "Network Down" (red); when offline → "Network Up" (green).
     *
     * @param currentlyOnline the current network state read from the flag / AppState
     */
    private void syncNetworkButton(boolean currentlyOnline) {
        if (currentlyOnline) {
            // Network is UP — clicking will bring it DOWN
            btnNetwork.setText("🌐  Network Down");
            btnNetwork.setForeground(UiTheme.HOT);
            btnNetwork.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.HOT, 1),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        } else {
            // Network is DOWN — clicking will bring it UP
            btnNetwork.setText("🌐  Network Up");
            btnNetwork.setForeground(UiTheme.OK);
            btnNetwork.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.OK, 1),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        }
    }

    // =========================================================================
    // Flag file I/O
    // =========================================================================

    /**
     * Writes {@code "up"} or {@code "down"} to {@code network.flag}.
     * The {@code NetworkMonitorDaemon} reads this file every
     * {@code HEARTBEAT_INTERVAL_MS} (1 s) and will react within that window.
     *
     * @param up {@code true} → write {@code "up"}; {@code false} → write {@code "down"}
     */
    private void writeFlagFile(boolean up) {
        try {
            Files.writeString(FLAG_FILE, up ? "up" : "down", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[SimulatorControlPanel] Could not write network.flag: " + e.getMessage());
        }
    }

    /**
     * Reads {@code network.flag} and returns {@code true} if it contains
     * {@code "up"} (case-insensitive). Returns {@code false} on any I/O error
     * or missing file.
     */
    private boolean readFlagFile() {
        try {
            if (!Files.exists(FLAG_FILE)) return false;
            String content = Files.readString(FLAG_FILE, StandardCharsets.UTF_8).trim();
            return content.equalsIgnoreCase("up");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ensures {@code network.flag} exists with a sensible initial value.
     * If the file is missing, writes {@code "up"} so the app starts online.
     */
    private void ensureFlagFile() {
        if (!Files.exists(FLAG_FILE)) {
            writeFlagFile(true);
        }
    }

    // =========================================================================
    // Misc helpers
    // =========================================================================

    /** Extracts bare stall ID from a combo label of the form {@code "Name  (SXX)"}. */
    private static String extractStallId(String label) {
        int open  = label.lastIndexOf('(');
        int close = label.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return label.substring(open + 1, close).trim();
        }
        return label.trim();
    }

    /** Creates a section panel with a titled border styled to UiTheme. */
    private JPanel styledSection(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(UiTheme.PANEL);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.ACCENT, 1), title);
        border.setTitleFont(UiTheme.MONOSPACE.deriveFont(Font.BOLD, 10f));
        border.setTitleColor(UiTheme.ACCENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                border, BorderFactory.createEmptyBorder(4, 6, 6, 6)));
        return panel;
    }

    /** Creates a styled radio button. */
    private JRadioButton radio(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setFont(UiTheme.BODY);
        rb.setForeground(UiTheme.FG);
        rb.setBackground(UiTheme.PANEL);
        rb.setFocusPainted(false);
        return rb;
    }

    /** Creates a muted vertical separator label. */
    private static JLabel sep() {
        JLabel s = new JLabel("│");
        s.setForeground(UiTheme.FG_MUTED);
        return s;
    }
}
