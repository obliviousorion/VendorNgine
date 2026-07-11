package com.festival.vendorengine.io;

import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.PriorityToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncClientTest {

    @Test
    void testConstructors() {
        SyncClient defaultClient = new SyncClient();
        assertEquals(SyncClient.DEFAULT_FILE_NAME, defaultClient.getFilePath());

        SyncClient customClient = new SyncClient("custom_sync.json");
        assertEquals("custom_sync.json", customClient.getFilePath());
    }

    @Test
    void testPushBatchSuccess(@TempDir Path tempDir) throws IOException {
        Path syncFile = tempDir.resolve("sync_payload.json");
        SyncClient client = new SyncClient(syncFile.toString());

        List<Order> orders = new ArrayList<>();
        // Order 1: standard
        orders.add(new Order("ORD-\"1\"", "S0\\1", 
                List.of(new Order.LineItem("Samosa", 2, 50.0)), 
                PriorityToken.STANDARD, 1000L));
        // Order 2: VIP
        orders.add(new Order("ORD-2", "S02", 
                List.of(new Order.LineItem("Tikka", 1, 100.0)), 
                PriorityToken.VIP, 2000L));

        client.pushBatch(orders);

        assertTrue(Files.exists(syncFile));
        String content = Files.readString(syncFile);

        assertTrue(content.contains("\"syncedAt\":"));
        assertTrue(content.contains("\"orders\":"));
        // Check escaping of order ID and stall ID
        assertTrue(content.contains("\"orderId\": \"ORD-\\\"1\\\"\""));
        assertTrue(content.contains("\"stallId\": \"S0\\\\1\""));
        assertTrue(content.contains("\"status\": \"PENDING\""));
        assertTrue(content.contains("\"total\": 100.0"));
        assertTrue(content.contains("\"orderId\": \"ORD-2\""));
        assertTrue(content.contains("\"total\": 100.0"));
    }

    @Test
    void testPushBatchEmpty(@TempDir Path tempDir) throws IOException {
        Path syncFile = tempDir.resolve("sync_empty.json");
        SyncClient client = new SyncClient(syncFile.toString());

        client.pushBatch(new ArrayList<>());

        assertTrue(Files.exists(syncFile));
        String content = Files.readString(syncFile);
        assertTrue(content.contains("\"orders\": [\n  ]"));
    }

    @Test
    void testPushBatchIOError(@TempDir Path tempDir) {
        // Try to write to the tempDir itself as a file, which triggers a FileWriter exception.
        SyncClient client = new SyncClient(tempDir.toString());
        assertDoesNotThrow(() -> client.pushBatch(new ArrayList<>()));
    }
}
