package com.festival.vendorengine.io;

import com.festival.vendorengine.exception.OrderProcessingException;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.OrderStatus;
import com.festival.vendorengine.model.PriorityToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled, zero-dependency recursive-descent JSON parser, purpose-built
 * for the two schemas this application needs to handle:
 * <ol>
 *   <li>Incoming order payloads (parsed by {@link #parseOrder}).</li>
 *   <li>{@code stalls.json} (parsed by {@code MockDataLoader} using the same
 *       lower-level primitives).</li>
 * </ol>
 *
 * <p>A full-featured JSON library (Jackson, org.json) would be a single extra
 * Maven dependency, but the architecture spec (Section 3 / Section 8.1) requires
 * a "pure Java, zero extra dependencies" design so that the parser counts as a
 * demonstrable custom algorithm in the final report. The trade-off is explicitly
 * acknowledged: a production system would use a battle-tested library.
 *
 * <p>The parser uses a single-element {@code int[]} as a mutable cursor passed
 * by reference through the recursive calls — idiomatic for a recursive-descent
 * parser without needing to heap-allocate a wrapper object.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public final class JsonUtil {

    private JsonUtil() {
        // Utility class — prevent instantiation.
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses a JSON string into an {@link Order} object.
     *
     * <p>Expected schema (Section 12):
     * <pre>
     * {
     *   "orderId"  : "&lt;uuid&gt;",
     *   "stallId"  : "S01",
     *   "priority" : "STANDARD" | "VIP",
     *   "createdAt": 1731234567890,
     *   "items"    : [ { "itemName": "...", "quantity": 2, "unitPrice": 60.0 } ]
     * }
     * </pre>
     *
     * @param raw the raw JSON string from the blocking queue
     * @return a parsed {@link Order} ready for routing
     * @throws OrderProcessingException if the JSON is malformed or a required
     *         field is missing/invalid — wraps the original cause for diagnosis
     */
    public static Order parseOrder(String raw) throws OrderProcessingException {
        try {
            int[] pos = {0};
            skipWhitespace(raw, pos);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parseValue(raw, pos);

            String orderId    = requireString(map, "orderId",  raw);
            String stallId    = requireString(map, "stallId",  raw);
            String priorityStr= requireString(map, "priority", raw);
            long createdAt    = requireLong(map,   "createdAt", raw);

            PriorityToken priority;
            try {
                priority = PriorityToken.valueOf(priorityStr);
            } catch (IllegalArgumentException e) {
                throw new OrderProcessingException(
                        "Unknown priority token '" + priorityStr + "' in: " + raw, e);
            }

            Object rawItems = map.get("items");
            List<Order.LineItem> lineItems = new ArrayList<>();
            if (rawItems instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> itemList = (List<Object>) rawItems;
                for (Object itemObj : itemList) {
                    if (itemObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) itemObj;
                        String itemName = requireString(itemMap, "itemName", raw);
                        int    quantity = (int) requireLong(itemMap, "quantity", raw);
                        double unitPrice= requireDouble(itemMap, "unitPrice", raw);
                        lineItems.add(new Order.LineItem(itemName, quantity, unitPrice));
                    }
                }
            }

            Order order = new Order(orderId, stallId, lineItems, priority, createdAt);
            // Default status is PENDING, set by Order constructor — no mutation needed here.
            return order;

        } catch (OrderProcessingException e) {
            throw e; // re-throw without wrapping again
        } catch (Exception e) {
            throw new OrderProcessingException("Malformed order JSON: " + raw, e);
        }
    }

    // -------------------------------------------------------------------------
    // Recursive-descent parser core
    // -------------------------------------------------------------------------

    /**
     * Dispatches to the appropriate parse routine based on the next character.
     * Advances {@code pos[0]} past the parsed value.
     */
    static Object parseValue(String json, int[] pos) {
        skipWhitespace(json, pos);
        if (pos[0] >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        char ch = json.charAt(pos[0]);
        if (ch == '{') return parseObject(json, pos);
        if (ch == '[') return parseArray(json, pos);
        if (ch == '"') return parseString(json, pos);
        if (ch == 't') { pos[0] += 4; return Boolean.TRUE; }  // true
        if (ch == 'f') { pos[0] += 5; return Boolean.FALSE; } // false
        if (ch == 'n') { pos[0] += 4; return null; }          // null
        return parseNumber(json, pos);
    }

    /**
     * Parses a JSON object ({@code { "key": value, ... }}) into a {@link Map}.
     */
    static Map<String, Object> parseObject(String json, int[] pos) {
        expect(json, pos, '{');
        Map<String, Object> map = new HashMap<>();
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') {
            pos[0]++;
            return map;
        }
        while (true) {
            skipWhitespace(json, pos);
            String key = parseString(json, pos);
            skipWhitespace(json, pos);
            expect(json, pos, ':');
            skipWhitespace(json, pos);
            Object value = parseValue(json, pos);
            map.put(key, value);
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char next = json.charAt(pos[0]);
            if (next == '}') { pos[0]++; break; }
            if (next == ',') { pos[0]++; } // continue to next key-value pair
        }
        return map;
    }

    /**
     * Parses a JSON array ({@code [ value, ... ]}) into a {@link List}.
     */
    static List<Object> parseArray(String json, int[] pos) {
        expect(json, pos, '[');
        List<Object> list = new ArrayList<>();
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == ']') {
            pos[0]++;
            return list;
        }
        while (true) {
            skipWhitespace(json, pos);
            list.add(parseValue(json, pos));
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char next = json.charAt(pos[0]);
            if (next == ']') { pos[0]++; break; }
            if (next == ',') { pos[0]++; }
        }
        return list;
    }

    /**
     * Parses a JSON string, handling basic {@code \"} escape sequences.
     */
    static String parseString(String json, int[] pos) {
        expect(json, pos, '"');
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char ch = json.charAt(pos[0]++);
            if (ch == '"') return sb.toString();
            if (ch == '\\' && pos[0] < json.length()) {
                char esc = json.charAt(pos[0]++);
                switch (esc) {
                    case '"' : sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/' : sb.append('/');  break;
                    case 'n' : sb.append('\n'); break;
                    case 'r' : sb.append('\r'); break;
                    case 't' : sb.append('\t'); break;
                    default  : sb.append(esc);
                }
            } else {
                sb.append(ch);
            }
        }
        throw new IllegalArgumentException("Unterminated string at pos " + pos[0]);
    }

    /**
     * Parses a JSON number, returning either a {@link Long} (if no decimal point)
     * or a {@link Double}.
     */
    static Number parseNumber(String json, int[] pos) {
        int start = pos[0];
        if (pos[0] < json.length() && json.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < json.length() && Character.isDigit(json.charAt(pos[0]))) pos[0]++;
        boolean isDecimal = false;
        if (pos[0] < json.length() && json.charAt(pos[0]) == '.') {
            isDecimal = true;
            pos[0]++;
            while (pos[0] < json.length() && Character.isDigit(json.charAt(pos[0]))) pos[0]++;
        }
        // Exponent (e/E)
        if (pos[0] < json.length() && (json.charAt(pos[0]) == 'e' || json.charAt(pos[0]) == 'E')) {
            isDecimal = true;
            pos[0]++;
            if (pos[0] < json.length() && (json.charAt(pos[0]) == '+' || json.charAt(pos[0]) == '-')) pos[0]++;
            while (pos[0] < json.length() && Character.isDigit(json.charAt(pos[0]))) pos[0]++;
        }
        String numStr = json.substring(start, pos[0]);
        if (isDecimal) return Double.parseDouble(numStr);
        return Long.parseLong(numStr);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length() && Character.isWhitespace(json.charAt(pos[0]))) pos[0]++;
    }

    private static void expect(String json, int[] pos, char expected) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != expected) {
            throw new IllegalArgumentException(
                    "Expected '" + expected + "' at pos " + pos[0]
                    + " but got '" + (pos[0] < json.length() ? json.charAt(pos[0]) : "EOF") + "'");
        }
        pos[0]++;
    }

    private static String requireString(Map<String, Object> map, String key, String raw) {
        Object val = map.get(key);
        if (!(val instanceof String)) {
            throw new IllegalArgumentException("Missing/invalid string field '" + key + "' in: " + raw);
        }
        return (String) val;
    }

    private static long requireLong(Map<String, Object> map, String key, String raw) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        throw new IllegalArgumentException("Missing/invalid numeric field '" + key + "' in: " + raw);
    }

    private static double requireDouble(Map<String, Object> map, String key, String raw) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        throw new IllegalArgumentException("Missing/invalid numeric field '" + key + "' in: " + raw);
    }
}
