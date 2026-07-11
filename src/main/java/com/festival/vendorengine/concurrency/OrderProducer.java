package com.festival.vendorengine.concurrency;

import com.festival.vendorengine.model.AppState;

import java.util.concurrent.BlockingQueue;

/**
 * Producer thread: pulls synthetic JSON order payloads from a
 * {@link PeakHourSimulator} and pushes them onto a shared
 * {@link BlockingQueue}{@code <String>} for the consumer pool to process.
 *
 * <h2>Pause/Resume</h2>
 * <p>Two independent pause mechanisms are supported and are ORed together —
 * both must be "not-paused" for an order to be generated:
 * <ul>
 *   <li><b>Manual pause</b> — {@link #setPaused(boolean)}, toggled by the
 *       {@code SimulatorControlPanel}.</li>
 *   <li><b>Network pause</b> — when {@code AppState.isOnline()} is
 *       {@code false} the producer automatically idles. Orders are not
 *       injected into the queue while the device is offline, matching the
 *       real-world behaviour where new order packets cannot arrive from a
 *       disconnected network. When connectivity is restored the producer
 *       resumes within one idle tick (~50 ms).</li>
 * </ul>
 *
 * <h2>Counter</h2>
 * <p>{@link #getOrdersEnqueued()} returns a running count of payloads
 * successfully delivered to the queue; the control panel displays this as
 * a live "Generated" metric.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class OrderProducer implements Runnable {

    private final BlockingQueue<String> queue;
    private final PeakHourSimulator simulator;

    /**
     * Optional reference to {@code AppState}. When non-null, the producer
     * treats {@code !appState.isOnline()} as an implicit pause: no orders
     * are enqueued while the device reports as offline.
     *
     * <p>{@code volatile} ensures the latest value of {@code AppState.online}
     * (itself {@code volatile}) is always visible to this thread.
     */
    private final AppState appState; // may be null (unit-test path)

    /**
     * Volatile running flag: written by the main/control thread via
     * {@link #stop()}, read by the producer thread.
     */
    private volatile boolean running = true;

    /**
     * Volatile manual-pause flag: when {@code true} the producer loop idles
     * (50 ms sleeps) rather than calling {@link PeakHourSimulator#nextPayload()}.
     * Written by the EDT via {@link #setPaused(boolean)}.
     */
    private volatile boolean paused = false;

    /**
     * Monotonically increasing count of payloads successfully handed to the
     * queue. Volatile so the EDT can read it safely for display.
     */
    private volatile long ordersEnqueued = 0L;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a producer with network-aware pausing.
     *
     * @param queue      the shared ingestion queue
     * @param simulator  the payload source
     * @param appState   shared application state — used to suspend production
     *                   when the device goes offline; pass {@code null} to
     *                   disable network-aware pausing (testing only)
     */
    public OrderProducer(BlockingQueue<String> queue,
                         PeakHourSimulator simulator,
                         AppState appState) {
        this.queue     = queue;
        this.simulator = simulator;
        this.appState  = appState;
    }

    /**
     * Convenience constructor for tests that don't need network-aware pausing.
     * Equivalent to {@code new OrderProducer(queue, simulator, null)}.
     *
     * @param queue     the shared ingestion queue
     * @param simulator the payload source
     */
    public OrderProducer(BlockingQueue<String> queue, PeakHourSimulator simulator) {
        this(queue, simulator, null);
    }

    // -------------------------------------------------------------------------
    // Runnable
    // -------------------------------------------------------------------------

    /**
     * Producer loop: runs until {@link #stop()} is called or the thread is
     * interrupted.
     *
     * <p>The loop idles (50 ms bursts) when either the manual {@link #paused}
     * flag is set <em>or</em> the network is reported offline via
     * {@code AppState.isOnline()}. Both conditions are ORed: either one alone
     * is enough to suspend production.
     */
    @Override
    public void run() {
        while (running) {
            // ── Idle check: manual pause OR network offline ───────────────────
            boolean networkDown = (appState != null) && !appState.isOnline();
            if (paused || networkDown) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
                continue;
            }

            // ── Normal produce path ───────────────────────────────────────────
            try {
                String payload = simulator.nextPayload();
                queue.put(payload);
                ordersEnqueued++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Control API (called from the EDT via SimulatorControlPanel)
    // -------------------------------------------------------------------------

    /** Signals the producer to stop permanently. Does not block. */
    public void stop() {
        running = false;
    }

    /**
     * Sets or clears the manual pause flag. Independent of the network state:
     * setting {@code paused = false} does NOT resume production if the network
     * is also down.
     *
     * @param paused {@code true} to pause; {@code false} to resume
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Returns {@code true} if the manual pause flag is set. */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Returns {@code true} if the producer is effectively idle for any reason
     * (manual pause OR network offline). Used by the control panel to show
     * an accurate status.
     */
    public boolean isEffectivelyIdle() {
        boolean networkDown = (appState != null) && !appState.isOnline();
        return paused || networkDown;
    }

    /**
     * Returns the total number of payloads successfully put into the queue
     * since this producer was started.
     */
    public long getOrdersEnqueued() {
        return ordersEnqueued;
    }
}
