package com.festival.vendorengine.concurrency;

import com.festival.vendorengine.controller.OrderComparator;
import com.festival.vendorengine.model.Stall;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads stall definitions from {@code stalls.json} and returns a
 * {@link ConcurrentHashMap}{@code <String, Stall>} ready for use by
 * {@code OrderController}.
 *
 * <p>The JSON is parsed manually with a lightweight hand-rolled approach
 * (no external library), consistent with the project's "pure Java, zero
 * extra dependencies" philosophy (Section 3). The format is the simple
 * flat array from Section 12:
 * <pre>
 * {
 *   "stalls": [
 *     { "stallId": "S01", "stallName": "Chaat Corner" },
 *     { "stallId": "S02", "stallName": "Momo Point" }
 *   ]
 * }
 * </pre>
 *
 * <p>File I/O uses {@code BufferedReader} inside a try-with-resources block
 * so the stream is closed correctly even if parsing throws (Section 8.1).
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class MockDataLoader {

    private MockDataLoader() {
        // Utility class — prevent instantiation.
    }

    /**
     * Reads {@code stalls.json} at the given path and returns a
     * {@link ConcurrentHashMap} keyed by stall ID.
     *
     * <p>Each {@link Stall} is constructed with {@link OrderComparator#DEFAULT}
     * already wired into its {@code PriorityBlockingQueue} (via the Stall
     * constructor, which in turn uses {@code OrderComparator.DEFAULT}).
     *
     * @param stallsJsonPath absolute or relative path to {@code stalls.json}
     * @return map of stallId → Stall; never null, may be empty if the file
     *         contains an empty stalls array
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the JSON is structurally malformed
     */
    public static ConcurrentHashMap<String, Stall> loadStalls(Path stallsJsonPath)
            throws IOException {

        ConcurrentHashMap<String, Stall> stallMap = new ConcurrentHashMap<>();

        // try-with-resources: reader is closed regardless of parse success/failure
        try (BufferedReader reader = Files.newBufferedReader(stallsJsonPath)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            String json = sb.toString();

            // Locate the "stalls" array
            int arrayStart = findArrayStart(json, "stalls");
            if (arrayStart < 0) {
                // Empty or malformed file — return empty map gracefully
                return stallMap;
            }
            int arrayEnd = findMatchingBracket(json, arrayStart);
            String arrayContent = json.substring(arrayStart + 1, arrayEnd);

            // Split objects — each stall is a {...} block
            for (String objectStr : splitObjects(arrayContent)) {
                String stallId   = extractStringValue(objectStr, "stallId");
                String stallName = extractStringValue(objectStr, "stallName");
                if (stallId != null && stallName != null) {
                    stallMap.put(stallId, new Stall(stallId, stallName));
                }
            }
        }

        return stallMap;
    }

    // -------------------------------------------------------------------------
    // Private parsing helpers (minimal, sufficient for the known schema)
    // -------------------------------------------------------------------------

    /**
     * Finds the opening {@code [} of a JSON array whose key matches {@code key}.
     * Returns -1 if not found.
     */
    private static int findArrayStart(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) return -1;
        int bracketIndex = json.indexOf('[', keyIndex + needle.length());
        return bracketIndex;
    }

    /**
     * Given the index of an opening bracket ({@code [} or {@code {}), finds the
     * index of the matching closing bracket, respecting nesting.
     */
    private static int findMatchingBracket(String json, int openIndex) {
        char open  = json.charAt(openIndex);
        char close = (open == '[') ? ']' : '}';
        int depth = 0;
        for (int i = openIndex; i < json.length(); i++) {
            if (json.charAt(i) == open)  depth++;
            if (json.charAt(i) == close) depth--;
            if (depth == 0) return i;
        }
        throw new IllegalArgumentException("Unmatched bracket at index " + openIndex);
    }

    /**
     * Splits a comma-delimited sequence of JSON objects ({@code {...}, {...}})
     * into individual object strings, respecting nested braces.
     */
    private static Iterable<String> splitObjects(String content) {
        java.util.List<String> objects = new java.util.ArrayList<>();
        int i = 0;
        while (i < content.length()) {
            int start = content.indexOf('{', i);
            if (start < 0) break;
            int end = findMatchingBracket(content, start);
            objects.add(content.substring(start, end + 1));
            i = end + 1;
        }
        return objects;
    }

    /**
     * Extracts the string value associated with {@code key} from a JSON object
     * fragment. Returns {@code null} if the key is not present.
     */
    private static String extractStringValue(String objectStr, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = objectStr.indexOf(needle);
        if (keyIndex < 0) return null;
        int colonIndex = objectStr.indexOf(':', keyIndex + needle.length());
        if (colonIndex < 0) return null;
        int quoteOpen = objectStr.indexOf('"', colonIndex + 1);
        if (quoteOpen < 0) return null;
        int quoteClose = objectStr.indexOf('"', quoteOpen + 1);
        if (quoteClose < 0) return null;
        return objectStr.substring(quoteOpen + 1, quoteClose);
    }
}
