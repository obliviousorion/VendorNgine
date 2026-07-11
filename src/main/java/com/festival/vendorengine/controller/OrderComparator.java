package com.festival.vendorengine.controller;

import com.festival.vendorengine.model.Order;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Comparator for {@link Order} objects used inside {@code Stall.orderQueue}
 * ({@code PriorityBlockingQueue<Order>}).
 *
 * <p><strong>Why this is a named class, not a lambda (Section 6.4):</strong><br>
 * {@code PriorityBlockingQueue} stores its comparator as a field and serializes
 * it as part of its own state when the enclosing {@code Stall} (and therefore
 * {@code AppState}) is written to {@code offline_stalls.ser} via
 * {@code ObjectOutputStream}. Java lambda expressions are anonymous classes
 * generated at runtime with synthetic, JVM-version-specific class names (e.g.
 * {@code OrderComparator$$Lambda$14/0x...}). Those synthetic names are NOT
 * guaranteed to resolve across different JVM versions or even across separate
 * JVM invocations of the same application — deserialization would throw
 * {@code InvalidClassException} or {@code ClassNotFoundException}. A named,
 * top-level class implementing {@code Serializable} has a stable, fully-
 * qualified class name ({@code com.festival.vendorengine.controller.OrderComparator})
 * that survives any JVM restart, making the round-trip save/load of
 * {@code AppState} reliable. This is the primary reason Section 6.4 of the
 * architecture document mandates a named class here rather than an inline lambda.
 *
 * <p><strong>Ordering rules (composite score, higher wins):</strong>
 * <ol>
 *   <li>Priority weight — VIP (100) always beats STANDARD (0).</li>
 *   <li>Among equal priority, the order that has been waiting <em>longer</em>
 *       is served first (older {@code createdAtMillis} → larger {@code elapsedMs}).</li>
 * </ol>
 *
 * <p>The singleton {@link #DEFAULT} is used by {@code Stall} at construction time
 * and by {@code Order.compareTo} — centralising the ordering logic in one place.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class OrderComparator implements Comparator<Order>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Singleton instance to be passed to every {@code PriorityBlockingQueue<Order>}
     * at stall construction time. Using a shared singleton avoids allocating a new
     * {@code Comparator} object per stall and keeps the serialized form minimal.
     */
    public static final OrderComparator DEFAULT = new OrderComparator();

    /**
     * Compares two orders for priority-queue ordering.
     *
     * <p>The queue is a <em>min-heap</em> internally, so this comparator returns
     * a negative value when {@code a} should be dequeued <em>before</em> {@code b}
     * (i.e. {@code a} has higher urgency). Hence:
     * <ul>
     *   <li>Higher {@code getPriority()} weight → negative result → dequeued first.</li>
     *   <li>Greater elapsed wait time → negative result → dequeued first.</li>
     * </ul>
     *
     * @param a the first order
     * @param b the second order
     * @return negative if {@code a} should be served before {@code b},
     *         positive if {@code b} should be served first, 0 if equivalent
     */
    @Override
    public int compare(Order a, Order b) {
        // Primary: higher priority weight is served first.
        // b before a in Integer.compare → negative when b.weight > a.weight, but
        // we want the HIGHER weight to win, so we compare b vs a (descending).
        int byPriority = Integer.compare(b.getPriority(), a.getPriority());
        if (byPriority != 0) {
            return byPriority;
        }

        // Tie-break: the order that has waited longer (larger elapsedMs) wins.
        // Again descending: compare b's elapsed vs a's elapsed.
        return Long.compare(b.getElapsedMs(), a.getElapsedMs());
    }
}
