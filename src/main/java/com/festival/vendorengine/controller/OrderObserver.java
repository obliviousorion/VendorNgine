package com.festival.vendorengine.controller;

import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.Stall;

/**
 * Observer interface for the Observer pattern implemented by the controller layer.
 *
 * <p>Any component interested in order state changes (e.g. {@code KitchenView}'s
 * {@code OrderTableModel}, the offline-serialization trigger in
 * {@code NetworkMonitorDaemon}) registers via
 * {@code OrderController.registerObserver(OrderObserver)}.
 *
 * <p>{@code OrderController.notifyObservers()} funnels every callback through
 * {@code SwingUtilities.invokeLater}, so implementations of this interface may
 * safely touch Swing components directly — they are always called on the EDT.
 *
 * <p>No javax.swing or java.awt imports in this interface — the interface itself
 * stays framework-agnostic so that non-Swing observers (e.g. a test stub) can
 * implement it without pulling in Swing.
 */
public interface OrderObserver {

    /**
     * Called whenever an {@link Order}'s status changes.
     *
     * @param order the order whose status was just updated
     */
    void onOrderUpdated(Order order);

    /**
     * Called whenever the state of a {@link Stall}'s queue changes (new order
     * enqueued, or an order transitioned out of {@code PENDING}).
     *
     * @param stall the stall whose queue snapshot just changed
     */
    void onStallSnapshotChanged(Stall stall);
}
