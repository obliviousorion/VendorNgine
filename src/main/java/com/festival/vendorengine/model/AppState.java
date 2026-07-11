package com.festival.vendorengine.model;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snapshot of the entire application state — the object that is serialized to
 * {@code offline_stalls.ser} when the network goes down and restored on reconnect.
 *
 * <p>{@code stallMap} uses {@link ConcurrentHashMap}, which is itself
 * {@link Serializable}, so the whole graph serializes cleanly provided every
 * value type ({@code Stall → PriorityBlockingQueue<Order> → Order → LineItem})
 * also implements {@code Serializable}.
 *
 * <p>{@code online} is {@code volatile} because {@code NetworkMonitorDaemon}
 * (a dedicated daemon thread) flips it without acquiring any lock. The view
 * layer reads it from the EDT; a volatile write is sufficient to ensure
 * visibility without a full synchronized block.
 *
 * <p>This class is a pure snapshot: it exposes only getters. Only
 * {@code OrderController} should create or replace an {@code AppState} instance.
 *
 * <p>Bump {@code serialVersionUID} if you ever add or remove fields so that
 * stale {@code .ser} files are rejected cleanly rather than silently
 * deserializing corrupt data.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class AppState implements Serializable {

    private static final long serialVersionUID = 1L; // BUMP this if fields ever change shape

    private final ConcurrentHashMap<String, Stall> stallMap;
    private volatile boolean online;
    private final long lastSyncedAtMillis;

    public AppState(ConcurrentHashMap<String, Stall> stallMap,
                    boolean online,
                    long lastSyncedAtMillis) {
        this.stallMap = stallMap;
        this.online = online;
        this.lastSyncedAtMillis = lastSyncedAtMillis;
    }

    // -------------------------------------------------------------------------
    // Getters only — this class is a snapshot, mutated only by OrderController
    // -------------------------------------------------------------------------

    public ConcurrentHashMap<String, Stall> getStallMap() {
        return stallMap;
    }

    public boolean isOnline() {
        return online;
    }

    /**
     * Flips the connectivity flag.
     *
     * <p>Only {@code NetworkMonitorDaemon} should call this method.
     * The field is {@code volatile}, so the write is immediately visible
     * to all other threads (including the EDT) without any lock.
     *
     * @param online {@code true} if the network heartbeat is reachable
     */
    public void setOnline(boolean online) {
        this.online = online;
    }

    public long getLastSyncedAtMillis() {
        return lastSyncedAtMillis;
    }
}
