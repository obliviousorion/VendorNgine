package com.festival.vendorengine.controller;

import com.festival.vendorengine.exception.OrderProcessingException;
import com.festival.vendorengine.exception.StallNotFoundException;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.Stall;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The only class permitted to mutate order state (MVC contract).
 *
 * <p><strong>Design guarantees (Section 7.2 / Section 11):</strong>
 * <ul>
 *   <li>No two locks are ever held simultaneously by any code path in this
 *       class. {@code ConcurrentHashMap} handles the stall map without manual
 *       locks; {@code CopyOnWriteArrayList} handles the observer list. The
 *       only explicit {@code synchronized} calls are inside {@code Stall}
 *       ({@code addRevenue} / {@code getRevenueTotal}) — a single, short,
 *       uncontended critical section. "Zero deadlocks" is therefore a
 *       structural guarantee, not a hope.</li>
 *   <li>{@code observers} uses {@link CopyOnWriteArrayList}: observers register
 *       once at start-up, reads vastly outnumber writes, and iteration during
 *       {@code notifyObservers()} never throws
 *       {@code ConcurrentModificationException}.</li>
 * </ul>
 *
 * <p><strong>EDT discipline:</strong> {@code notifyObservers()} wraps every
 * callback in {@code SwingUtilities.invokeLater}. This is the single most
 * important line in the whole application — every background thread (consumers,
 * daemon, sync worker) eventually calls into this controller, and every
 * UI-touching callback funnels through this one point so that Swing's
 * single-threaded rule is enforced in exactly one place. See the inline comment
 * on that call below.
 *
 * <p>No javax.swing or java.awt imports in the field/method declarations —
 * the {@code invokeLater} call is isolated to {@code notifyObservers} and
 * uses a fully-qualified reference so the import appears only there.
 */
public class OrderController {

    // Shared, lock-free map: consumers call computeIfAbsent concurrently.
    private final ConcurrentHashMap<String, Stall> stallMap;

    // CopyOnWriteArrayList: observers register once at start-up; iteration
    // in notifyObservers() is always safe and never throws CME.
    private final CopyOnWriteArrayList<OrderObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * Constructs a controller backed by the provided stall map.
     *
     * @param stallMap the shared stall registry (typically loaded by
     *                 {@code MockDataLoader.loadStalls()})
     */
    public OrderController(ConcurrentHashMap<String, Stall> stallMap) {
        this.stallMap = stallMap;
    }

    // -------------------------------------------------------------------------
    // Observer registration
    // -------------------------------------------------------------------------

    /**
     * Registers an observer to be notified on every order or stall update.
     *
     * @param observer the observer to add; must not be null
     */
    public void registerObserver(OrderObserver observer) {
        observers.add(observer);
    }

    /**
     * Removes a previously registered observer.
     *
     * @param observer the observer to remove
     */
    public void unregisterObserver(OrderObserver observer) {
        observers.remove(observer);
    }

    // -------------------------------------------------------------------------
    // Core operations
    // -------------------------------------------------------------------------

    /**
     * Routes an incoming {@link Order} to the correct stall's
     * {@code PriorityBlockingQueue}.
     *
     * <p>{@code computeIfAbsent} is atomic in {@code ConcurrentHashMap}: if no
     * stall exists for the order's {@code stallId}, a new {@link Stall} is created
     * and inserted in a single lock-free operation — no manual synchronisation.
     *
     * @param order the parsed order to route
     * @throws OrderProcessingException if {@code put} is interrupted (rethrown
     *         as unchecked so the consumer's catch block can log it)
     */
    public void routeOrder(Order order) {
        // computeIfAbsent: atomic, lock-free stall creation if ID is unknown.
        // Uses a two-arg Stall constructor compatible with Stall(stallId, stallName);
        // stallName falls back to the stallId when the stall was not pre-loaded.
        Stall stall = stallMap.computeIfAbsent(
                order.getStallId(),
                id -> new Stall(id, id) // stall name = id when loaded dynamically
        );

        // PriorityBlockingQueue.put() never blocks for an unbounded queue and
        // does NOT declare InterruptedException — insert directly.
        stall.getOrderQueue().put(order);

        notifyObservers(order, stall);
    }


