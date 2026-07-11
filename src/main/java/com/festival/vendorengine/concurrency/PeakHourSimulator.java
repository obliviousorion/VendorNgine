package com.festival.vendorengine.concurrency;

import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic JSON order payloads at a configurable rate.
 *
 * <p><strong>Modes (Section 6.2):</strong>
 * <ul>
 *   <li><b>Slow</b>  — 1 order every {@value #SLOW_DELAY_MS} ms (0.25/s)</li>
 *   <li><b>Normal</b>— 1 order every {@value #NORMAL_DELAY_MS} ms (0.5/s)</li>
 *   <li><b>Peak</b>  — {@value #PEAK_ORDERS_PER_SEC} orders/second (via
 *       {@code ordersPerSecond} field with {@code customDelayMs == -1})</li>
 *   <li><b>Custom</b>— caller sets any positive {@code customDelayMs}</li>
 * </ul>
 *
 * <p><strong>Jitter:</strong> each sleep interval is jittered ±20% so the
 * stream is not perfectly periodic — more realistic and better stresses the
 * {@code BlockingQueue} under burst conditions.
 *
 * <p>The stall ID pool is supplied at construction time (loaded from
 * {@code stalls.json} via {@code Main}) so the simulator always matches the
 * actual registry — no more hard-coded list diverging from the data file.
 *
 * <p>{@link #nextPayloadForStall(String)} lets the {@code SimulatorControlPanel}
 * inject a single order for a specific stall without going through the
 * normal rate-limited path.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class PeakHourSimulator {

    // -------------------------------------------------------------------------
    // Rate / mode constants
    // -------------------------------------------------------------------------

    /** Slow mode: 1 order every 4 seconds. */
    public static final long SLOW_DELAY_MS = 4_000L;

    /** Normal mode: 1 order every 2 seconds. */
    public static final long NORMAL_DELAY_MS = 2_000L;

    /** Peak mode: 5 orders per second (200 ms base interval). */
    public static final int PEAK_ORDERS_PER_SEC = 5;

    /**
     * Legacy spike-rate constant kept for backward compatibility
     * ({@code setSpikeMode(true)} still works).
     */
    public static final int SPIKE_RATE = 50;

    /** Normal operating rate used by the no-arg constructor. */
    public static final int NORMAL_RATE = 10;

    /** Jitter factor: sleep interval varies ±20% of the base interval. */
    private static final double JITTER_FRACTION = 0.20;

    // -------------------------------------------------------------------------
    // Default stall IDs (used only when no stall pool is supplied)
    // -------------------------------------------------------------------------

    private static final String[] DEFAULT_STALL_IDS = {"S01", "S02", "S03", "S04", "S05"};

    /** Menu items used for synthetic line items. */
    private static final String[] MENU_ITEMS = {
        "Pani Puri", "Samosa", "Chole Bhature", "Masala Chai",
        "Momo", "Tikka Skewer", "Lassi", "Corn Chaat"
    };

    /** Unit prices (parallel array with MENU_ITEMS). */
    private static final double[] UNIT_PRICES = {
        60.0, 30.0, 120.0, 25.0, 80.0, 100.0, 50.0, 45.0
    };

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    /** Live stall ID pool — sourced from stalls.json at startup. */
    private volatile String[] stallIds;

    private volatile int ordersPerSecond;
    private volatile long customDelayMs = NORMAL_DELAY_MS; // default: Normal mode
    private final Random rng;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a simulator using the default stall pool and Normal rate.
     * Prefer {@link #PeakHourSimulator(String[])} in production code so the
     * stall pool matches {@code stalls.json}.
     */
    public PeakHourSimulator() {
        this.stallIds = DEFAULT_STALL_IDS.clone();
        this.ordersPerSecond = NORMAL_RATE;
        this.rng = new Random();
    }

    /**
     * Constructs a simulator with an explicit initial rate (legacy API).
     *
     * @param ordersPerSecond orders to emit per second (must be &gt; 0)
     */
    public PeakHourSimulator(int ordersPerSecond) {
        if (ordersPerSecond <= 0) {
            throw new IllegalArgumentException("ordersPerSecond must be > 0, got: " + ordersPerSecond);
        }
        this.stallIds = DEFAULT_STALL_IDS.clone();
        this.ordersPerSecond = ordersPerSecond;
        this.rng = new Random();
    }

    /**
     * Preferred constructor: supplies the live stall ID pool from
     * {@code stalls.json} so the simulator never generates orders for
     * stalls that aren't registered.
     *
     * @param stallIds array of stall IDs to distribute orders across;
     *                 must not be null or empty
     */
    public PeakHourSimulator(String[] stallIds) {
        if (stallIds == null || stallIds.length == 0) {
            throw new IllegalArgumentException("stallIds must not be null or empty");
        }
        this.stallIds = stallIds.clone();
        this.ordersPerSecond = NORMAL_RATE;
        this.rng = new Random();
    }

    // -------------------------------------------------------------------------
    // Rate / mode control
    // -------------------------------------------------------------------------

    /**
     * Sets SLOW mode: 1 order every {@value #SLOW_DELAY_MS} ms.
     * This is the most readable rate for manual UI testing.
     */
    public void setSlowMode() {
        this.customDelayMs = SLOW_DELAY_MS;
    }

    /**
     * Sets NORMAL mode: 1 order every {@value #NORMAL_DELAY_MS} ms.
     */
    public void setNormalMode() {
        this.customDelayMs = NORMAL_DELAY_MS;
    }

    /**
     * Sets PEAK mode: {@value #PEAK_ORDERS_PER_SEC} orders/second.
     * Disables the fixed-delay path and uses the rate-based path instead.
     */
    public void setPeakMode() {
        this.customDelayMs = -1L;                      // switch to rate-based path
        this.ordersPerSecond = PEAK_ORDERS_PER_SEC;
    }

    /**
     * Switches between legacy spike mode (50/sec) and normal mode (10/sec).
     * Kept for backward compatibility with {@code Main.java}'s CLI args.
     *
     * @param spike {@code true} for spike mode, {@code false} for normal
     */
    public void setSpikeMode(boolean spike) {
        if (spike) {
            this.customDelayMs = -1L;
            this.ordersPerSecond = SPIKE_RATE;
        } else {
            setNormalMode();
        }
    }

    /**
     * Sets a custom fixed inter-arrival delay, overriding the mode setting.
     * Set to -1 to disable and use the {@code ordersPerSecond} rate instead.
     *
     * @param customDelayMs positive value = ms between orders; -1 = use rate
     */
    public void setCustomDelayMs(long customDelayMs) {
        this.customDelayMs = customDelayMs;
    }

    /** Returns the current custom delay in milliseconds (-1 = not active). */
    public long getCustomDelayMs() {
        return customDelayMs;
    }

    /**
     * Sets the orders-per-second rate used when {@code customDelayMs == -1}.
     *
     * @param ordersPerSecond must be &gt; 0
     */
    public void setOrdersPerSecond(int ordersPerSecond) {
        if (ordersPerSecond <= 0) {
            throw new IllegalArgumentException("ordersPerSecond must be > 0, got: " + ordersPerSecond);
        }
        this.ordersPerSecond = ordersPerSecond;
    }

    /** Returns the currently configured orders-per-second rate. */
    public int getOrdersPerSecond() {
        return ordersPerSecond;
    }

    /**
     * Replaces the live stall ID pool. Thread-safe via volatile write;
     * the next call to {@link #nextPayload()} will pick from the new pool.
     *
     * @param stallIds new pool; must not be null or empty
     */
    public void setStallIds(String[] stallIds) {
        if (stallIds == null || stallIds.length == 0) return;
        this.stallIds = stallIds.clone();
    }

    /** Returns a snapshot of the current stall ID pool. */
    public String[] getStallIds() {
        return stallIds.clone();
    }

    // -------------------------------------------------------------------------
    // Payload generation
    // -------------------------------------------------------------------------

    /**
     * Sleeps for the jittered inter-arrival interval and then returns one
     * synthetic JSON order payload for a randomly chosen stall.
     *
     * @return a JSON string conforming to the order payload schema (Section 12)
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    public String nextPayload() throws InterruptedException {
        sleepJittered();
        return buildJsonForStall(stallIds[rng.nextInt(stallIds.length)]);
    }

    /**
     * Returns one synthetic JSON payload for a randomly chosen stall
     * <em>without</em> sleeping. Useful for tests and immediate injection.
     *
     * @return a JSON string conforming to the order payload schema (Section 12)
     */
    public String nextPayloadNoSleep() {
        return buildJsonForStall(stallIds[rng.nextInt(stallIds.length)]);
    }

    /**
     * Returns one synthetic JSON payload for the <em>specified</em> stall,
     * without sleeping. Used by {@code SimulatorControlPanel} to inject
     * a targeted order for manual testing.
     *
     * @param stallId the stall ID to embed in the payload
     * @return a JSON string with the given {@code stallId}
     */
    public String nextPayloadForStall(String stallId) {
        return buildJsonForStall(stallId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sleeps for the jittered inter-arrival delay.
     * Uses {@code customDelayMs} when positive; otherwise uses {@code ordersPerSecond}.
     */
    private void sleepJittered() throws InterruptedException {
        long baseMs;
        long delay = customDelayMs; // volatile snapshot
        if (delay > 0) {
            baseMs = delay;
        } else {
            int rate = ordersPerSecond; // volatile snapshot
            baseMs = 1000L / rate;
        }

        // Uniform jitter in [-JITTER_FRACTION, +JITTER_FRACTION] of base
        double jitterFactor = 1.0 + (rng.nextDouble() * 2.0 * JITTER_FRACTION) - JITTER_FRACTION;
        long sleepMs = Math.max(1L, Math.round(baseMs * jitterFactor));
        Thread.sleep(sleepMs);
    }

    /**
     * Builds a synthetic JSON payload string for the given stall ID.
     * Uses {@link StringBuilder} and manual escaping — no JSON library needed.
     *
     * @param stallId the stall to route the order to
     * @return a complete JSON order payload string
     */
    private String buildJsonForStall(String stallId) {
        String orderId  = UUID.randomUUID().toString();
        String priority = rng.nextInt(10) == 0 ? "VIP" : "STANDARD"; // 10% VIP
        long createdAt  = System.currentTimeMillis();

        // Generate 1–4 random line items from the menu pool
        int itemCount = 1 + rng.nextInt(4); // 1..4 inclusive
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < itemCount; i++) {
            int idx      = rng.nextInt(MENU_ITEMS.length);
            String name  = MENU_ITEMS[idx];
            int qty      = 1 + rng.nextInt(4); // 1..4
            double price = UNIT_PRICES[idx];
            if (i > 0) items.append(',');
            items.append("{\"itemName\":\"").append(name).append("\",")
                 .append("\"quantity\":").append(qty).append(',')
                 .append("\"unitPrice\":").append(price).append('}');
        }

        return "{"
                + "\"orderId\":\"" + orderId + "\","
                + "\"stallId\":\"" + stallId + "\","
                + "\"priority\":\"" + priority + "\","
                + "\"createdAt\":" + createdAt + ","
                + "\"items\":[" + items + "]"
                + "}";
    }
}
