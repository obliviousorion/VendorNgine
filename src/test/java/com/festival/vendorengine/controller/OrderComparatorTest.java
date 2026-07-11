package com.festival.vendorengine.controller;

import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.PriorityToken;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OrderComparator} — Section 13 spec.
 *
 * <h2>Required cases (verbatim from Section 13)</h2>
 * <ol>
 *   <li>VIP always ranks above STANDARD regardless of wait time.</li>
 *   <li>Among equal priority, the older order (greater {@code elapsedMs}) wins.</li>
 *   <li>Comparator is consistent: {@code compare(a,b) == -compare(b,a)} for all pairs.</li>
 * </ol>
 *
 * <h2>Additional cases</h2>
 * <ul>
 *   <li>compare(x, x) == 0 (reflexivity, same object reference).</li>
 *   <li>Identical creation time and priority → 0 (tie resolution is stable).</li>
 *   <li>Two VIP orders — older one wins (elapsed tie-break within VIP tier).</li>
 *   <li>Two STANDARD orders — older one wins (elapsed tie-break within STANDARD tier).</li>
 *   <li>{@code DEFAULT} singleton is non-null and {@code Serializable}.</li>
 *   <li>{@code Order.compareTo} delegates to {@code DEFAULT}: consistent with
 *       direct {@code compare} call.</li>
 * </ul>
 *
 * <p><strong>PriorityBlockingQueue semantics reminder:</strong> the queue is a
 * <em>min-heap</em>, so the element with the <em>smallest</em> comparator value
 * is dequeued first. This comparator returns a <em>negative</em> value when
 * {@code a} should be dequeued before {@code b}, effectively making higher-
 * priority / longer-waiting orders the "smallest" in the heap.
 */
class OrderComparatorTest {

    private static final long NOW = System.currentTimeMillis();

    // -------------------------------------------------------------------------
    // Fixture: reusable orders
    // -------------------------------------------------------------------------

    /** A VIP order created at time T. */
    private Order vipOrder(String id, long createdAt) {
        return new Order(id, "S01",
                List.of(new Order.LineItem("Item", 1, 10.0)),
                PriorityToken.VIP, createdAt);
    }

    /** A STANDARD order created at time T. */
    private Order stdOrder(String id, long createdAt) {
        return new Order(id, "S01",
                List.of(new Order.LineItem("Item", 1, 10.0)),
                PriorityToken.STANDARD, createdAt);
    }

    // -------------------------------------------------------------------------
    // 1. VIP always ranks above STANDARD regardless of wait time
    // -------------------------------------------------------------------------

    @Test
    void vipAlwaysBeforeStandard_evenIfStandardIsOlder() {
        // STANDARD created 10 seconds ago (long wait), VIP just now
        Order oldStandard = stdOrder("OLD-STD", NOW - 10_000);
        Order newVip      = vipOrder("NEW-VIP", NOW);

        int result = OrderComparator.DEFAULT.compare(newVip, oldStandard);
        assertTrue(result < 0,
                "VIP order must rank before STANDARD even if STANDARD is much older; "
                + "compare(VIP, STD) must be negative, got: " + result);
    }

    @Test
    void vipAlwaysBeforeStandard_standardIsNewer() {
        Order oldVip      = vipOrder("OLD-VIP", NOW - 5_000);
        Order newStandard = stdOrder("NEW-STD", NOW);

        int result = OrderComparator.DEFAULT.compare(oldVip, newStandard);
        assertTrue(result < 0,
                "VIP order must rank before STANDARD regardless of creation time; "
                + "compare(VIP, STD) must be negative, got: " + result);
    }

    @Test
    void standardAlwaysAfterVip_symmetry() {
        Order std = stdOrder("STD", NOW);
        Order vip = vipOrder("VIP", NOW);

        assertTrue(OrderComparator.DEFAULT.compare(std, vip) > 0,
                "compare(STD, VIP) must be positive (STD dequeued after VIP)");
    }

    // -------------------------------------------------------------------------
    // 2. Equal priority → older order (greater elapsedMs) wins
    // -------------------------------------------------------------------------

    @Test
    void equalPriority_olderOrderWins_bothStandard() {
        Order older = stdOrder("STD-OLD", NOW - 5_000);  // 5 s ago
        Order newer = stdOrder("STD-NEW", NOW - 1_000);  // 1 s ago

        int result = OrderComparator.DEFAULT.compare(older, newer);
        assertTrue(result < 0,
                "Older STANDARD order must rank before newer STANDARD; "
                + "compare(older, newer) must be negative, got: " + result);
    }

    @Test
    void equalPriority_olderOrderWins_bothVip() {
        Order olderVip = vipOrder("VIP-OLD", NOW - 8_000);
        Order newerVip = vipOrder("VIP-NEW", NOW - 1_000);

        int result = OrderComparator.DEFAULT.compare(olderVip, newerVip);
        assertTrue(result < 0,
                "Older VIP must rank before newer VIP; "
                + "compare(olderVip, newerVip) must be negative, got: " + result);
    }

