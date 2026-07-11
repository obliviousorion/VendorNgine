package com.festival.vendorengine.concurrency;

import java.util.concurrent.BlockingQueue;

/**
 * Producer thread: pulls synthetic JSON order payloads from a
 * {@link PeakHourSimulator} and pushes them onto a shared
 * {@link BlockingQueue}{@code <String>} for the consumer pool to process.
 *
 * <p><strong>Producer-Consumer pattern (Section 6.2):</strong> one producer is
 * sufficient — the queue is the fan-out point. Increasing to N producers would
 * only make sense if the payload <em>generation</em> itself were CPU-bound, which
 * it isn't (the simulator is I/O / sleep dominated). Eight consumers handle the
 * downstream parallelism instead.
 *
 * <p>{@code queue.put(payload)} applies natural backpressure: if the queue is full
 * (capacity 1000 per the architecture spec) the producer thread blocks rather than
 * dropping data, giving consumers time to catch up. This is exactly the
 * "zero data loss" guarantee at the ingestion boundary.
 *
 * <p>{@code running} is {@code volatile} so that {@link #stop()} called from any
 * thread is immediately visible to the loop in {@link #run()} without requiring a
 * {@code synchronized} block (a single-writer, single-reader volatile flag).
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class OrderProducer implements Runnable {

    private final BlockingQueue<String> queue;
    private final PeakHourSimulator simulator;

    /**
     * Volatile flag: written by the main/control thread (via {@link #stop()}),
     * read by the producer thread (loop condition in {@link #run()}).
     * Volatile ensures the write is immediately visible without a lock.
     */
    private volatile boolean running = true;

    /**
     * Constructs a producer bound to the given queue and simulator.
     *
     * @param queue     the shared ingestion queue (e.g. {@code LinkedBlockingQueue})
     * @param simulator the payload source controlling emit rate and format
     */
    public OrderProducer(BlockingQueue<String> queue, PeakHourSimulator simulator) {
        this.queue = queue;
        this.simulator = simulator;
    }

    /**
     * Producer loop: runs until {@link #stop()} is called or the thread is
     * interrupted (e.g. by {@code ExecutorService.shutdownNow()}).
     *
     * <p>On {@link InterruptedException} the interrupt flag is re-set so that
     * the enclosing executor infrastructure can clean up correctly.
     */
    @Override
    public void run() {
        while (running) {
            try {
                // nextPayload() may sleep internally to honour the configured rate;
                // it also jitters the sleep interval ±20% for realism.
                String payload = simulator.nextPayload();

                // put() blocks if the queue is at capacity — built-in backpressure,
                // no data is ever dropped at the producer boundary.
                queue.put(payload);

            } catch (InterruptedException e) {
                // Re-set the interrupt flag so the executor can honour it.
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    /**
     * Signals the producer to stop after the current {@code nextPayload()} /
     * {@code put()} call returns. Does not block; the thread terminates on its
     * own schedule once it sees the flag.
     */
    public void stop() {
        running = false;
    }
}
