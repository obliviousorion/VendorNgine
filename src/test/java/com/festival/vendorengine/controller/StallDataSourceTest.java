package com.festival.vendorengine.controller;

import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.PriorityToken;
import com.festival.vendorengine.model.Stall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class StallDataSourceTest {

    private ConcurrentHashMap<String, Stall> stallMap;
    private StallDataSource dataSource;

    @BeforeEach
    void setUp() {
        stallMap = new ConcurrentHashMap<>();
        dataSource = new StallDataSource(stallMap);
    }

    @Test
    void testConstructorThrowsOnNullMap() {
        assertThrows(IllegalArgumentException.class, () -> new StallDataSource(null));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testGettersAndMetrics() {
        // Empty map state
        assertEquals(0, dataSource.getStallCount());
        assertEquals(0.0, dataSource.getTotalRevenue(), 1e-9);
        assertEquals(0, dataSource.getTotalQueueDepth());
        assertTrue(dataSource.getAllStallIds().isEmpty());
        assertTrue(dataSource.getAllStalls().isEmpty());
        assertNull(dataSource.getStall("S01"));

        // Populate S01
        Stall s1 = new Stall("S01", "Tikka Corner");
        s1.addRevenue(150.0);
        Order o1 = new Order("ORD-1", "S01", List.of(), PriorityToken.STANDARD, System.currentTimeMillis());
        s1.enqueue(o1);

        stallMap.put("S01", s1);

        // Populate S02
        Stall s2 = new Stall("S02", "Dosa Hut");
        s2.addRevenue(300.0);
        Order o2 = new Order("ORD-2", "S02", List.of(), PriorityToken.VIP, System.currentTimeMillis());
        Order o3 = new Order("ORD-3", "S02", List.of(), PriorityToken.STANDARD, System.currentTimeMillis());
        s2.enqueue(o2);
        s2.enqueue(o3);

        stallMap.put("S02", s2);

        // Assert updated state
        assertEquals(2, dataSource.getStallCount());
        assertEquals(450.0, dataSource.getTotalRevenue(), 1e-9);
        assertEquals(3, dataSource.getTotalQueueDepth());

        // Get single stall lookup
        assertEquals(s1, dataSource.getStall("S01"));
        assertEquals(s2, dataSource.getStall("S02"));
        assertNull(dataSource.getStall("S03"));

        // Check sorted stall IDs
        List<String> ids = dataSource.getAllStallIds();
        assertEquals(2, ids.size());
        assertEquals("S01", ids.get(0));
        assertEquals("S02", ids.get(1));

        // Unmodifiable verification
        assertThrows(UnsupportedOperationException.class, () -> ids.add("S03"));

        // Check collection snapshot
        Collection<Stall> stalls = dataSource.getAllStalls();
        assertEquals(2, stalls.size());
        assertTrue(stalls.contains(s1));
        assertTrue(stalls.contains(s2));

        // Unmodifiable verification for stalls collection snapshot
        assertThrows(UnsupportedOperationException.class, () -> stalls.add(new Stall("S03", "Test")));
    }
}
