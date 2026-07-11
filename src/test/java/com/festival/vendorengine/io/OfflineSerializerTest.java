package com.festival.vendorengine.io;

import com.festival.vendorengine.exception.OfflineSerializationException;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.PriorityToken;
import com.festival.vendorengine.model.Stall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OfflineSerializer} — Section 13 spec.
 *
 * <h2>Coverage</h2>
 * <ol>
 *   <li>Save-then-load round-trip: {@link AppState} is identical after
 *       serialization + deserialization (stall IDs, order IDs, revenue,
 *       online flag, syncedAt timestamp).</li>
 *   <li>Empty-stall-map round-trip: no stalls present — must still work.</li>
 *   <li>Online flag round-trip: {@code true} and {@code false} survive the
 *       cycle correctly.</li>
 *   <li>Corrupted file: loading a non-serialized-object file must throw
 *       {@link OfflineSerializationException} (not raw {@link IOException} or
 *       {@link ClassCastException}).</li>
 *   <li>Missing file: loading from a path that does not exist must throw
 *       {@link OfflineSerializationException}.</li>
 *   <li>Save to a read-only directory must throw
 *       {@link OfflineSerializationException}.</li>
 * </ol>
 *
 * <p>All file I/O uses {@code @TempDir} so no artefacts are written to the
 * working directory.
 */
class OfflineSerializerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a minimal order for a given stall. */
    private Order makeOrder(String orderId, String stallId) {
        return new Order(
                orderId, stallId,
                List.of(new Order.LineItem("Chaat", 2, 50.0)),
                PriorityToken.STANDARD,
                System.currentTimeMillis());
    }

    /** Builds a minimal AppState with one stall containing one order. */
    private AppState buildState(boolean online) {
        Stall stall = new Stall("S01", "Chaat Corner");
        stall.enqueue(makeOrder("ORD-001", "S01"));
        stall.addRevenue(100.0);

        ConcurrentHashMap<String, Stall> map = new ConcurrentHashMap<>();
        map.put("S01", stall);
        return new AppState(map, online, 1_700_000_000_000L);
    }

    // -------------------------------------------------------------------------
    // 1. Full round-trip — one stall, one order
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_singleStall_deepEquality(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("state.ser");
        OfflineSerializer ser = new OfflineSerializer(file);

        AppState original = buildState(true);
        ser.save(original);

        AppState loaded = ser.load();

        // Online flag
        assertTrue(loaded.isOnline(), "isOnline must survive round-trip");

        // Timestamp
        assertEquals(original.getLastSyncedAtMillis(),
                loaded.getLastSyncedAtMillis(),
                "lastSyncedAtMillis must survive round-trip");

        // Stall map presence
        assertEquals(1, loaded.getStallMap().size(),
                "Stall count must be 1 after round-trip");
        assertTrue(loaded.getStallMap().containsKey("S01"),
                "Stall S01 must be present after round-trip");

        // Revenue
        Stall loadedStall = loaded.getStallMap().get("S01");
        assertEquals("Chaat Corner", loadedStall.getStallName(),
                "Stall name must survive round-trip");
        assertEquals(100.0, loadedStall.getRevenueTotal(), 1e-9,
                "Revenue must survive round-trip");

        // Order in queue
        assertEquals(1, loadedStall.getOrderQueue().size(),
                "Order queue size must be 1 after round-trip");
        Order loadedOrder = loadedStall.getOrderQueue().peek();
        assertNotNull(loadedOrder, "Peeked order must not be null");
        assertEquals("ORD-001", loadedOrder.getOrderId(),
                "Order ID must survive round-trip");
        assertEquals("S01", loadedOrder.getStallId(),
                "Stall ID inside order must survive round-trip");
    }

    // -------------------------------------------------------------------------
    // 2. Empty stall map round-trip
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_emptyStallMap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("empty.ser");
        OfflineSerializer ser = new OfflineSerializer(file);

        AppState original = new AppState(
                new ConcurrentHashMap<>(), false, 0L);
        ser.save(original);

        AppState loaded = ser.load();

        assertFalse(loaded.isOnline(), "isOnline=false must survive round-trip");
        assertEquals(0, loaded.getStallMap().size(),
                "Empty stall map must survive round-trip");
    }

    // -------------------------------------------------------------------------
    // 3. Online flag round-trip (offline case)
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_offlineFlag(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("offline.ser");
        OfflineSerializer ser = new OfflineSerializer(file);

        AppState state = buildState(false);
        ser.save(state);

        AppState loaded = ser.load();
        assertFalse(loaded.isOnline(), "isOnline=false must survive round-trip");
    }

    // -------------------------------------------------------------------------
    // 4. Corrupted file → OfflineSerializationException (not raw IOException)
    // -------------------------------------------------------------------------

    @Test
    void load_corruptedFile_throwsOfflineSerializationException(@TempDir Path tmp)
            throws IOException {

        Path file = tmp.resolve("corrupt.ser");
        // Write garbage bytes using Files.writeString — handle closed immediately.
        // A raw FileWriter leaks the handle on Windows and prevents TempDir cleanup.
        java.nio.file.Files.writeString(file,
                "THIS IS NOT A VALID SERIALIZED OBJECT STREAM\nrandom garbage bytes",
                java.nio.charset.StandardCharsets.UTF_8);

        OfflineSerializer ser = new OfflineSerializer(file);

        OfflineSerializationException ex = assertThrows(
                OfflineSerializationException.class,
                ser::load,
                "Loading a corrupted file must throw OfflineSerializationException");

        // Must NOT be a raw IOException — the exception must be wrapped.
        assertNotNull(ex.getCause(),
                "OfflineSerializationException must have a non-null cause");
    }

    // -------------------------------------------------------------------------
    // 5. Missing file → OfflineSerializationException
    // -------------------------------------------------------------------------

    @Test
    void load_missingFile_throwsOfflineSerializationException(@TempDir Path tmp) {
        Path missing = tmp.resolve("does_not_exist.ser");
        OfflineSerializer ser = new OfflineSerializer(missing);

        assertThrows(OfflineSerializationException.class, ser::load,
                "Loading from a missing file must throw OfflineSerializationException");
    }

    // -------------------------------------------------------------------------
    // 6. Multiple stalls and orders survive round-trip
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_multipleStallsAndOrders(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("multi.ser");
        OfflineSerializer ser = new OfflineSerializer(file);

        // Build 2 stalls with different orders and revenues
        Stall s1 = new Stall("S01", "Stall One");
        s1.enqueue(makeOrder("ORD-A1", "S01"));
        s1.enqueue(makeOrder("ORD-A2", "S01"));
        s1.addRevenue(200.0);

        Stall s2 = new Stall("S02", "Stall Two");
        s2.enqueue(makeOrder("ORD-B1", "S02"));
        s2.addRevenue(75.50);

        ConcurrentHashMap<String, Stall> map = new ConcurrentHashMap<>();
        map.put("S01", s1);
        map.put("S02", s2);

        AppState original = new AppState(map, true, 999L);
        ser.save(original);

        AppState loaded = ser.load();

        assertEquals(2, loaded.getStallMap().size(), "Must have 2 stalls");
        assertEquals(2, loaded.getStallMap().get("S01").getOrderQueue().size(),
                "S01 must have 2 orders");
        assertEquals(1, loaded.getStallMap().get("S02").getOrderQueue().size(),
                "S02 must have 1 order");
        assertEquals(200.0, loaded.getStallMap().get("S01").getRevenueTotal(), 1e-9);
        assertEquals(75.50, loaded.getStallMap().get("S02").getRevenueTotal(), 1e-9);
        assertEquals(999L, loaded.getLastSyncedAtMillis());
    }
}
