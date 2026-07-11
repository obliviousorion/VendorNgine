package com.festival.vendorengine.io;

import com.festival.vendorengine.model.Order;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes a batch of served/synced orders to {@code sync_payload.json} so that
 * a future real-network implementation can POST the file to Django.
 *
 * <h2>Design (Section 8.4)</h2>
 * <p>JSON is built manually with {@link StringBuilder} — no parsing required
 * here, only string-building. Quotes inside string values are escaped with
 * {@code \"} to keep the output valid. In the demo, "syncing with Django" means
 * writing this file; the narrator explains that a production deployment would
 * issue an HTTP POST instead.
 *
 * <h2>File path</h2>
 * <p>Defaults to {@value #DEFAULT_FILE_NAME} in the working directory. A
 * path-aware constructor is provided for testing.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class SyncClient {

    /** Default output file name (Section 12). */
    public static final String DEFAULT_FILE_NAME = "sync_payload.json";

    private final String filePath;

    /**
     * Constructs a client that writes to {@value #DEFAULT_FILE_NAME}.
     */
    public SyncClient() {
        this.filePath = DEFAULT_FILE_NAME;
    }

    /**
     * Constructs a client that writes to the given path string.
     *
     * @param filePath target file path
     */
    public SyncClient(String filePath) {
        this.filePath = filePath;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Writes {@code orders} to the sync file as a JSON object containing a
     * {@code syncedAt} timestamp and an {@code orders} array.
     *
     * <p>Format (Section 12):
     * <pre>
     * {
     *   "syncedAt": 1731234999999,
     *   "orders": [
     *     { "orderId": "...", "stallId": "S01", "status": "SERVED", "total": 120.0 }
     *   ]
     * }
     * </pre>
     *
     * <p>Uses {@code BufferedWriter} + try-with-resources so the file is
     * always closed and flushed. IO errors are logged to {@code System.err}
     * rather than thrown — a sync failure must not crash the application.
     *
     * @param orders the orders to include in the sync payload; may be empty
     */
    public void pushBatch(List<Order> orders) {
        try {
            java.io.File file = new java.io.File(filePath);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        } catch (SecurityException e) {
            // Ignore security exception, let FileWriter handle it
        }
        try (BufferedWriter bw = new BufferedWriter(
                new FileWriter(filePath, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"syncedAt\": ").append(System.currentTimeMillis()).append(",\n");
            sb.append("  \"orders\": [\n");

            for (int i = 0; i < orders.size(); i++) {
                Order o = orders.get(i);
                double total = o.getItems().stream()
                        .mapToDouble(Order.LineItem::getSubtotal)
                        .sum();

                sb.append("    {")
                  .append("\"orderId\": \"").append(escape(o.getOrderId())).append("\", ")
                  .append("\"stallId\": \"").append(escape(o.getStallId())).append("\", ")
                  .append("\"status\": \"").append(o.getStatus().name()).append("\", ")
                  .append("\"total\": ").append(total)
                  .append("}");

                if (i < orders.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("  ]\n");
            sb.append("}\n");

            bw.write(sb.toString());

        } catch (IOException e) {
            // Sync failure must not crash the application — log and continue.
            System.err.println("[SyncClient] Failed to write " + filePath
                    + ": " + e.getMessage());
        }
    }

    /**
     * Returns the configured output file path.
     *
     * @return file path string
     */
    public String getFilePath() {
        return filePath;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Escapes {@code "} and {@code \} characters inside a JSON string value. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
