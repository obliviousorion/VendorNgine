package com.festival.vendorengine.controller;

import com.festival.vendorengine.exception.OrderProcessingException;
import com.festival.vendorengine.exception.StallNotFoundException;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.PriorityToken;
import com.festival.vendorengine.model.Stall;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OrderController} — Section 13 spec.
 *
 * <h2>Coverage</h2>
 * <ol>
 *   <li>Legal transition chain: PENDING → ACCEPTED → READY → SERVED.</li>
 *   <li>Revenue credited to stall only on SERVED.</li>
 *   <li>Illegal skip (PENDING → SERVED) throws {@link OrderProcessingException}.</li>
 *   <li>Illegal skip (PENDING → READY) throws {@link OrderProcessingException}.</li>
 *   <li>ACCEPTED → PENDING (backwards) throws {@link OrderProcessingException}.</li>
 *   <li>Transition on unknown stall throws {@link StallNotFoundException}.</li>
 *   <li>Transition on unknown order ID throws {@link OrderProcessingException}.</li>
 *   <li>CANCELLED is reachable from PENDING (non-terminal to terminal).</li>
 *   <li>CANCELLED is reachable from ACCEPTED.</li>
 *   <li>CANCELLED → ACCEPTED is illegal (terminal state is final).</li>
 *   <li>SERVED → ACCEPTED is illegal (terminal state is final).</li>
 *   <li>{@code routeOrder} creates a new stall on-the-fly if the stall ID
 *       is not pre-loaded (computeIfAbsent behaviour).</li>
 *   <li>Observer is notified via {@code invokeLater} after routeOrder.</li>
 *   <li>Observer is notified via {@code invokeLater} after transitionStatus.</li>
 *   <li>Multiple orders routed to the same stall all appear in the queue.</li>
 * </ol>
 *
 * <p><strong>EDT note:</strong> {@code OrderController.notifyObservers()} wraps
 * callbacks in {@code SwingUtilities.invokeLater}. Tests that assert observer
 * delivery use a {@code CountDownLatch} and call
 * {@code javax.swing.SwingUtilities.invokeAndWait(() -> {})} to drain the EDT
 * queue before asserting — the standard pattern for Swing unit tests that run
 * headless.
 */
class OrderControllerTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private static final String STALL_ID   = "S01";
    private static final String STALL_NAME = "Chaat Corner";

    private ConcurrentHashMap<String, Stall> stallMap;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        stallMap = new ConcurrentHashMap<>();
        stallMap.put(STALL_ID, new Stall(STALL_ID, STALL_NAME));
        controller = new OrderController(stallMap);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a minimal STANDARD order with one LineItem (qty 2, price 50 → total 100). */
    private Order makeOrder(String orderId) {
        return new Order(
                orderId,
                STALL_ID,
                List.of(new Order.LineItem("Samosa", 2, 50.0)),
                PriorityToken.STANDARD,
                System.currentTimeMillis()
        );
    }

    /** Builds a VIP order. */
    private Order makeVipOrder(String orderId) {
        return new Order(
                orderId,
                STALL_ID,
                List.of(new Order.LineItem("Tikka", 3, 100.0)),
                PriorityToken.VIP,
                System.currentTimeMillis()
        );
    }

    /**
     * Routes an order and drains the EDT so that any invokeLater callbacks
     * have fired before we assert.
     */
    private void routeAndDrainEDT(Order order) throws Exception {
        controller.routeOrder(order);
        javax.swing.SwingUtilities.invokeAndWait(() -> { /* drain EDT */ });
    }

    /**
     * Transitions status and drains the EDT.
     */
    private void transitionAndDrainEDT(String stallId, String orderId, OrderStatus next)
            throws Exception {
        controller.transitionStatus(stallId, orderId, next);
        javax.swing.SwingUtilities.invokeAndWait(() -> { /* drain EDT */ });
    }

    // -------------------------------------------------------------------------
    // 1. Legal full transition chain
    // -------------------------------------------------------------------------

    @Test
    void legalTransitionChain_pendingToServed() throws Exception {
        Order order = makeOrder("ORD-001");
        routeAndDrainEDT(order);

        assertEquals(OrderStatus.PENDING, order.getStatus());

        transitionAndDrainEDT(STALL_ID, "ORD-001", OrderStatus.ACCEPTED);
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());

        transitionAndDrainEDT(STALL_ID, "ORD-001", OrderStatus.READY);
        assertEquals(OrderStatus.READY, order.getStatus());

        transitionAndDrainEDT(STALL_ID, "ORD-001", OrderStatus.SERVED);
        assertEquals(OrderStatus.SERVED, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 2. Revenue credited on SERVED only
    // -------------------------------------------------------------------------

    @Test
    void revenue_creditedOnlyWhenServed() throws Exception {
        Order order = makeOrder("ORD-REV");  // 2 × 50 = 100.0
        routeAndDrainEDT(order);
        Stall stall = stallMap.get(STALL_ID);

        assertEquals(0.0, stall.getRevenueTotal(), 1e-9,
                "Revenue must be zero before SERVED");

        transitionAndDrainEDT(STALL_ID, "ORD-REV", OrderStatus.ACCEPTED);
        assertEquals(0.0, stall.getRevenueTotal(), 1e-9,
                "Revenue must still be zero after ACCEPTED");

        transitionAndDrainEDT(STALL_ID, "ORD-REV", OrderStatus.READY);
        assertEquals(0.0, stall.getRevenueTotal(), 1e-9,
                "Revenue must still be zero after READY");

        transitionAndDrainEDT(STALL_ID, "ORD-REV", OrderStatus.SERVED);
        assertEquals(100.0, stall.getRevenueTotal(), 1e-9,
                "Revenue must equal order total (2 × 50) after SERVED");
    }

    // -------------------------------------------------------------------------
    // 3. Illegal skip: PENDING → SERVED
    // -------------------------------------------------------------------------

    @Test
    void illegalTransition_pendingToServed_throws() throws Exception {
        Order order = makeOrder("ORD-SKIP1");
        routeAndDrainEDT(order);

        assertThrows(OrderProcessingException.class, () ->
                controller.transitionStatus(STALL_ID, "ORD-SKIP1", OrderStatus.SERVED),
                "PENDING → SERVED must throw OrderProcessingException (no skipping)");

        // Status must be unchanged
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 4. Illegal skip: PENDING → READY
    // -------------------------------------------------------------------------

    @Test
    void illegalTransition_pendingToReady_throws() throws Exception {
        Order order = makeOrder("ORD-SKIP2");
        routeAndDrainEDT(order);

        assertThrows(OrderProcessingException.class, () ->
                controller.transitionStatus(STALL_ID, "ORD-SKIP2", OrderStatus.READY),
                "PENDING → READY must throw OrderProcessingException (no skipping)");

        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 5. Backwards transition: ACCEPTED → PENDING
    // -------------------------------------------------------------------------

    @Test
    void illegalTransition_backwards_acceptedToPending_throws() throws Exception {
        Order order = makeOrder("ORD-BACK");
        routeAndDrainEDT(order);
        transitionAndDrainEDT(STALL_ID, "ORD-BACK", OrderStatus.ACCEPTED);

        assertThrows(OrderProcessingException.class, () ->
                controller.transitionStatus(STALL_ID, "ORD-BACK", OrderStatus.PENDING),
                "ACCEPTED → PENDING must throw (backwards transition)");

        assertEquals(OrderStatus.ACCEPTED, order.getStatus(),
                "Status must not be mutated by a rejected transition");
    }

    // -------------------------------------------------------------------------
    // 6. Unknown stall → StallNotFoundException
    // -------------------------------------------------------------------------

    @Test
    void transitionStatus_unknownStall_throwsStallNotFoundException() {
        assertThrows(StallNotFoundException.class, () ->
                controller.transitionStatus("NO-SUCH-STALL", "ORD-X", OrderStatus.ACCEPTED),
                "Transition on an unknown stall must throw StallNotFoundException");
    }

    // -------------------------------------------------------------------------
    // 7. Unknown order ID → OrderProcessingException
    // -------------------------------------------------------------------------

    @Test
    void transitionStatus_unknownOrderId_throwsOrderProcessingException() {
        assertThrows(OrderProcessingException.class, () ->
                controller.transitionStatus(STALL_ID, "NO-SUCH-ORDER", OrderStatus.ACCEPTED),
                "Transition for an unrecognised order ID must throw OrderProcessingException");
    }

    // -------------------------------------------------------------------------
    // 8. CANCELLED reachable from PENDING
    // -------------------------------------------------------------------------

    @Test
    void cancel_fromPending_isLegal() throws Exception {
        Order order = makeOrder("ORD-CAN1");
        routeAndDrainEDT(order);

        assertDoesNotThrow(() ->
                controller.transitionStatus(STALL_ID, "ORD-CAN1", OrderStatus.CANCELLED),
                "PENDING → CANCELLED must be legal");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 9. CANCELLED reachable from ACCEPTED
    // -------------------------------------------------------------------------

    @Test
    void cancel_fromAccepted_isLegal() throws Exception {
        Order order = makeOrder("ORD-CAN2");
        routeAndDrainEDT(order);
        transitionAndDrainEDT(STALL_ID, "ORD-CAN2", OrderStatus.ACCEPTED);

        assertDoesNotThrow(() ->
                controller.transitionStatus(STALL_ID, "ORD-CAN2", OrderStatus.CANCELLED),
                "ACCEPTED → CANCELLED must be legal");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 10. CANCELLED → ACCEPTED is illegal (terminal state is final)
    // -------------------------------------------------------------------------

    @Test
    void illegalTransition_cancelledToAccepted_throws() throws Exception {
        Order order = makeOrder("ORD-CAN3");
        routeAndDrainEDT(order);
        transitionAndDrainEDT(STALL_ID, "ORD-CAN3", OrderStatus.CANCELLED);

        assertThrows(OrderProcessingException.class, () ->
                controller.transitionStatus(STALL_ID, "ORD-CAN3", OrderStatus.ACCEPTED),
                "CANCELLED → ACCEPTED must throw (CANCELLED is terminal)");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 11. SERVED → ACCEPTED is illegal (terminal state is final)
    // -------------------------------------------------------------------------

    @Test
    void illegalTransition_servedToAccepted_throws() throws Exception {
        Order order = makeOrder("ORD-SERV-BACK");
        routeAndDrainEDT(order);
        transitionAndDrainEDT(STALL_ID, "ORD-SERV-BACK", OrderStatus.ACCEPTED);
        transitionAndDrainEDT(STALL_ID, "ORD-SERV-BACK", OrderStatus.READY);
        transitionAndDrainEDT(STALL_ID, "ORD-SERV-BACK", OrderStatus.SERVED);

        assertThrows(OrderProcessingException.class, () ->
                controller.transitionStatus(STALL_ID, "ORD-SERV-BACK", OrderStatus.ACCEPTED),
                "SERVED → ACCEPTED must throw (SERVED is terminal)");

        assertEquals(OrderStatus.SERVED, order.getStatus());
    }

    // -------------------------------------------------------------------------
    // 12. routeOrder creates stall on-the-fly via computeIfAbsent
    // -------------------------------------------------------------------------

    @Test
    void routeOrder_unknownStall_createsStallDynamically() throws Exception {
        Order order = new Order(
                "ORD-NEW-STALL",
                "S99",   // not pre-loaded
                List.of(new Order.LineItem("Lassi", 1, 40.0)),
                PriorityToken.STANDARD,
                System.currentTimeMillis()
        );

        assertFalse(stallMap.containsKey("S99"),
                "Precondition: stall S99 must not exist before routeOrder");

        routeAndDrainEDT(order);

        assertTrue(stallMap.containsKey("S99"),
                "routeOrder must create stall S99 via computeIfAbsent");
        assertEquals(1, stallMap.get("S99").getOrderQueue().size(),
                "The newly created stall must contain exactly the routed order");
    }

    // -------------------------------------------------------------------------
    // 13. Observer notified after routeOrder (via invokeLater)
    // -------------------------------------------------------------------------

    @Test
    void routeOrder_notifiesObserver() throws Exception {
        AtomicReference<Order>  notifiedOrder = new AtomicReference<>();
        AtomicReference<Stall>  notifiedStall = new AtomicReference<>();

        controller.registerObserver(new OrderObserver() {
            @Override public void onOrderUpdated(Order o)          { notifiedOrder.set(o); }
            @Override public void onStallSnapshotChanged(Stall s)  { notifiedStall.set(s); }
        });

        Order order = makeOrder("ORD-OBS1");
        routeAndDrainEDT(order);  // drains EDT so invokeLater has fired

        assertNotNull(notifiedOrder.get(),  "Observer.onOrderUpdated must be called");
        assertNotNull(notifiedStall.get(),  "Observer.onStallSnapshotChanged must be called");
        assertEquals("ORD-OBS1", notifiedOrder.get().getOrderId());
        assertEquals(STALL_ID,   notifiedStall.get().getStallId());
    }

    // -------------------------------------------------------------------------
    // 14. Observer notified after transitionStatus (via invokeLater)
    // -------------------------------------------------------------------------

    @Test
    void transitionStatus_notifiesObserver() throws Exception {
        Order order = makeOrder("ORD-OBS2");
        routeAndDrainEDT(order);

        AtomicReference<OrderStatus> observedStatus = new AtomicReference<>();
        controller.registerObserver(new OrderObserver() {
            @Override public void onOrderUpdated(Order o)          { observedStatus.set(o.getStatus()); }
            @Override public void onStallSnapshotChanged(Stall s)  { /* not checked here */ }
        });

        transitionAndDrainEDT(STALL_ID, "ORD-OBS2", OrderStatus.ACCEPTED);

        assertEquals(OrderStatus.ACCEPTED, observedStatus.get(),
                "Observer must see the post-transition status");
    }

    // -------------------------------------------------------------------------
    // 15. Multiple orders routed to same stall
    // -------------------------------------------------------------------------

    @Test
    void multipleOrders_allPresentInStallQueue() throws Exception {
        for (int i = 0; i < 5; i++) {
            routeAndDrainEDT(makeOrder("ORD-MULTI-" + i));
        }

        Stall stall = stallMap.get(STALL_ID);
        assertEquals(5, stall.getOrderQueue().size(),
                "All 5 routed orders must be in the stall queue");
    }

    // -------------------------------------------------------------------------
    // 16. Revenue accumulates across multiple SERVED orders
    // -------------------------------------------------------------------------

    @Test
    void revenue_accumulatesAcrossMultipleServedOrders() throws Exception {
        // Two orders: each 2 × 50 = 100, total expected = 200
        for (String id : List.of("ORD-ACC1", "ORD-ACC2")) {
            routeAndDrainEDT(makeOrder(id));
            transitionAndDrainEDT(STALL_ID, id, OrderStatus.ACCEPTED);
            transitionAndDrainEDT(STALL_ID, id, OrderStatus.READY);
            transitionAndDrainEDT(STALL_ID, id, OrderStatus.SERVED);
        }

        assertEquals(200.0, stallMap.get(STALL_ID).getRevenueTotal(), 1e-9,
                "Revenue must accumulate: 2 × 100 = 200");
    }
}
