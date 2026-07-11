package com.festival.vendorengine.controller;

import com.festival.vendorengine.model.Stall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only façade over the shared stall registry.
 *
 * <p><strong>Purpose (Section 7.3):</strong> {@code AdminView} and any other
 * component that needs to <em>read</em> stall data (list stalls, aggregate
 * revenue, inspect queue depths) uses this class rather than reaching directly
 * into {@code OrderController}. This separation keeps the view layer from ever
 * obtaining a reference to {@code OrderController}'s mutation methods
 * ({@code routeOrder}, {@code transitionStatus}) — a hard MVC constraint.
 *
 * <p>All methods return defensive snapshots (unmodifiable views or copies)
 * rather than exposing the live {@code ConcurrentHashMap} directly, so callers
 * cannot accidentally mutate the registry.
 *
 * <p><strong>DAO shape:</strong> {@code StallDataSource} already has the
 * read-only façade structure of a DAO and is described in the architecture
 * document as "JDBC-ready" — a future persistence layer could back this class
 * with a real database query without changing any caller.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class StallDataSource {

    private final ConcurrentHashMap<String, Stall> stallMap;

    /**
     * Constructs a data source backed by the same stall map as
     * {@code OrderController}. Both must receive the <em>same</em> map instance
     * (passed in from {@code Main}) so that stalls created dynamically by
     * {@code OrderController.routeOrder()} are immediately visible here.
     *
     * @param stallMap the shared stall registry; must not be null
     */
    public StallDataSource(ConcurrentHashMap<String, Stall> stallMap) {
        if (stallMap == null) throw new IllegalArgumentException("stallMap must not be null");
        this.stallMap = stallMap;
    }

    // -------------------------------------------------------------------------
    // Stall enumeration
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable, point-in-time snapshot of all registered stall IDs,
     * sorted lexicographically for stable display order in the UI.
     *
     * @return sorted, unmodifiable list of stall IDs; never null, may be empty
     */
    public List<String> getAllStallIds() {
        List<String> ids = new ArrayList<>(stallMap.keySet());
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    /**
     * Returns an unmodifiable, point-in-time snapshot of all registered
     * {@link Stall} objects.
     *
     * @return unmodifiable collection of stalls; never null, may be empty
     */
    public Collection<Stall> getAllStalls() {
        return Collections.unmodifiableCollection(
                new ArrayList<>(stallMap.values()));
    }

    /**
     * Looks up a single stall by its ID.
     *
     * @param stallId the stall identifier to look up
     * @return the {@link Stall} for that ID, or {@code null} if not registered
     */
    public Stall getStall(String stallId) {
        return stallMap.get(stallId);
    }

    // -------------------------------------------------------------------------
    // Aggregate metrics
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of currently registered stalls.
     *
     * @return stall count; 0 if no stalls have been registered yet
     */
    public int getStallCount() {
        return stallMap.size();
    }

    /**
     * Computes the aggregate revenue across <em>all</em> stalls at the moment
     * this method is called.
     *
     * <p>Note: individual {@code Stall.getRevenueTotal()} calls are
     * {@code synchronized} on the stall instance (see {@code Stall}); summing
     * them here is a point-in-time snapshot — not a globally atomic read —
     * which is acceptable for a display metric.
     *
     * @return sum of {@code revenueTotal} across all stalls; 0.0 if empty
     */
    public double getTotalRevenue() {
        return stallMap.values().stream()
                .mapToDouble(Stall::getRevenueTotal)
                .sum();
    }

    /**
     * Returns the total number of orders currently sitting in all stall queues
     * combined (i.e. the number of orders not yet dequeued by consumers).
     *
     * <p>Like {@link #getTotalRevenue()}, this is a point-in-time snapshot.
     *
     * @return sum of {@code orderQueue.size()} across all stalls
     */
    public int getTotalQueueDepth() {
        return stallMap.values().stream()
                .mapToInt(stall -> stall.getOrderQueue().size())
                .sum();
    }
}
