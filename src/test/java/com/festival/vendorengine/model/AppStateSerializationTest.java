package com.festival.vendorengine.model;

import com.festival.vendorengine.io.OfflineSerializer;
import com.festival.vendorengine.model.Order.LineItem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Serialization round-trip test for {@link AppState} — Section 13 spec.
 *
 * <h2>Scenario</h2>
 * Build an {@link AppState} containing:
 * <ul>
 *   <li>2 stalls: {@code S01} ("Tikka House") and {@code S02} ("Juice Bar").</li>
 *   <li>3 orders total: 2 in {@code S01} (one VIP, one STANDARD) and 1 in {@code S02}.</li>
 *   <li>Revenue applied to both stalls.</li>
 * </ul>
 * Serialize to a {@code @TempDir} file, deserialize, and assert deep equality
 * on stall IDs, stall names, order IDs, item names, quantities, unit prices,
 * statuses, priorities, revenue totals, and the {@code online} flag.
 *
 * <h2>Why this test matters</h2>
 * The full type chain ({@code AppState → ConcurrentHashMap<String,Stall>
 * → PriorityBlockingQueue<Order> → Order → LineItem}) must implement
 * {@code Serializable} end-to-end. The comparator inside
 * {@code PriorityBlockingQueue} must be a named {@code Serializable} class
 * (not a lambda) — {@link com.festival.vendorengine.controller.OrderComparator}
 * satisfies this requirement (Section 6.4). If any class in the chain is missing
 * {@code Serializable} or the comparator is a lambda, this test fails with
 * {@code NotSerializableException}.
 */
class AppStateSerializationTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order makeOrder(String orderId, String stallId,
                            PriorityToken priority,
                            String itemName, int qty, double price) {
        return new Order(
                orderId, stallId,
                List.of(new LineItem(itemName, qty, price)),
                priority,
                1_700_000_000_000L);  // fixed timestamp for determinism
    }

    // -------------------------------------------------------------------------
    // Main round-trip assertion
    // -------------------------------------------------------------------------

    @Test
    void appStateRoundTrip_twoStalls_threeOrders_deepEquality(@TempDir Path tmp)
            throws Exception {

        // ----- Build originals -----

        // S01 — Tikka House: 1 VIP order (ORD-V) + 1 STANDARD order (ORD-S)
        Stall stall1 = new Stall("S01", "Tikka House");
        Order vipOrder = makeOrder("ORD-VIP", "S01", PriorityToken.VIP,
                "Tikka Platter", 1, 350.0);      // total = 350
        Order stdOrder = makeOrder("ORD-STD", "S01", PriorityToken.STANDARD,
                "Naan Bread",   2,  50.0);       // total = 100
        stall1.enqueue(vipOrder);
        stall1.enqueue(stdOrder);
        stall1.addRevenue(450.0);                // 350 + 100

        // S02 — Juice Bar: 1 STANDARD order (ORD-J)
        Stall stall2 = new Stall("S02", "Juice Bar");
        Order juiceOrder = makeOrder("ORD-JCE", "S02", PriorityToken.STANDARD,
                "Mango Lassi",  3,  80.0);       // total = 240
        stall2.enqueue(juiceOrder);
        stall2.addRevenue(240.0);

        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();
        stallMap.put("S01", stall1);
        stallMap.put("S02", stall2);

        long syncedAt = 1_700_000_123_456L;
        AppState original = new AppState(stallMap, true, syncedAt);

        // ----- Serialize -----
        Path serFile = tmp.resolve("appstate.ser");
        OfflineSerializer serializer = new OfflineSerializer(serFile);
        serializer.save(original);

        // ----- Deserialize -----
        AppState loaded = serializer.load();

        // =====================================================================
        // Top-level AppState assertions
        // =====================================================================

        assertTrue(loaded.isOnline(),
                "isOnline flag (true) must survive round-trip");
        assertEquals(syncedAt, loaded.getLastSyncedAtMillis(),
                "lastSyncedAtMillis must survive round-trip");
        assertEquals(2, loaded.getStallMap().size(),
                "Stall map must contain exactly 2 stalls after round-trip");

        // =====================================================================
        // S01 — Tikka House assertions
        // =====================================================================

        assertTrue(loaded.getStallMap().containsKey("S01"),
                "S01 must be present in the loaded stall map");
        Stall loadedS1 = loaded.getStallMap().get("S01");

        assertEquals("S01",        loadedS1.getStallId(),   "S01 stallId");
        assertEquals("Tikka House", loadedS1.getStallName(), "S01 stallName");
        assertEquals(450.0,        loadedS1.getRevenueTotal(), 1e-9, "S01 revenue");
        assertEquals(2,            loadedS1.getOrderQueue().size(),
                "S01 must have 2 orders in queue");

        // Both ORD-VIP and ORD-STD must be present (use a set-contains check
        // since PriorityBlockingQueue does not guarantee iteration order here)
        boolean foundVip = false, foundStd = false;
        for (Order o : loadedS1.getOrderQueue()) {
            if ("ORD-VIP".equals(o.getOrderId())) {
                foundVip = true;
                assertEquals("S01",             o.getStallId());
                assertEquals(PriorityToken.VIP,  o.getPriorityToken(), "VIP token preserved");
                assertEquals(OrderStatus.PENDING, o.getStatus(),       "status preserved");
                assertEquals(1, o.getItems().size(), "VIP order item count");
                LineItem vipItem = o.getItems().get(0);
                assertEquals("Tikka Platter", vipItem.getItemName());
                assertEquals(1,               vipItem.getQuantity());
                assertEquals(350.0,           vipItem.getUnitPrice(), 1e-9);
                assertEquals(350.0,           vipItem.getSubtotal(),  1e-9);
            }
            if ("ORD-STD".equals(o.getOrderId())) {
                foundStd = true;
                assertEquals(PriorityToken.STANDARD, o.getPriorityToken(), "STANDARD token preserved");
                LineItem stdItem = o.getItems().get(0);
                assertEquals("Naan Bread", stdItem.getItemName());
                assertEquals(2,            stdItem.getQuantity());
                assertEquals(50.0,         stdItem.getUnitPrice(), 1e-9);
                assertEquals(100.0,        stdItem.getSubtotal(),  1e-9);
            }
        }
        assertTrue(foundVip, "ORD-VIP must be present in S01 queue after round-trip");
        assertTrue(foundStd, "ORD-STD must be present in S01 queue after round-trip");

        // =====================================================================
        // S02 — Juice Bar assertions
        // =====================================================================

        assertTrue(loaded.getStallMap().containsKey("S02"),
                "S02 must be present in the loaded stall map");
        Stall loadedS2 = loaded.getStallMap().get("S02");

        assertEquals("S02",       loadedS2.getStallId(),   "S02 stallId");
        assertEquals("Juice Bar", loadedS2.getStallName(), "S02 stallName");
        assertEquals(240.0,       loadedS2.getRevenueTotal(), 1e-9, "S02 revenue");
        assertEquals(1,           loadedS2.getOrderQueue().size(),
                "S02 must have 1 order in queue");

        Order loadedJuice = loadedS2.getOrderQueue().peek();
        assertNotNull(loadedJuice, "S02's peeked order must not be null");
        assertEquals("ORD-JCE",              loadedJuice.getOrderId());
        assertEquals("S02",                  loadedJuice.getStallId());
        assertEquals(PriorityToken.STANDARD, loadedJuice.getPriorityToken());
        assertEquals(OrderStatus.PENDING,    loadedJuice.getStatus());
        assertEquals(1, loadedJuice.getItems().size());

        LineItem juiceItem = loadedJuice.getItems().get(0);
        assertEquals("Mango Lassi", juiceItem.getItemName());
        assertEquals(3,             juiceItem.getQuantity());
        assertEquals(80.0,          juiceItem.getUnitPrice(), 1e-9);
        assertEquals(240.0,         juiceItem.getSubtotal(),  1e-9);

        // =====================================================================
        // Total revenue across both stalls
        // =====================================================================

        double totalRevenue = loaded.getStallMap().values().stream()
                .mapToDouble(Stall::getRevenueTotal)
                .sum();
        assertEquals(690.0, totalRevenue, 1e-9,
                "Total revenue must be 450 + 240 = 690 after round-trip");
    }

    // -------------------------------------------------------------------------
    // Second test: offline flag + zero revenue round-trip
    // -------------------------------------------------------------------------

    @Test
    void appStateRoundTrip_offlineFlag_noRevenue(@TempDir Path tmp) throws Exception {
        Stall stall = new Stall("S03", "Empty Stall");
        ConcurrentHashMap<String, Stall> map = new ConcurrentHashMap<>();
        map.put("S03", stall);

        AppState original = new AppState(map, false, 0L);

        Path serFile = tmp.resolve("offline.ser");
        new OfflineSerializer(serFile).save(original);
        AppState loaded = new OfflineSerializer(serFile).load();

        assertFalse(loaded.isOnline(), "isOnline=false must survive round-trip");
        assertEquals(0L, loaded.getLastSyncedAtMillis());
        assertEquals(0.0, loaded.getStallMap().get("S03").getRevenueTotal(), 1e-9);
        assertEquals(0, loaded.getStallMap().get("S03").getOrderQueue().size());
    }
}
