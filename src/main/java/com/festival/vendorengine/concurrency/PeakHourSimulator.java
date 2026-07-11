package com.festival.vendorengine.concurrency;

import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic JSON order payloads at a configurable rate.
 *
 * <p><strong>Default rate:</strong> 10 orders/second.<br>
 * <strong>Spike mode:</strong> 50 orders/second (activated via {@link #setSpikeMode(boolean)}).<br>
 * <strong>Jitter:</strong> each sleep interval is jittered ±20% so the stream is
 * not perfectly periodic — more realistic and better stresses the
 * {@code BlockingQueue} under burst conditions.
 *
 * <p>Stall IDs are sampled from {@code STALL_IDS}. Menu items are drawn from a
 * fixed pool. VIP priority is assigned to 10% of orders (matches the deck's
 * assumption from the Part A formulation).
 *
 * <p>{@link #nextPayload()} sleeps the calling thread for the jittered interval,
 * then returns a JSON {@code String} matching the schema in Section 12 of the
 * architecture document:
 * <pre>
 * {
 *   "orderId"  : "&lt;uuid&gt;",
 *   "stallId"  : "S01",
 *   "priority" : "STANDARD",
 *   "createdAt": 1731234567890,
 *   "items"    : [ { "itemName": "Pani Puri", "quantity": 2, "unitPrice": 60.0 } ]
 * }
 * </pre>
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class PeakHourSimulator {

    // -------------------------------------------------------------------------
    // Rate constants
    // -------------------------------------------------------------------------

    /** Normal operating rate: 10 orders per second → 100 ms base sleep. */
    public static final int NORMAL_RATE = 10;

    /** Spike mode rate: 50 orders per second → 20 ms base sleep. */
    public static final int SPIKE_RATE = 50;

    /** Jitter factor: sleep interval varies ±20% of the base interval. */
    private static final double JITTER_FRACTION = 0.20;

    // -------------------------------------------------------------------------
    // Fixed data pools
    // -------------------------------------------------------------------------

    /** Stall IDs that orders can be assigned to. */
    private static final String[] STALL_IDS = {"S01", "S02", "S03", "S04", "S05"};

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
    // State
    // -------------------------------------------------------------------------

    private volatile int ordersPerSecond;
    private volatile long customDelayMs = -1;
    private final Random rng;

    /**
     * Constructs a simulator with the normal (10 orders/second) rate.
     */
    public PeakHourSimulator() {
        this.ordersPerSecond = NORMAL_RATE;
        this.rng = new Random();
    }

    /**
     * Constructs a simulator with an explicit initial rate.
     *
     * @param ordersPerSecond orders to emit per second (must be &gt; 0)
     */
    public PeakHourSimulator(int ordersPerSecond) {
        if (ordersPerSecond <= 0) {
            throw new IllegalArgumentException("ordersPerSecond must be > 0, got: " + ordersPerSecond);
        }
        this.ordersPerSecond = ordersPerSecond;
        this.rng = new Random();
    }

    // -------------------------------------------------------------------------
    // Rate control
    // -------------------------------------------------------------------------

    /**
     * Switches between spike mode (50/sec) and normal mode (10/sec).
     *
     * <p>The change is visible to the producer thread immediately (volatile write).
     *
     * @param spike {@code true} to activate spike mode, {@code false} for normal
     */
    public void setSpikeMode(boolean spike) {
        this.ordersPerSecond = spike ? SPIKE_RATE : NORMAL_RATE;
    }

    /**
     * Sets an arbitrary custom rate.
     *
     * @param ordersPerSecond must be &gt; 0
     */
    public void setOrdersPerSecond(int ordersPerSecond) {
        if (ordersPerSecond <= 0) {
            throw new IllegalArgumentException("ordersPerSecond must be > 0, got: " + ordersPerSecond);
        }
        this.ordersPerSecond = ordersPerSecond;
    }

    /** Returns the currently configured rate. */
    public int getOrdersPerSecond() {
        return ordersPerSecond;
    }

    /**
     * Sets a custom sleep delay in milliseconds, overriding the ordersPerSecond rate.
     * Set to -1 to disable and use the rate instead.
     */
    public void setCustomDelayMs(long customDelayMs) {
        this.customDelayMs = customDelayMs;
    }

    /** Returns the custom delay in milliseconds. */
    public long getCustomDelayMs() {
        return customDelayMs;
    }

    // -------------------------------------------------------------------------
    // Payload generation
    // -------------------------------------------------------------------------

    /**
     * Sleeps for the jittered inter-arrival interval and then returns one
     * synthetic JSON order payload.
     *
     * <p>Base interval = {@code 1000 / ordersPerSecond} ms. Actual sleep =
     * base × (1 ± {@value #JITTER_FRACTION}), chosen uniformly at random.
     *
     * @return a JSON string conforming to the order payload schema (Section 12)
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    public String nextPayload() throws InterruptedException {
        sleepJittered();
        return buildJson();
    }

    /**
     * Returns one synthetic JSON payload without sleeping.
     * Useful for tests that need payloads without rate-throttling.
     *
     * @return a JSON string conforming to the order payload schema (Section 12)
     */
    public String nextPayloadNoSleep() {
        return buildJson();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sleeps for the jittered inter-arrival delay.
     * Jitter is ±{@value #JITTER_FRACTION} of the base interval.
     */
    private void sleepJittered() throws InterruptedException {
        long baseMs;
        if (customDelayMs > 0) {
            baseMs = customDelayMs;
        } else {
            // Snapshot rate to avoid TOCTOU on volatile read
            int rate = ordersPerSecond;
            baseMs = 1000L / rate;
        }

        // Uniform jitter in [-jitter, +jitter] relative to base
        // rng.nextDouble() in [0,1) → scale to [-0.2, +0.2] of base
        double jitterFactor = 1.0 + (rng.nextDouble() * 2.0 * JITTER_FRACTION) - JITTER_FRACTION;
        long sleepMs = Math.max(1L, Math.round(baseMs * jitterFactor));

        Thread.sleep(sleepMs);
    }

    /**
     * Builds a synthetic JSON payload string.
     * Uses {@link StringBuilder} and manual escaping — no JSON library needed.
     */
    private String buildJson() {
        String orderId  = UUID.randomUUID().toString();
        String stallId  = STALL_IDS[rng.nextInt(STALL_IDS.length)];
        String priority = rng.nextInt(10) == 0 ? "VIP" : "STANDARD"; // 10% VIP
        long createdAt  = System.currentTimeMillis();

        // Generate 1–4 random line items from the menu pool
        int itemCount = 1 + rng.nextInt(4); // 1..4 inclusive
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < itemCount; i++) {
            int idx      = rng.nextInt(MENU_ITEMS.length);
            String name  = MENU_ITEMS[idx];
            int qty      = 1 + rng.nextInt(4);        // 1..4
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
