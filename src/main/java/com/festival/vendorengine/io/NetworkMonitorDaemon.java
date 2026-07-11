package com.festival.vendorengine.io;

import com.festival.vendorengine.exception.OfflineSerializationException;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Autonomous daemon that monitors network connectivity by reading a
 * {@code network.flag} file, and triggers offline-failover or re-sync actions
 * in response to connectivity changes.
 *
 * <h2>Design (Section 8.3)</h2>
 * <p>This class implements {@link Runnable} and is started as a <strong>daemon
 * thread</strong> ({@code thread.setDaemon(true)}) launched <em>outside</em>
 * the 8-thread {@code ExecutorServiceManager} pool. Running outside the pool
 * means busy consumer threads can never starve the heartbeat loop: even if all
 * 8 consumer slots are occupied, this daemon always gets a CPU slice.
 *
 * <h2>Heartbeat simulation (Section 14 — manual-toggle demo)</h2>
 * <p>In a real deployment, {@code pingHeartbeat()} would send an HTTP request
 * to a known endpoint. For the demo, the method reads a one-line file named
 * {@value #FLAG_FILE} in the working directory. The content {@code "up"} means
 * the network is reachable; anything else (or a missing file) means it is not.
 * This lets the presenter simulate a network outage live by editing
 * {@code network.flag} without restarting the application.
 *
 * <h2>State-machine</h2>
 * <ul>
 *   <li><strong>Online → Offline:</strong> saves {@code AppState} via
 *       {@link OfflineSerializer#save} then sets {@code appState.online = false}.</li>
 *   <li><strong>Offline → Online:</strong> sets {@code appState.online = true},
 *       gathers all currently SERVED orders across all stalls, and pushes them
 *       via {@link SyncClient#pushBatch}.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>{@code lastKnownOnline} is {@code volatile}: written and read from the
 * daemon thread only, but {@code AppState.online} (which mirrors it) is read
 * from the EDT, so the visibility is provided by the {@code volatile} field on
 * {@code AppState}.
 *
 * <h2>Interrupt handling</h2>
 * <p>If the thread is interrupted (e.g. during JVM shutdown), the loop exits
 * cleanly and re-sets the interrupt flag so any caller above can also observe it.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class NetworkMonitorDaemon implements Runnable {

    /**
     * File read each heartbeat tick to determine connectivity.
     * Content {@code "up"} (case-insensitive) means online; anything else means offline.
     * Presenter edits this file during the demo to simulate connectivity changes.
     */
    public static final String FLAG_FILE = "network.flag";

    /** Interval between heartbeat checks, in milliseconds. */
    public static final long HEARTBEAT_INTERVAL_MS = 1_000;

    private final AppState         appState;
    private final OfflineSerializer serializer;
    private final SyncClient       syncClient;

    /**
     * {@code volatile} so the latest value is always visible; only the daemon
     * thread writes it. Starts optimistically as {@code true} — the first tick
     * will correct this immediately if the flag file says otherwise.
     */
    private volatile boolean lastKnownOnline = true;

    /**
     * Constructs a {@code NetworkMonitorDaemon}.
     *
     * @param appState   the shared application state (mutated via
     *                   {@link AppState#setOnline(boolean)})
     * @param serializer the offline serializer used to persist state on disconnect
     * @param syncClient the client used to push a batch of served orders on reconnect
     */
    public NetworkMonitorDaemon(AppState appState,
                                OfflineSerializer serializer,
                                SyncClient syncClient) {
        this.appState   = appState;
        this.serializer = serializer;
        this.syncClient = syncClient;
    }

    // -------------------------------------------------------------------------
    // Runnable — daemon loop
    // -------------------------------------------------------------------------

    /**
     * Heartbeat loop. Runs until the thread is interrupted (e.g. JVM shutdown).
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean heartbeatOk = pingHeartbeat();

            if (!heartbeatOk && lastKnownOnline) {
                onNetworkLost();
            } else if (heartbeatOk && !lastKnownOnline) {
                onNetworkRestored();
            }

            lastKnownOnline = heartbeatOk;

            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Restore interrupt status and exit cleanly.
                Thread.currentThread().interrupt();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connectivity probe
    // -------------------------------------------------------------------------

    /**
     * Reads {@value #FLAG_FILE} and returns {@code true} if its content is
     * {@code "up"} (case-insensitive). Returns {@code false} if the file is
     * missing, empty, or contains any other value.
     *
     * <p>This is the Section 14 "manual-toggle" mechanism: the presenter edits
     * the file during the demo to simulate connectivity changes without
     * restarting the application.
     *
     * @return {@code true} if the flag file says the network is up
     */
    boolean pingHeartbeat() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(FLAG_FILE);
            if (!java.nio.file.Files.exists(path)) {
                return false;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            if (bytes.length == 0) {
                return false;
            }

            java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
            int offset = 0;

            // Detect Byte Order Mark (BOM) to select correct Charset
            if (bytes.length >= 2) {
                if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                    charset = java.nio.charset.Charset.forName("UTF-16LE");
                    offset = 2;
                } else if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                    charset = java.nio.charset.Charset.forName("UTF-16BE");
                    offset = 2;
                } else if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                    charset = java.nio.charset.StandardCharsets.UTF_8;
                    offset = 3;
                }
            }

            String content = new String(bytes, offset, bytes.length - offset, charset).replace("\"", "").trim();
            return content.equalsIgnoreCase("up");
        } catch (IOException e) {
            // Missing or unreadable flag file → treat as offline.
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // State-transition handlers
    // -------------------------------------------------------------------------

    /**
     * Called once on the tick when the network first becomes unreachable.
     *
     * <ol>
     *   <li>Saves the full {@link AppState} to the offline backup file.</li>
     *   <li>Sets {@code appState.online = false} so the UI can show an
     *       "Offline" indicator.</li>
     * </ol>
     */
    void onNetworkLost() {
        System.out.println("[NetworkMonitorDaemon] Network lost — saving offline state.");
        try {
            serializer.save(appState);
        } catch (OfflineSerializationException e) {
            System.err.println("[NetworkMonitorDaemon] Could not save offline state: "
                    + e.getMessage());
        }
        appState.setOnline(false);
    }

    /**
     * Called once on the tick when the network first becomes reachable again.
     *
     * <ol>
     *   <li>Sets {@code appState.online = true}.</li>
     *   <li>Collects all currently {@code SERVED} orders across all stalls.</li>
     *   <li>Pushes them to {@link SyncClient#pushBatch}.</li>
     * </ol>
     */
    void onNetworkRestored() {
        System.out.println("[NetworkMonitorDaemon] Network restored — syncing orders.");
        appState.setOnline(true);

        // Collect served orders from all stall queues for the sync batch.
        List<Order> toSync = new ArrayList<>();
        appState.getStallMap().values().forEach(stall -> {
            // PriorityBlockingQueue is safe to iterate (snapshot view) while orders
            // may still be arriving, but consumers won't remove elements during iteration.
            for (Order o : stall.getOrderQueue()) {
                if (o.getStatus() == OrderStatus.SERVED) {
                    toSync.add(o);
                }
            }
        });

        syncClient.pushBatch(toSync);
        System.out.println("[NetworkMonitorDaemon] Pushed " + toSync.size()
                + " order(s) to sync payload.");
    }

    // -------------------------------------------------------------------------
    // Factory — daemon thread creation
    // -------------------------------------------------------------------------

    /**
     * Creates and starts a daemon {@link Thread} wrapping this runnable.
     *
     * <p><strong>Daemon thread rationale:</strong> The JVM exits when only
     * daemon threads remain. Using {@code setDaemon(true)} here means that
     * the heartbeat loop will not prevent a clean application shutdown — no
     * explicit shutdown hook is needed. The thread is launched <em>outside</em>
     * the {@code ExecutorServiceManager}'s fixed 8-thread pool so that a burst
     * of 8 concurrent consumers cannot starve the connectivity monitor.
     *
     * @return the started daemon thread (retained by caller only for testing;
     *         normal production code does not need the reference)
     */
    public Thread startDaemonThread() {
        Thread t = new Thread(this, "network-monitor-daemon");
        t.setDaemon(true); // Must NOT block JVM shutdown (Section 8.3)
        t.start();
        return t;
    }
}