    /**
     * Transitions an order's status following the legal state machine:
     * {@code PENDING → ACCEPTED → READY → SERVED} (no skipping allowed).
     * {@code CANCELLED} is reachable from any non-terminal state.
     *
     * <p>If the order reaches {@code SERVED}, the stall's revenue total is
     * updated atomically via {@code Stall.addRevenue()}.
     *
     * @param stallId  the stall containing the order
     * @param orderId  the target order's UUID
     * @param next     the requested next status
     * @throws StallNotFoundException    if {@code stallId} is not in the registry
     * @throws OrderProcessingException  if {@code orderId} is not found in the
     *                                   stall's queue, or the transition is illegal
     */
    public void transitionStatus(String stallId, String orderId, OrderStatus next)
            throws StallNotFoundException {

        Stall stall = Optional.ofNullable(stallMap.get(stallId))
                .orElseThrow(() -> new StallNotFoundException(stallId));

        // Find the order in the stall's priority queue
        Order target = null;
        for (Order o : stall.getOrderQueue()) {
            if (o.getOrderId().equals(orderId)) {
                target = o;
                break;
            }
        }
        if (target == null) {
            throw new OrderProcessingException(
                    "Order " + orderId + " not found in stall " + stallId, null);
        }

        validateTransition(target.getStatus(), next, orderId);
        target.setStatus(next);

        if (next == OrderStatus.SERVED) {
            double orderTotal = target.getItems().stream()
                    .mapToDouble(Order.LineItem::getSubtotal)
                    .sum();
            stall.addRevenue(orderTotal);
        }

        notifyObservers(target, stall);
    }

    // -------------------------------------------------------------------------
    // Accessors for StallDataSource / testing
    // -------------------------------------------------------------------------

    /**
     * Returns the underlying stall map (read-only use only — mutations should
     * go through {@code routeOrder} or {@code transitionStatus}).
     *
     * @return the live stall registry
     */
    public ConcurrentHashMap<String, Stall> getStallMap() {
        return stallMap;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that a status transition is legal.
     *
     * <p>Legal forward transitions: {@code PENDING → ACCEPTED → READY → SERVED}.
     * {@code CANCELLED} is always reachable from any non-terminal state.
     *
     * @throws OrderProcessingException if the transition is illegal
     */
    private void validateTransition(OrderStatus current, OrderStatus next, String orderId) {
        if (next == OrderStatus.CANCELLED) return; // always allowed
        boolean legal = switch (current) {
            case PENDING   -> next == OrderStatus.ACCEPTED;
            case ACCEPTED  -> next == OrderStatus.READY;
            case READY     -> next == OrderStatus.SERVED;
            case SERVED, CANCELLED -> false; // terminal states
        };
        if (!legal) {
            throw new OrderProcessingException(
                    "Illegal transition " + current + " → " + next + " for order " + orderId, null);
        }
    }

    /**
     * Dispatches order and stall update events to all registered observers.
     *
     * <p><strong>This {@code SwingUtilities.invokeLater} call is the single most
     * important line in the whole application.</strong> Every background thread —
     * consumers, the network monitor daemon, the sync worker — eventually calls
     * into {@code OrderController}, and every UI-touching callback funnels
     * through here. Swing's single-threaded rule ("only touch components from
     * the Event Dispatch Thread") is enforced in exactly <em>one</em> place
     * rather than being scattered across 8 consumer threads. Do not remove or
     * move this {@code invokeLater}; its absence is the most common source of
     * subtle Swing threading bugs.
     */
    private void notifyObservers(Order order, Stall stall) {
        // Capture snapshot of observer list — CopyOnWriteArrayList iteration is safe
        // even if observers are added/removed concurrently.
        final java.util.List<OrderObserver> snapshot = new java.util.ArrayList<>(observers);
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (OrderObserver obs : snapshot) {
                obs.onOrderUpdated(order);
                obs.onStallSnapshotChanged(stall);
            }
        });
    }
}
