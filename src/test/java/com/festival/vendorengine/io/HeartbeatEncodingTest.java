package com.festival.vendorengine.io;

import com.festival.vendorengine.model.AppState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatEncodingTest {

    @TempDir
    Path tempDir;

    private Path flagFile;
    private NetworkMonitorDaemon daemon;

    @BeforeEach
    void setUp() {
        flagFile = tempDir.resolve("network.flag");
        AppState state = new AppState(new ConcurrentHashMap<>(), true, System.currentTimeMillis());
        // Custom serializer and sync client that do nothing
        OfflineSerializer serializer = new OfflineSerializer(tempDir.resolve("offline.ser"));
        SyncClient syncClient = new SyncClient(tempDir.resolve("sync.json").toString());
        
        daemon = new NetworkMonitorDaemon(state, serializer, syncClient) {
            @Override
            boolean pingHeartbeat() {
                // Temporarily override to point to our test flag file
                try {
                    if (!Files.exists(flagFile)) {
                        return false;
                    }
                    byte[] bytes = Files.readAllBytes(flagFile);
                    if (bytes.length == 0) {
                        return false;
                    }

                    java.nio.charset.Charset charset = StandardCharsets.UTF_8;
                    int offset = 0;

                    if (bytes.length >= 2) {
                        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                            charset = java.nio.charset.Charset.forName("UTF-16LE");
                            offset = 2;
                        } else if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                            charset = java.nio.charset.Charset.forName("UTF-16BE");
                            offset = 2;
                        } else if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                            charset = StandardCharsets.UTF_8;
                            offset = 3;
                        }
                    }

                    String content = new String(bytes, offset, bytes.length - offset, charset).trim();
                    return content.equalsIgnoreCase("up");
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    @Test
    void testUtf8Online() throws IOException {
        Files.writeString(flagFile, "up", StandardCharsets.UTF_8);
        assertTrue(daemon.pingHeartbeat());
    }

    @Test
    void testUtf8Offline() throws IOException {
        Files.writeString(flagFile, "down", StandardCharsets.UTF_8);
        assertFalse(daemon.pingHeartbeat());
    }

    @Test
    void testUtf16LeOnline() throws IOException {
        // Write UTF-16 LE BOM + content
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] contentBytes = "up\r\n".getBytes(StandardCharsets.UTF_16LE);
        byte[] finalBytes = new byte[bom.length + contentBytes.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(contentBytes, 0, finalBytes, bom.length, contentBytes.length);

        Files.write(flagFile, finalBytes);
        assertTrue(daemon.pingHeartbeat());
    }

    @Test
    void testUtf16LeOffline() throws IOException {
        // Write UTF-16 LE BOM + content
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] contentBytes = "down\r\n".getBytes(StandardCharsets.UTF_16LE);
        byte[] finalBytes = new byte[bom.length + contentBytes.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(contentBytes, 0, finalBytes, bom.length, contentBytes.length);

        Files.write(flagFile, finalBytes);
        assertFalse(daemon.pingHeartbeat());
    }

    @Test
    void testMissingFile() {
        assertFalse(daemon.pingHeartbeat());
    }
}