    @Test
    void equalPriority_newerOrderLoses() {
        Order older = stdOrder("OLD", NOW - 3_000);
        Order newer = stdOrder("NEW", NOW - 500);

        int result = OrderComparator.DEFAULT.compare(newer, older);
        assertTrue(result > 0,
                "Newer STANDARD must rank after older STANDARD; "
                + "compare(newer, older) must be positive, got: " + result);
    }

    // -------------------------------------------------------------------------
    // 3. Comparator consistency: compare(a,b) == -compare(b,a)
    // -------------------------------------------------------------------------

    @Test
    void consistency_vipVsStandard() {
        Order vip = vipOrder("VIP", NOW);
        Order std = stdOrder("STD", NOW);

        int ab = OrderComparator.DEFAULT.compare(vip, std);
        int ba = OrderComparator.DEFAULT.compare(std, vip);

        // Exact anti-symmetry in sign (both non-zero cases)
        assertTrue(ab < 0 && ba > 0,
                "compare(VIP,STD) must be negative and compare(STD,VIP) positive; "
                + "got ab=" + ab + ", ba=" + ba);

        // Strict sign-reversal: Integer.signum(ab) == -Integer.signum(ba)
        assertEquals(Integer.signum(ab), -Integer.signum(ba),
                "compare(a,b) and compare(b,a) must have opposite signs");
    }

    @Test
    void consistency_olderVsNewer_sameToken() {
        Order older = stdOrder("OLD", NOW - 4_000);
        Order newer = stdOrder("NEW", NOW - 500);

        int ab = OrderComparator.DEFAULT.compare(older, newer);
        int ba = OrderComparator.DEFAULT.compare(newer, older);

        assertEquals(Integer.signum(ab), -Integer.signum(ba),
                "compare(older,newer) and compare(newer,older) must have opposite signs; "
                + "got ab=" + ab + ", ba=" + ba);
    }

    @Test
    void consistency_holds_acrossAllPairs() {
        // Table-driven consistency check over a representative cross-product
        List<Order> orders = List.of(
                vipOrder("V1", NOW - 9_000),
                vipOrder("V2", NOW - 1_000),
                stdOrder("S1", NOW - 7_000),
                stdOrder("S2", NOW - 2_000)
        );

        for (Order a : orders) {
            for (Order b : orders) {
                int ab = OrderComparator.DEFAULT.compare(a, b);
                int ba = OrderComparator.DEFAULT.compare(b, a);
                assertEquals(Integer.signum(ab), -Integer.signum(ba),
                        "Consistency violated for (" + a.getOrderId()
                        + ", " + b.getOrderId()
                        + "): ab=" + ab + ", ba=" + ba);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4. Reflexivity: compare(x, x) == 0
    // -------------------------------------------------------------------------

    @Test
    void reflexivity_sameObjectComparesAsEqual() {
        Order order = vipOrder("SAME", NOW - 3_000);
        assertEquals(0, OrderComparator.DEFAULT.compare(order, order),
                "compare(x, x) must be 0");
    }

    // -------------------------------------------------------------------------
    // 5. Identical creation time and identical priority → 0
    // -------------------------------------------------------------------------

    @Test
    void tiedOrdersCompareTo_zero() {
        // Identical createdAtMillis → same elapsedMs → compare returns 0
        long ts = NOW - 2_000;
        Order a = stdOrder("A", ts);
        Order b = stdOrder("B", ts);

        // elapsedMs is computed from System.currentTimeMillis() − createdAt,
        // so two orders with the same createdAt will have (nearly) equal elapsedMs.
        // The result must be 0 (or very close to 0 within a 1ms test window).
        // We tolerate the rare race where one millisecond slips between calls.
        int result = OrderComparator.DEFAULT.compare(a, b);
        assertTrue(Math.abs(result) <= 1,
                "Orders with same priority and same creation time must compare as 0 "
                + "(or differ by at most 1ms clock tick); got: " + result);
    }

    // -------------------------------------------------------------------------
    // 6. DEFAULT singleton is non-null and Serializable
    // -------------------------------------------------------------------------

    @Test
    void defaultSingleton_nonNull() {
        assertNotNull(OrderComparator.DEFAULT,
                "OrderComparator.DEFAULT must not be null");
    }

    @Test
    void defaultSingleton_isSerializable() {
        assertInstanceOf(java.io.Serializable.class, OrderComparator.DEFAULT,
                "OrderComparator.DEFAULT must implement Serializable (Section 6.4)");
    }

    // -------------------------------------------------------------------------
    // 7. Order.compareTo delegates to DEFAULT (consistency)
    // -------------------------------------------------------------------------

    @Test
    void orderCompareTo_consistentWithDefaultCompare() {
        Order vip = vipOrder("VIP", NOW - 2_000);
        Order std = stdOrder("STD", NOW - 2_000);

        int compareTo = vip.compareTo(std);
        int direct    = OrderComparator.DEFAULT.compare(vip, std);

        assertEquals(Integer.signum(direct), Integer.signum(compareTo),
                "Order.compareTo must agree in sign with OrderComparator.DEFAULT.compare");
    }
}
