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
import com.festival.vendorengine.view.UiTheme;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.SwingUtilities;

/**
 * Main application entry point (Section 6 & 9.5).
 */
public class Main {

    public static void main(String[] args) {
        // 1. Call UiTheme.apply() as the very first line, before any Swing frame is constructed (Section 9.5)
        UiTheme.apply();

        System.out.println("[Main] Initializing Vendor Engine...");

        // Parse simulation delay from command-line (defaults to 4000ms for visual verification)
        long delayMs = 4000; 
        if (args.length > 0) {
            try {
                if (args[0].equalsIgnoreCase("normal")) {
                    delayMs = -1; // 10 orders/second
                } else if (args[0].equalsIgnoreCase("spike")) {
                    delayMs = -2; // 50 orders/second
                } else {
                    delayMs = Long.parseLong(args[0]);
                }
            } catch (NumberFormatException e) {
                System.err.println("[Main] Invalid delay argument, using default 4000ms.");
            }
        }

        // 2. Load stall definitions
        ConcurrentHashMap<String, Stall> stallMap;
        try {
            stallMap = MockDataLoader.loadStalls(AppConfig.STALLS_JSON_PATH);
            System.out.println("[Main] Loaded " + stallMap.size() + " stall(s) from " + AppConfig.STALLS_JSON_PATH);
        } catch (Exception e) {
            System.err.println("[Main] Could not load stalls from file. Using default in-memory stalls. Error: " + e.getMessage());
            stallMap = new ConcurrentHashMap<>();
            stallMap.put("S01", new Stall("S01", "Chaat Corner"));
            stallMap.put("S02", new Stall("S02", "Momo Point"));
            stallMap.put("S03", new Stall("S03", "South Indian Delights"));
            stallMap.put("S04", new Stall("S04", "Dessert Parlour"));
        }

        // 3. Initialize Controller, DataSource, and AppState
        OrderController controller = new OrderController(stallMap);
        StallDataSource dataSource = new StallDataSource(stallMap);
        AppState appState = new AppState(stallMap, true, System.currentTimeMillis());

        // 4. Set up the Ingestion Queue (Capacity 1000 per Section 6.3)
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(1000);

        // 5. Initialize the Concurrency Layer (Section 6)
        PeakHourSimulator simulator = new PeakHourSimulator();
        if (delayMs == -2) {
            simulator.setSpikeMode(true);
            System.out.println("[Main] Simulation running at spike rate (50 orders/sec).");
        } else if (delayMs == -1) {
            System.out.println("[Main] Simulation running at default rate (10 orders/sec).");
        } else if (delayMs > 0) {
            simulator.setCustomDelayMs(delayMs);
            System.out.println("[Main] Simulation throttled to 1 order every " + delayMs + " ms.");
        }
        OrderProducer producer = new OrderProducer(queue, simulator);

        // Submit 8 consumer tasks to the ExecutorServiceManager fixed thread pool (Section 6.3)
        ExecutorServiceManager threadManager = ExecutorServiceManager.getInstance();
        for (int i = 0; i < 8; i++) {
            threadManager.submit(new OrderConsumer(queue, controller));
        }
        System.out.println("[Main] Started 8 order consumers in the fixed thread pool.");

        // Start 1 producer thread (runs as a dedicated thread per Section 11)
        Thread producerThread = new Thread(producer, "order-producer");
        producerThread.start();
        System.out.println("[Main] Started dedicated order producer thread.");

        // 6. Initialize IO Failover Components and start Network Monitor Daemon (Section 8)
        OfflineSerializer serializer = new OfflineSerializer(AppConfig.OFFLINE_SER_PATH);
        SyncClient syncClient = new SyncClient(AppConfig.SYNC_PAYLOAD_PATH);
        NetworkMonitorDaemon networkMonitor = new NetworkMonitorDaemon(appState, serializer, syncClient);
        networkMonitor.startDaemonThread(); // Starts outside pool as a daemon thread (Section 8.3)
        System.out.println("[Main] Started autonomous network monitor daemon thread.");

        // 7. Register JVM Shutdown Hook for clean thread pools termination (Section 6.1)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown Hook] Terminating threads gracefully...");
            producer.stop();
            threadManager.shutdown();
            System.out.println("[Shutdown Hook] Shutdown complete.");
        }, "pool-shutdown-hook"));

        // 8. Launch the Swing GUI on the Event Dispatch Thread (EDT) (Section 9.5)
        SwingUtilities.invokeLater(() -> {
            LoginView loginView = new LoginView(controller, dataSource, appState);
            loginView.setVisible(true);
            System.out.println("[Main] LoginView GUI launched on Event Dispatch Thread (EDT).");
        });
    }
}
