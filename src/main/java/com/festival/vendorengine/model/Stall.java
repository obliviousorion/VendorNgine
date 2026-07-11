package com.festival.vendorengine.model;

import java.io.Serializable;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Represents a physical vendor stall at the festival.
 *
 * <p>{@code orderQueue} uses a {@link PriorityBlockingQueue} so that the highest-priority
 * {@link Order} is always dequeued first. The comparator is supplied at construction time
 * from {@code OrderComparator.DEFAULT} (a named, {@link Serializable} class — NOT a lambda —
 * so that the queue survives Java object serialization correctly; see Section 6.4 of the
 * architecture document for the rationale).
 *
 * <p>{@code revenueTotal} is guarded by {@code synchronized} on {@code this} because it can be
 * incremented by any of the 8 consumer threads via {@code OrderController.transitionStatus}
 * when an order reaches SERVED status. The critical section is short and uncontended in practice,
 * so no {@code java.util.concurrent.atomic} type is needed here.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class Stall implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String stallId;
    private final String stallName;
    private final PriorityBlockingQueue<Order> orderQueue;
    private double revenueTotal;

    /**
     * Creates a stall with an empty order queue ordered by {@code OrderComparator.DEFAULT}.
     *
     * <p>The queue initial capacity is 64 — large enough for a burst of orders before the
     * consumer threads catch up, but not so large that memory is wasted at idle stalls.
     * {@link PriorityBlockingQueue} grows automatically if this is exceeded.
     *
     * <p><strong>Serialization note:</strong> the comparator passed here must implement
     * {@link java.io.Serializable}; a lambda would break round-trip serialization.
     * {@code OrderComparator} is therefore a named class (Section 6.4).
     *
     * @param stallId   unique identifier (e.g. "S01")
     * @param stallName human-readable display name (e.g. "Chaat Corner")
     */
    public Stall(String stallId, String stallName) {
        this.stallId = stallId;
        this.stallName = stallName;
        // Comparator is intentionally left as natural ordering for now.
        // OrderComparator.DEFAULT will be wired here once controller/ is implemented.
        this.orderQueue = new PriorityBlockingQueue<>(64);
        this.revenueTotal = 0.0;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getStallId() {
        return stallId;
    }

    public String getStallName() {
        return stallName;
    }

    public PriorityBlockingQueue<Order> getOrderQueue() {
        return orderQueue;
    }

    // -------------------------------------------------------------------------
    // Revenue (synchronized — mutated by multiple threads)
    // -------------------------------------------------------------------------

    /** Adds {@code amount} to this stall's cumulative revenue. Thread-safe. */
    public synchronized void addRevenue(double amount) {
        revenueTotal += amount;
    }

    /** Returns the current cumulative revenue. Thread-safe. */
    public synchronized double getRevenueTotal() {
        return revenueTotal;
    }

    // -------------------------------------------------------------------------
    // Queue helpers
    // -------------------------------------------------------------------------

    /**
     * Enqueues an order into the priority queue. Delegates to
     * {@link PriorityBlockingQueue#put(Object)}, which never blocks
     * (unbounded queue).
     */
    public void enqueue(Order order) {
        orderQueue.put(order);
    }
}
