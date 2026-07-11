package com.festival.vendorengine.io;

import com.festival.vendorengine.exception.OfflineSerializationException;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.PriorityToken;
import com.festival.vendorengine.model.Stall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMonitorDaemonTest {

    @Test
    void testOnNetworkLostSuccess(@TempDir Path tempDir) throws Exception {
        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();
        AppState state = new AppState(stallMap, true, System.currentTimeMillis());

        Path serFile = tempDir.resolve("offline.ser");
        OfflineSerializer serializer = new OfflineSerializer(serFile);
        SyncClient syncClient = new SyncClient(tempDir.resolve("sync.json").toString());

        NetworkMonitorDaemon daemon = new NetworkMonitorDaemon(state, serializer, syncClient);

        // Precondition
        assertTrue(state.isOnline());

        daemon.onNetworkLost();

        // Verification
        assertFalse(state.isOnline(), "State should be marked offline after network lost");
        assertTrue(Files.exists(serFile), "State should be serialized to offline.ser");
    }

    @Test
    void testOnNetworkLostSerializationError(@TempDir Path tempDir) {
        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();
        AppState state = new AppState(stallMap, true, System.currentTimeMillis());

        // Use a serializer targeting a directory path to force an OfflineSerializationException
        OfflineSerializer failingSerializer = new OfflineSerializer(tempDir) {
            @Override
            public void save(AppState state) throws OfflineSerializationException {
                throw new OfflineSerializationException("Forced serialization error", new IOException("Simulated"));
            }
        };

        SyncClient syncClient = new SyncClient(tempDir.resolve("sync.json").toString());
        NetworkMonitorDaemon daemon = new NetworkMonitorDaemon(state, failingSerializer, syncClient);

        // Precondition
        assertTrue(state.isOnline());

        // Call should complete without throwing (exception is caught and logged)
        assertDoesNotThrow(daemon::onNetworkLost);

        // Verification: Even if serialization fails, state should still be marked offline
        assertFalse(state.isOnline());
    }

    @Test
    void testOnNetworkRestoredSyncsOnlyServedOrders(@TempDir Path tempDir) {
        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();
        Stall stall = new Stall("S01", "Mock Stall");

        // Enqueue three orders with different states: 2 SERVED, 1 PENDING
        Order o1 = new Order("ORD-1", "S01", List.of(), PriorityToken.STANDARD, 1000L);
        o1.setStatus(OrderStatus.SERVED);
        Order o2 = new Order("ORD-2", "S01", List.of(), PriorityToken.VIP, 2000L);
        o2.setStatus(OrderStatus.PENDING);
        Order o3 = new Order("ORD-3", "S01", List.of(), PriorityToken.STANDARD, 3000L);
        o3.setStatus(OrderStatus.SERVED);

        stall.enqueue(o1);
        stall.enqueue(o2);
        stall.enqueue(o3);

        stallMap.put("S01", stall);
        AppState state = new AppState(stallMap, false, System.currentTimeMillis());

        OfflineSerializer serializer = new OfflineSerializer(tempDir.resolve("offline.ser"));
        
        final List<Order> syncedOrders = new ArrayList<>();
        SyncClient mockSync = new SyncClient(tempDir.resolve("sync.json").toString()) {
            @Override
            public void pushBatch(List<Order> orders) {
                syncedOrders.addAll(orders);
            }
        };

        NetworkMonitorDaemon daemon = new NetworkMonitorDaemon(state, serializer, mockSync);

        // Precondition
        assertFalse(state.isOnline());

        daemon.onNetworkRestored();

        // Verification
        assertTrue(state.isOnline(), "State should be marked online after network restored");
        assertEquals(2, syncedOrders.size(), "Only SERVED orders should be synced");
        assertTrue(syncedOrders.stream().anyMatch(o -> o.getOrderId().equals("ORD-1")));
        assertTrue(syncedOrders.stream().anyMatch(o -> o.getOrderId().equals("ORD-3")));
        assertFalse(syncedOrders.stream().anyMatch(o -> o.getOrderId().equals("ORD-2")));
    }

    @Test
    void testDaemonLifecycleAndTransitions() throws Exception {
        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();
        AppState state = new AppState(stallMap, true, System.currentTimeMillis());

        AtomicInteger pingCount = new AtomicInteger(0);
        AtomicBoolean onNetworkLostCalled = new AtomicBoolean(false);
        AtomicBoolean onNetworkRestoredCalled = new AtomicBoolean(false);

        // Subclass NetworkMonitorDaemon to mock pingHeartbeat and transitions
        NetworkMonitorDaemon daemon = new NetworkMonitorDaemon(state, null, null) {
            @Override
            boolean pingHeartbeat() {
                int count = pingCount.incrementAndGet();
                if (count == 1) {
                    return false; // network drops
                } else if (count == 2) {
                    return true;  // network recovers
                }
                return true;
            }

            @Override
            void onNetworkLost() {
                onNetworkLostCalled.set(true);
            }

            @Override
            void onNetworkRestored() {
                onNetworkRestoredCalled.set(true);
            }
        };

        // We run the daemon loop in a thread using startDaemonThread
        Thread daemonThread = daemon.startDaemonThread();
        assertTrue(daemonThread.isDaemon(), "Thread must be marked as daemon");

        // Wait a short duration to let the loop run
        Thread.sleep(2500);

        // Terminate loop
        daemonThread.interrupt();
        daemonThread.join(1000);

        // Assert transitions occurred
        assertTrue(pingCount.get() >= 2, "Heartbeat should have pinged at least twice, got: " + pingCount.get());
        assertTrue(onNetworkLostCalled.get(), "onNetworkLost should be called when connection drops");
        assertTrue(onNetworkRestoredCalled.get(), "onNetworkRestored should be called when connection recovers");
    }

    @Test
    void testRealPingHeartbeat() throws IOException {
        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();
        AppState state = new AppState(stallMap, true, System.currentTimeMillis());
        NetworkMonitorDaemon daemon = new NetworkMonitorDaemon(state, null, null);

        Path flagPath = java.nio.file.Paths.get(NetworkMonitorDaemon.FLAG_FILE);
        Files.deleteIfExists(flagPath);

        try {
            // 1. Missing file -> offline
            assertFalse(daemon.pingHeartbeat());

            // 2. Empty file -> offline
            Files.write(flagPath, new byte[0]);
            assertFalse(daemon.pingHeartbeat());

            // 3. UTF-8 "up" -> online
            Files.writeString(flagPath, "up", java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(daemon.pingHeartbeat());

            // 4. UTF-8 "down" -> offline
            Files.writeString(flagPath, "down", java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(daemon.pingHeartbeat());

            // 5. UTF-16LE "up" with BOM -> online
            byte[] leBom = {(byte) 0xFF, (byte) 0xFE};
            byte[] leContent = "up\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
            byte[] leBytes = new byte[leBom.length + leContent.length];
            System.arraycopy(leBom, 0, leBytes, 0, leBom.length);
            System.arraycopy(leContent, 0, leBytes, leBom.length, leContent.length);
            Files.write(flagPath, leBytes);
            assertTrue(daemon.pingHeartbeat());

            // 6. UTF-16BE "up" with BOM -> online
            byte[] beBom = {(byte) 0xFE, (byte) 0xFF};
            byte[] beContent = "up\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_16BE);
            byte[] beBytes = new byte[beBom.length + beContent.length];
            System.arraycopy(beBom, 0, beBytes, 0, beBom.length);
            System.arraycopy(beContent, 0, beBytes, beBom.length, beContent.length);
            Files.write(flagPath, beBytes);
            assertTrue(daemon.pingHeartbeat());

            // 7. UTF-8 with BOM "up" -> online
            byte[] utf8Bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] utf8Content = "up".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] utf8Bytes = new byte[utf8Bom.length + utf8Content.length];
            System.arraycopy(utf8Bom, 0, utf8Bytes, 0, utf8Bom.length);
            System.arraycopy(utf8Content, 0, utf8Bytes, utf8Bom.length, utf8Content.length);
            Files.write(flagPath, utf8Bytes);
            assertTrue(daemon.pingHeartbeat());

        } finally {
            Files.deleteIfExists(flagPath);
        }
    }
}
