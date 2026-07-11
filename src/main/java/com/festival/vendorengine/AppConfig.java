package com.festival.vendorengine;

import java.nio.file.Path;

/**
 * Global configuration constants for the Vendor Engine application.
 */
public final class AppConfig {

    private AppConfig() {
        // Prevent instantiation
    }

    /** Path to the stalls configuration file. */
    public static final Path STALLS_JSON_PATH = Path.of("data/stalls.json");

    /** Path to the simulated network heartbeat status file. */
    public static final String NETWORK_FLAG_PATH = "network.flag";

    /** File name of the serialized offline backup. */
    public static final String OFFLINE_SER_PATH = "offline_stalls.ser";

    /** File name of the synchronized order history payload. */
    public static final String SYNC_PAYLOAD_PATH = "sync_payload.json";
}
