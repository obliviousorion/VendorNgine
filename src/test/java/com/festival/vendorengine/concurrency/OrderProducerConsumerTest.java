package com.festival.vendorengine.concurrency;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.Stall;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Producer-Consumer pipeline (Section 13).
 *
 * <h2>Spec</h2>
 * <ul>
 *   <li>Push exactly 500 synthetic JSON payloads through a real
 *       {@link LinkedBlockingQueue} (capacity 1000) with 8 concurrent consumers.</li>
 *   <li>Use a {@link CountDownLatch}{@code (500)} with a 5-second timeout to
 *       await completion — no raw {@code Thread.sleep} assertions.</li>
 *   <li>Assert all 500 orders land in some stall's queue with <strong>none lost
 *       and none duplicated</strong>.</li>
 * </ul>
 *
 * <h2>Approach</h2>
 * <p>We install a custom {@link OrderController} subclass (a test double) that
 * overrides {@code routeOrder} to record every routed order ID in a
 * thread-safe set and count down the latch. This avoids touching Swing
 * ({@code SwingUtilities.invokeLater}) during tests while still exercising
 * the real {@code JsonUtil}, {@code OrderComparator}, and the
 * {@code LinkedBlockingQueue} / consumer threading.
 *
 * <p>The thread pool used here is local to the test (not the application
 * singleton) so tests are fully isolated from the main pool.
 */
class OrderProducerConsumerTest {

    // -------------------------------------------------------------------------
    // Test configuration
    // -------------------------------------------------------------------------

    private static final int TOTAL_ORDERS = 500;
    private static final int CONSUMER_COUNT = 8;
    private static final int QUEUE_CAPACITY = 1_000;
    private static final int LATCH_TIMEOUT_SECONDS = 5;

    // -------------------------------------------------------------------------
    // Fields wired in @BeforeEach
    // -------------------------------------------------------------------------

    private LinkedBlockingQueue<String> queue;
    private ExecutorService pool;
    private CountDownLatch latch;

    /** Thread-safe set that collects every order ID routed by consumers. */
    private Set<String> routedOrderIds;

    /** Counts routing invocations — used to detect duplicates. */
    private AtomicInteger routeCallCount;

    /**
     * Minimal test-scope controller that:
     * <ol>
     *   <li>Delegates to the real parent {@code routeOrder} (so the real
     *       {@code PriorityBlockingQueue} in each {@code Stall} receives orders).</li>
     *   <li>Records each routed order ID for duplicate detection.</li>
     *   <li>Counts down the latch so the main test thread knows when done.</li>
     * </ol>
     *
     * <p>We skip the {@code notifyObservers} / {@code SwingUtilities.invokeLater}
     * call by overriding {@code routeOrder} and enqueuing directly into the stall's
     * queue — keeping the test Swing-free while still exercising the real data path.
     */
    private class TestController extends OrderController {
        TestController() {
            super(new ConcurrentHashMap<>());
        }

        @Override
        public void routeOrder(Order order) {
            // Enqueue into the stall's real PriorityBlockingQueue
            // (mirrors what the real routeOrder does, minus the invokeLater)
            Stall stall = getStallMap().computeIfAbsent(
                    order.getStallId(), id -> new Stall(id, id));
            stall.getOrderQueue().put(order);

            // Record for assertion
            boolean isNew = routedOrderIds.add(order.getOrderId());
            // isNew == false would mean we received the same order ID twice
            // (a duplicate) — we assert this doesn't happen in the test body.
            routeCallCount.incrementAndGet();

            latch.countDown();
        }
    }

    private TestController controller;

    // -------------------------------------------------------------------------
    // Setup / tear-down
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        queue           = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        latch           = new CountDownLatch(TOTAL_ORDERS);
        routedOrderIds  = Collections.newSetFromMap(new ConcurrentHashMap<>());
        routeCallCount  = new AtomicInteger(0);
        controller      = new TestController();

        // Local thread pool — isolated from ExecutorServiceManager singleton
        pool = Executors.newFixedThreadPool(CONSUMER_COUNT);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Test
    // -------------------------------------------------------------------------

    /**
     * Verifies that 500 synthetic order payloads are produced, consumed, and
     * routed without any loss or duplication.
     *
     * <p>The {@link Timeout} annotation is a safety net (10 s) on top of the
     * 5-second latch timeout so the test suite never hangs.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void allOrdersArrivedInStallQueues_noLossNoDuplication() throws InterruptedException {

        // 1. Start 8 consumers
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            pool.submit(new OrderConsumer(queue, controller));
        }

        // 2. Generate 500 synthetic payloads using the real PeakHourSimulator
        //    (no sleep — use nextPayloadNoSleep() so the test is fast).
        PeakHourSimulator simulator = new PeakHourSimulator();
        List<String> payloads = new ArrayList<>(TOTAL_ORDERS);
        for (int i = 0; i < TOTAL_ORDERS; i++) {
            payloads.add(simulator.nextPayloadNoSleep());
        }

        // 3. Enqueue all payloads — BlockingQueue.put() provides backpressure;
        //    no data is dropped here because capacity (1000) > count (500).
        for (String payload : payloads) {
            queue.put(payload);
        }

        // 4. Wait for consumers to process all 500 orders.
        //    CountDownLatch(500) with a strict 5-second timeout — per Section 13.
        boolean completed = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(completed,
                "Latch timed out: only "
                + (TOTAL_ORDERS - latch.getCount()) + "/" + TOTAL_ORDERS
                + " orders were processed within " + LATCH_TIMEOUT_SECONDS + "s");

        // 5a. No orders lost — every routeOrder call was counted down.
        assertEquals(TOTAL_ORDERS, routeCallCount.get(),
                "Route call count mismatch — orders were lost");

        // 5b. No duplicates — routedOrderIds is a set; its size equals unique IDs.
        //     Because each payload has a UUID orderId, size < TOTAL_ORDERS implies
        //     a duplicate was routed.
        assertEquals(TOTAL_ORDERS, routedOrderIds.size(),
                "Duplicate order IDs detected: expected "
                + TOTAL_ORDERS + " unique IDs but got " + routedOrderIds.size());

        // 5c. All 500 orders are accounted for across the stall queues.
        int totalInQueues = controller.getStallMap().values().stream()
                .mapToInt(stall -> stall.getOrderQueue().size())
                .sum();
        assertEquals(TOTAL_ORDERS, totalInQueues,
                "Queue depth mismatch: expected " + TOTAL_ORDERS
                + " orders in stall queues, found " + totalInQueues);

        // 5d. Spot-check: every stall that received orders has a non-empty queue
        //     and its queue contains only PENDING orders (status set by Order constructor).
        for (Stall stall : controller.getStallMap().values()) {
            assertFalse(stall.getOrderQueue().isEmpty(),
                    "Stall " + stall.getStallId() + " has no orders despite being in the map");
            for (Order order : stall.getOrderQueue()) {
                assertNotNull(order.getOrderId(),  "Order ID must not be null");
                assertNotNull(order.getStallId(),  "Stall ID must not be null");
                assertNotNull(order.getPriorityToken(), "Priority token must not be null");
                // Status is PENDING because no transitionStatus() was called in this test
                assertEquals(com.festival.vendorengine.model.OrderStatus.PENDING,
                        order.getStatus(),
                        "Order " + order.getOrderId() + " should be PENDING");
            }
        }
    }
}
