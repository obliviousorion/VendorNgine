package com.festival.vendorengine.concurrency;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.exception.OrderProcessingException;
import com.festival.vendorengine.io.JsonUtil;
import com.festival.vendorengine.model.Order;

import java.util.concurrent.BlockingQueue;

/**
 * Consumer thread: drains JSON order payloads from the shared
 * {@link BlockingQueue}{@code <String>}, parses each into an {@link Order},
 * and routes it to the correct stall via {@link OrderController#routeOrder}.
 *
 * <p><strong>Why 8 instances (Section 6.3):</strong> a single consumer would
 * serialise all JSON parsing + routing, becoming the bottleneck at 90+ stalls
 * running simultaneously. Eight consumers pull from one shared
 * {@code LinkedBlockingQueue<String>} (capacity 1000), giving real parallelism
 * with zero shared mutable state between consumers — {@code ConcurrentHashMap}
 * inside {@code OrderController} handles the only shared structure and requires
 * no manual locks. This makes "zero deadlocks" a structural guarantee rather
 * than a hope: no method in this design acquires two locks at once.
 *
 * <p>{@code queue.take()} blocks when the queue is empty, so consumer threads
 * sleep cheaply while the producer is between bursts rather than spinning.
 *
 * <p>{@link OrderProcessingException} is caught and logged (never silently
 * swallowed) but does not kill the consumer thread — one malformed payload
 * must not halt processing for all subsequent orders.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class OrderConsumer implements Runnable {

    private final BlockingQueue<String> queue;
    private final OrderController controller;

    /**
     * Constructs a consumer bound to the given queue and controller.
     *
     * @param queue      the shared ingestion queue to drain
     * @param controller the controller that routes parsed orders to stalls
     */
    public OrderConsumer(BlockingQueue<String> queue, OrderController controller) {
        this.queue = queue;
        this.controller = controller;
    }

    /**
     * Consumer loop: runs until the thread is interrupted (e.g. by
     * {@code ExecutorService.shutdownNow()} or an explicit interrupt from the
     * test harness).
     *
     * <p>On {@link InterruptedException} the interrupt flag is re-set so that
     * the enclosing executor infrastructure can clean up correctly and the loop
     * exits cleanly.
     *
     * <p>On {@link OrderProcessingException} the malformed payload is logged
     * to {@code stderr} with context but the loop continues — a single bad
     * payload must not stop the consumer.
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // blocks if queue is empty — efficient idle wait
                String raw = queue.take();

                Order order = JsonUtil.parseOrder(raw);    // may throw OrderProcessingException
                controller.routeOrder(order);              // ConcurrentHashMap lookup + enqueue

            } catch (InterruptedException e) {
                // Re-set the interrupt flag and exit the loop cleanly.
                Thread.currentThread().interrupt();

            } catch (OrderProcessingException e) {
                // Never silently swallowed — always log with cause chain.
                // getCause() carries the original parse error for diagnosis.
                System.err.println("[Consumer] dropped malformed order: " + e.getMessage()
                        + (e.getCause() != null ? " caused by: " + e.getCause() : ""));
            }
        }
    }
}
