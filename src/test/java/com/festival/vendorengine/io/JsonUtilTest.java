package com.festival.vendorengine.io;

import com.festival.vendorengine.exception.OrderProcessingException;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.PriorityToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void testPrivateConstructor() throws Exception {
        Constructor<JsonUtil> constructor = JsonUtil.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        JsonUtil instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void testParseOrderSuccess() throws OrderProcessingException {
        String json = "{\n" +
                "  \"orderId\": \"ORD-123\",\n" +
                "  \"stallId\": \"S01\",\n" +
                "  \"priority\": \"VIP\",\n" +
                "  \"createdAt\": 1731234567890,\n" +
                "  \"items\": [\n" +
                "    { \"itemName\": \"Biryani\", \"quantity\": 2, \"unitPrice\": 150.0 }\n" +
                "  ]\n" +
                "}";
        Order order = JsonUtil.parseOrder(json);
        assertNotNull(order);
        assertEquals("ORD-123", order.getOrderId());
        assertEquals("S01", order.getStallId());
        assertEquals(PriorityToken.VIP, order.getPriorityToken());
        assertEquals(1731234567890L, order.getCreatedAtMillis());
        assertEquals(1, order.getItems().size());
        assertEquals("Biryani", order.getItems().get(0).getItemName());
        assertEquals(2, order.getItems().get(0).getQuantity());
        assertEquals(150.0, order.getItems().get(0).getUnitPrice(), 1e-9);
    }

    @Test
    void testParseOrderInvalidPriority() {
        String json = "{\n" +
                "  \"orderId\": \"ORD-123\",\n" +
                "  \"stallId\": \"S01\",\n" +
                "  \"priority\": \"SUPER_VIP\",\n" +
                "  \"createdAt\": 1731234567890,\n" +
                "  \"items\": []\n" +
                "}";
        assertThrows(OrderProcessingException.class, () -> JsonUtil.parseOrder(json));
    }

    @Test
    void testParseOrderMissingOrInvalidFields() {
        // Missing orderId
        String missingOrderId = "{ \"stallId\": \"S01\", \"priority\": \"VIP\", \"createdAt\": 1731234567890 }";
        assertThrows(OrderProcessingException.class, () -> JsonUtil.parseOrder(missingOrderId));

        // Invalid stallId type
        String invalidStallId = "{ \"orderId\": \"ORD-1\", \"stallId\": 123, \"priority\": \"VIP\", \"createdAt\": 1731234567890 }";
        assertThrows(OrderProcessingException.class, () -> JsonUtil.parseOrder(invalidStallId));

        // Missing createdAt
        String missingCreatedAt = "{ \"orderId\": \"ORD-1\", \"stallId\": \"S01\", \"priority\": \"VIP\" }";
        assertThrows(OrderProcessingException.class, () -> JsonUtil.parseOrder(missingCreatedAt));

        // Invalid unitPrice type
        String invalidUnitPrice = "{\n" +
                "  \"orderId\": \"ORD-123\",\n" +
                "  \"stallId\": \"S01\",\n" +
                "  \"priority\": \"VIP\",\n" +
                "  \"createdAt\": 1731234567890,\n" +
                "  \"items\": [\n" +
                "    { \"itemName\": \"Biryani\", \"quantity\": 2, \"unitPrice\": \"not-a-number\" }\n" +
                "  ]\n" +
                "}";
        assertThrows(OrderProcessingException.class, () -> JsonUtil.parseOrder(invalidUnitPrice));
    }

    @Test
    void testParseOrderMalformedJson() {
        String malformed = "{ orderId: \"ORD-123\" ";
        assertThrows(OrderProcessingException.class, () -> JsonUtil.parseOrder(malformed));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testParseValuePrimitives() {
        // Booleans and nulls
        int[] pos = {0};
        assertEquals(Boolean.TRUE, JsonUtil.parseValue("true", pos));
        assertEquals(4, pos[0]);

        pos[0] = 0;
        assertEquals(Boolean.FALSE, JsonUtil.parseValue("false", pos));
        assertEquals(5, pos[0]);

        pos[0] = 0;
        assertNull(JsonUtil.parseValue("null", pos));
        assertEquals(4, pos[0]);

        // String
        pos[0] = 0;
        assertEquals("hello", JsonUtil.parseValue("\"hello\"", pos));

        // Empty array
        pos[0] = 0;
        List<Object> emptyArr = (List<Object>) JsonUtil.parseValue("[]", pos);
        assertTrue(emptyArr.isEmpty());

        // Array with primitives
        pos[0] = 0;
        List<Object> arr = (List<Object>) JsonUtil.parseValue("[true, false, null]", pos);
        assertEquals(3, arr.size());
        assertEquals(Boolean.TRUE, arr.get(0));
        assertEquals(Boolean.FALSE, arr.get(1));
        assertNull(arr.get(2));

        // Empty object
        pos[0] = 0;
        Map<String, Object> emptyObj = (Map<String, Object>) JsonUtil.parseValue("{}", pos);
        assertTrue(emptyObj.isEmpty());
    }

    @Test
    void testParseNumberFormats() {
        int[] pos = {0};
        assertEquals(12345L, JsonUtil.parseValue("12345", pos));

        pos[0] = 0;
        assertEquals(-678L, JsonUtil.parseValue("-678", pos));

        pos[0] = 0;
        assertEquals(12.34, JsonUtil.parseValue("12.34", pos));

        pos[0] = 0;
        assertEquals(-0.56, JsonUtil.parseValue("-0.56", pos));

        pos[0] = 0;
        assertEquals(1.2e3, (Double) JsonUtil.parseValue("1.2e3", pos), 1e-9);

        pos[0] = 0;
        assertEquals(5E-2, (Double) JsonUtil.parseValue("5E-2", pos), 1e-9);
    }

    @Test
    void testStringEscapeSequences() {
        int[] pos = {0};
        String escaped = "\"\\\\ \\\" \\/ \\n \\r \\t \\x\"";
        // \x is default case in switch which appends the char itself
        String parsed = JsonUtil.parseString(escaped, pos);
        assertEquals("\\ \" / \n \r \t x", parsed);
    }

    @Test
    void testParserExceptions() {
        // Unexpected EOF
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseValue("", new int[]{0}));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseValue("   ", new int[]{3}));

        // Unterminated String
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseString("\"unclosed string", new int[]{0}));

        // Expected colon error in object
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("{\"key\" 123}", new int[]{0}));

        // Expected brace/bracket/colon mismatch
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("{", new int[]{0}));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("{\"key\"", new int[]{0}));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("{\"key\":", new int[]{0}));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseArray("[", new int[]{0}));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseArray("[1,", new int[]{0}));
    }
}
