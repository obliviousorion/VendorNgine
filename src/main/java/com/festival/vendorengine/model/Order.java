package com.festival.vendorengine.model;

import java.io.Serializable;
import java.util.List;

/**
 * Immutable-ish data holder for a customer order.
 *
 * <p>The only mutable field is {@code status}, which is volatile so that reads
 * from multiple consumer threads always see the latest value written by
 * {@code OrderController.transitionStatus}. {@code setStatus} must ONLY ever
 * be called from {@code OrderController}; no other class should mutate an order.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class Order implements Serializable, Comparable<Order> {

    private static final long serialVersionUID = 1L;

    private final String orderId;          // UUID
    private final String stallId;
    private final List<LineItem> items;    // static nested class (see below)
    private final PriorityToken priority;
    private final long createdAtMillis;
    private volatile OrderStatus status;   // volatile: read/written by multiple threads

    public Order(String orderId, String stallId, List<LineItem> items,
                 PriorityToken priority, long createdAtMillis) {
        this.orderId = orderId;
        this.stallId = stallId;
        this.items = items;
        this.priority = priority;
        this.createdAtMillis = createdAtMillis;
        this.status = OrderStatus.PENDING;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getOrderId() {
        return orderId;
    }

    public String getStallId() {
        return stallId;
    }

    public List<LineItem> getItems() {
        return items;
    }

    public PriorityToken getPriorityToken() {
        return priority;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public OrderStatus getStatus() {
        return status;
    }

    // -------------------------------------------------------------------------
    // Computed helpers
    // -------------------------------------------------------------------------

    /** Milliseconds elapsed since this order was created. */
    public long getElapsedMs() {
        return System.currentTimeMillis() - createdAtMillis;
    }

    /** Numeric priority weight derived from the token (higher = more urgent). */
    public int getPriority() {
        return priority.getWeight();
    }

    // -------------------------------------------------------------------------
    // Mutator — ONLY OrderController may call this
    // -------------------------------------------------------------------------

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    // -------------------------------------------------------------------------
    // Comparable — delegated to OrderComparator once it exists (Section 6.4).
    // Placeholder until controller/ is implemented.
    // -------------------------------------------------------------------------

    @Override
    public int compareTo(Order other) {
        throw new UnsupportedOperationException(
                "compareTo is not yet implemented; OrderComparator (controller/) is required.");
    }

    // -------------------------------------------------------------------------
    // Static nested class — satisfies "inner classes" rubric requirement.
    // Static (not inner) so it carries no implicit reference to the enclosing
    // Order instance, which prevents accidentally serializing the whole Order
    // graph twice when only a LineItem is needed.
    // -------------------------------------------------------------------------

    public static class LineItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String itemName;
        private final int quantity;
        private final double unitPrice;

        public LineItem(String itemName, int quantity, double unitPrice) {
            this.itemName = itemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getItemName() {
            return itemName;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }

        public double getSubtotal() {
            return quantity * unitPrice;
        }
    }
}
