package io.oxyjen.tools.test;

import static java.lang.System.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.tools.ToolCall;

class ToolCallTest {
    
    @Test
    void testSimpleCreation() {
        ToolCall call = ToolCall.of("calculator", Map.of("a", 5, "b", 3));
        assertEquals("calculator", call.getName());
        assertEquals(5, call.getArgument("a", Integer.class));
        assertEquals(3, call.getArgument("b", Integer.class));
        assertFalse(call.hasId());
    }
    
    @Test
    void testCreationWithId() {
        ToolCall call = ToolCall.of("call_123", "calculator", Map.of("a", 5));
        assertTrue(call.hasId());
        assertEquals("call_123", call.getId());
        assertEquals("calculator", call.getName());
    }
    
    @Test
    void testBuilder() {
        ToolCall call = ToolCall.builder()
            .name("web_search")
            .argument("query", "Java frameworks")
            .argument("limit", 10)
            .build();
        out.println(call);
        assertEquals("web_search", call.getName());
        assertTrue(call.hasArgument("query"));
        assertTrue(call.hasArgument("limit"));
    }
    
    @Test
    void testNullNameThrows() {
        assertThrows(NullPointerException.class, () ->
            ToolCall.of(null, Map.of())
        );
    }
    
    @Test
    void testNullArgumentsCreatesEmptyMap() {
        ToolCall call = ToolCall.of("test", null);
        assertNotNull(call.getArguments());
        assertTrue(call.getArguments().isEmpty());
    }
    
    @Test
    void testGetArgumentWithWrongType() {
        ToolCall call = ToolCall.of("test", Map.of("num", 42));
        assertThrows(ClassCastException.class, () ->
            call.getArgument("num", String.class)
        );
    }
    
    @Test
    void testGetArgumentReturnsNull() {
        ToolCall call = ToolCall.of("test", Map.of("a", 1));
        assertNull(call.getArgument("nonexistent", String.class));
    }
    
    @Test
    void testHasArgument() {
        ToolCall call = ToolCall.of("test", Map.of("key", "value"));
        assertTrue(call.hasArgument("key"));
        assertFalse(call.hasArgument("missing"));
    }
    
    @Test
    void testImmutability() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("a", 1);
        ToolCall call = ToolCall.of("test", args);
        
        args.put("b", 2);
        assertFalse(call.hasArgument("b"));
    }
    
    @Test
    void testEquality() {
        ToolCall call1 = ToolCall.of("calc", Map.of("a", 5));
        ToolCall call2 = ToolCall.of("calc", Map.of("a", 5));
        ToolCall call3 = ToolCall.of("calc", Map.of("a", 6));
        out.println(call1);
        assertEquals(call1, call2);
        assertNotEquals(call1, call3);
    }
    
    @Test
    void testToString() {
        ToolCall call = ToolCall.of("test", Map.of("a", 1, "b", 2));
        String str = call.toString();
        assertTrue(str.contains("test"));
        assertTrue(str.contains("a"));
        assertTrue(str.contains("b"));
    }
}