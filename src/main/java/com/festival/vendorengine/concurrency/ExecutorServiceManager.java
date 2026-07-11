package com.festival.vendorengine.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Application-wide thread pool manager — eagerly-initialized Singleton.
 *
 * <p><strong>Singleton choice (Section 6.1):</strong> the instance is created
 * when the class is first loaded by the JVM classloader. Because class loading
 * is inherently thread-safe (guaranteed by the Java Language Specification §12.4),
 * no double-checked locking, {@code volatile}, or {@code synchronized} block is
 * needed. This is the simplest correct form of the Singleton pattern.
 *
 * <p><strong>Pool size = 8 (architecture decision):</strong> matches the
 * "≥8 concurrent stall threads" success metric from the deck. Eight consumers
 * pulling from one shared {@code LinkedBlockingQueue<String>} gives real
 * parallelism for JSON parsing + routing with zero shared mutable state between
 * consumers ({@code ConcurrentHashMap} in {@code OrderController} handles the
 * only shared structure).
 *
 * <p>{@link #shutdown()} must be called from a JVM shutdown hook in {@code Main}:
 * <pre>
 *     Runtime.getRuntime().addShutdownHook(new Thread(
 *         ExecutorServiceManager.getInstance()::shutdown, "pool-shutdown-hook"));
 * </pre>
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public final class ExecutorServiceManager {

    // Eagerly created at class-load time — thread-safe by JLS §12.4, no lock needed.
    private static final ExecutorServiceManager INSTANCE = new ExecutorServiceManager();

    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    private ExecutorServiceManager() {
        // Private constructor: enforces singleton; prevents accidental
        // instantiation that would create a second, untracked thread pool.
    }

    /**
     * Returns the sole {@code ExecutorServiceManager} instance.
     *
     * @return the singleton
     */
    public static ExecutorServiceManager getInstance() {
        return INSTANCE;
    }

    /**
     * Submits a task to the shared fixed thread pool.
     *
     * @param task the {@code Runnable} to execute
     */
    public void submit(Runnable task) {
        pool.submit(task);
    }

    /**
     * Gracefully shuts down the pool, waiting up to 5 seconds for in-flight
     * tasks to finish. If any task does not finish within that window the pool
     * is force-stopped via {@link ExecutorService#shutdownNow()}.
     *
     * <p>Registers thread-interrupt state correctly so that callers running
     * inside a shutdown hook can still detect interruption.
     */
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
