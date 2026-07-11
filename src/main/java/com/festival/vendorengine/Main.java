package com.festival.vendorengine;

import com.festival.vendorengine.concurrency.ExecutorServiceManager;
import com.festival.vendorengine.concurrency.MockDataLoader;
import com.festival.vendorengine.concurrency.OrderConsumer;
import com.festival.vendorengine.concurrency.OrderProducer;
import com.festival.vendorengine.concurrency.PeakHourSimulator;
import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.io.NetworkMonitorDaemon;
import com.festival.vendorengine.io.OfflineSerializer;
import com.festival.vendorengine.io.SyncClient;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Stall;
import com.festival.vendorengine.view.LoginView;
import com.festival.vendorengine.view.SimulatorControlPanel;
import com.festival.vendorengine.view.UiTheme;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.SwingUtilities;

/**
 * Main application entry point (Section 6 &amp; 9.5).
 *
 * <p>Key wiring changes:
 * <ul>
 *   <li>The stall ID pool is extracted from the loaded {@code stallMap} and
 *       passed to {@link PeakHourSimulator} — simulator and data file stay
 *       in sync automatically.</li>
 *   <li>If {@link AppConfig#SHOW_SIMULATOR_PANEL} is {@code true}, a floating
 *       {@link SimulatorControlPanel} is opened on the EDT alongside the
 *       {@link LoginView}.</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) {
        // Redirect System.out and System.err to both console and log/app.log
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("log"));
            final java.io.PrintStream consoleOut = System.out;
            final java.io.PrintStream consoleErr = System.err;
            final java.io.FileOutputStream logFile = new java.io.FileOutputStream("log/app.log", true);
            
            System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
                @Override
                public void write(int b) throws java.io.IOException {
                    consoleOut.write(b);
                    logFile.write(b);
                }
                @Override
                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    consoleOut.write(b, off, len);
                    logFile.write(b, off, len);
                }
                @Override
                public void flush() throws java.io.IOException {
                    consoleOut.flush();
                    logFile.flush();
                }
                @Override
                public void close() throws java.io.IOException {
                    consoleOut.close();
                    logFile.close();
                }
            }, true, "UTF-8"));
            
            System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
                @Override
                public void write(int b) throws java.io.IOException {
                    consoleErr.write(b);
                    logFile.write(b);
                }
                @Override
                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    consoleErr.write(b, off, len);
                    logFile.write(b, off, len);
                }
                @Override
                public void flush() throws java.io.IOException {
                    consoleErr.flush();
                    logFile.flush();
                }
                @Override
                public void close() throws java.io.IOException {
                    consoleErr.close();
                    logFile.close();
                }
            }, true, "UTF-8"));
        } catch (Exception e) {
            System.err.println("Warning: Could not redirect logs to log/app.log: " + e.getMessage());
        }

        // 1. Apply UiTheme as the very first line before any Swing frame is built (Section 9.5)
        UiTheme.apply();

        System.out.println("[Main] Initializing Vendor Engine...");

        // Parse simulation mode from command-line args (optional; SimulatorControlPanel
        // lets you change rate at runtime without restarting).
        // Accepted values: "slow" | "normal" | "peak" | <ms>
        String initMode = args.length > 0 ? args[0].toLowerCase() : "normal";

        // 2. Load stall definitions
        ConcurrentHashMap<String, Stall> stallMap;
        try {
            stallMap = MockDataLoader.loadStalls(AppConfig.STALLS_JSON_PATH);
            System.out.println("[Main] Loaded " + stallMap.size() + " stall(s) from " + AppConfig.STALLS_JSON_PATH);
        } catch (Exception e) {
            System.err.println("[Main] Could not load stalls from file. Using built-in defaults. Error: " + e.getMessage());
            stallMap = new ConcurrentHashMap<>();
            stallMap.put("S01", new Stall("S01", "Chaat Corner"));
            stallMap.put("S02", new Stall("S02", "Momo Point"));
            stallMap.put("S03", new Stall("S03", "Dosa Junction"));
            stallMap.put("S04", new Stall("S04", "Kulfi Cart"));
            stallMap.put("S05", new Stall("S05", "Chai Adda"));
        }

        // 3. Initialize Controller, DataSource, and AppState
        OrderController controller = new OrderController(stallMap);
        StallDataSource dataSource = new StallDataSource(stallMap);
        AppState appState = new AppState(stallMap, true, System.currentTimeMillis());

        // 4. Set up the Ingestion Queue (Capacity 1000 per Section 6.3)
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(1000);

        // 5. Build the simulator with the live stall ID pool so it always
        //    matches stalls.json — no more hard-coded array divergence.
        String[] stallIds = stallMap.keySet().toArray(new String[0]);
        PeakHourSimulator simulator = new PeakHourSimulator(stallIds);

        // Apply the initial mode (overridable at runtime via SimulatorControlPanel)
        switch (initMode) {
            case "slow"  -> { simulator.setSlowMode();   System.out.println("[Main] Starting in SLOW mode (4 s/order)."); }
            case "peak"  -> { simulator.setPeakMode();   System.out.println("[Main] Starting in PEAK mode (5/sec)."); }
            case "spike" -> { simulator.setSpikeMode(true); System.out.println("[Main] Starting in SPIKE mode (50/sec)."); }
            default      -> { simulator.setNormalMode(); System.out.println("[Main] Starting in NORMAL mode (2 s/order)."); }
        }
        // Allow a raw ms value as the first arg
        try {
            long ms = Long.parseLong(initMode);
            simulator.setCustomDelayMs(ms);
            System.out.println("[Main] Starting with custom delay " + ms + " ms/order.");
        } catch (NumberFormatException ignored) { /* named mode was already applied */ }

        // 6. Start the producer + 8 consumers
        OrderProducer producer = new OrderProducer(queue, simulator, appState);
        ExecutorServiceManager threadManager = ExecutorServiceManager.getInstance();
        threadManager.submit(producer);
        System.out.println("[Main] Started order producer.");

        for (int i = 0; i < 8; i++) {
            threadManager.submit(new OrderConsumer(queue, controller));
        }
        System.out.println("[Main] Started 8 order consumers.");

        // 7. IO Failover / Network Monitor Daemon (Section 8)
        OfflineSerializer serializer = new OfflineSerializer(AppConfig.OFFLINE_SER_PATH);
        SyncClient syncClient = new SyncClient(AppConfig.SYNC_PAYLOAD_PATH);
        NetworkMonitorDaemon networkMonitor = new NetworkMonitorDaemon(appState, serializer, syncClient);
        networkMonitor.startDaemonThread();
        System.out.println("[Main] Network monitor daemon started.");

        // 8. JVM Shutdown Hook (Section 6.1)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown Hook] Terminating threads gracefully...");
            producer.stop();
            threadManager.shutdown();
            System.out.println("[Shutdown Hook] Shutdown complete.");
        }, "pool-shutdown-hook"));

        // 9. Launch GUI on the EDT (Section 9.5)
        final StallDataSource ds = dataSource; // effectively-final capture for lambda
        SwingUtilities.invokeLater(() -> {
            // Login screen
            LoginView loginView = new LoginView(controller, ds, appState);
            loginView.setVisible(true);
            System.out.println("[Main] LoginView launched on EDT.");

            // Simulator Control Panel (opt-in via AppConfig)
            if (AppConfig.SHOW_SIMULATOR_PANEL) {
                SimulatorControlPanel simPanel =
                        new SimulatorControlPanel(simulator, producer, queue, ds, appState);
                simPanel.setVisible(true);
                System.out.println("[Main] SimulatorControlPanel launched on EDT.");
            }
        });
    }
}
