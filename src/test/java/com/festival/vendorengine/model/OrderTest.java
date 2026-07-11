package com.festival.vendorengine.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void testLineItemGettersAndSubtotal() {
        Order.LineItem item = new Order.LineItem("Biryani", 3, 120.0);
        assertEquals("Biryani", item.getItemName());
        assertEquals(3, item.getQuantity());
        assertEquals(120.0, item.getUnitPrice(), 1e-9);
        assertEquals(360.0, item.getSubtotal(), 1e-9);
    }

    @Test
    void testOrderGettersAndSetters() {
        Order.LineItem item = new Order.LineItem("Samosa", 2, 50.0);
        long createdAt = System.currentTimeMillis() - 1000;
        Order order = new Order("ORD-001", "S01", List.of(item), PriorityToken.VIP, createdAt);

        assertEquals("ORD-001", order.getOrderId());
        assertEquals("S01", order.getStallId());
        assertEquals(1, order.getItems().size());
        assertEquals(item, order.getItems().get(0));
        assertEquals(PriorityToken.VIP, order.getPriorityToken());
        assertEquals(100, order.getPriority());
        assertEquals(createdAt, order.getCreatedAtMillis());
        assertEquals(OrderStatus.PENDING, order.getStatus());

        order.setStatus(OrderStatus.ACCEPTED);
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
    }

    @Test
    void testGetElapsedMsMonotonicIncrease() throws InterruptedException {
        long createdAt = System.currentTimeMillis() - 50;
        Order order = new Order("ORD-002", "S01", List.of(), PriorityToken.STANDARD, createdAt);

        long firstElapsed = order.getElapsedMs();
        assertTrue(firstElapsed >= 50, "Elapsed time should be at least the difference: " + firstElapsed);

        Thread.sleep(15);

        long secondElapsed = order.getElapsedMs();
        assertTrue(secondElapsed > firstElapsed, "Elapsed time must monotonically increase. First: " + firstElapsed + ", Second: " + secondElapsed);
    }

    @Test
    void testEnumsCoverage() {
        // OrderStatus
        assertEquals(5, OrderStatus.values().length);
        assertEquals(OrderStatus.PENDING, OrderStatus.valueOf("PENDING"));

        // UserRole
        assertEquals(2, UserRole.values().length);
        assertEquals(UserRole.KITCHEN_WORKER, UserRole.valueOf("KITCHEN_WORKER"));

        // PriorityToken
        assertEquals(2, PriorityToken.values().length);
        assertEquals(PriorityToken.VIP, PriorityToken.valueOf("VIP"));
        assertEquals(100, PriorityToken.VIP.getWeight());
        assertEquals(0, PriorityToken.STANDARD.getWeight());
    }
}
