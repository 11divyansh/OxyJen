package io.oxyjen.tools.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.safety.AllowListPermission;

class AllowListPermissionTest {
    
    private static class DummyTool implements Tool {
        private final String toolName;
        
        DummyTool(String name) { this.toolName = name; }
        
        @Override public String name() { return toolName; }
        @Override public String description() { return "test"; }
        @Override public JSONSchema inputSchema() { return null; }
        @Override public JSONSchema outputSchema() { return null; }
        @Override public ToolResult execute(Map<String, Object> input, NodeContext ctx) {
            return ToolResult.success(toolName, "ok");
        }
    }
    
    @Test
    void testAllowOnlyMode() {
        AllowListPermission permission = AllowListPermission.allowOnly()
            .allow("calculator")
            .allow("web_search")
            .build();
        
        NodeContext ctx = new NodeContext();
        Tool calc = new DummyTool("calculator");
        Tool search = new DummyTool("web_search");
        Tool blocked = new DummyTool("delete_file");
        
        assertTrue(permission.isAllowed(calc, null, ctx));
        assertTrue(permission.isAllowed(search, null, ctx));
        assertFalse(permission.isAllowed(blocked, null, ctx));
    }
    
    @Test
    void testBlockOnlyMode() {
        AllowListPermission permission = AllowListPermission.blockOnly()
            .block("delete_file")
            .block("shell_exec")
            .build();
        
        NodeContext ctx = new NodeContext();
        Tool calc = new DummyTool("calculator");
        Tool blocked = new DummyTool("delete_file");
        
        assertTrue(permission.isAllowed(calc, null, ctx));
        assertFalse(permission.isAllowed(blocked, null, ctx));
    }
    
    @Test
    void testAllowAll() {
        AllowListPermission permission = AllowListPermission.allowAll();
        
        NodeContext ctx = new NodeContext();
        Tool anyTool = new DummyTool("anything");
        assertTrue(permission.isAllowed(anyTool, null, ctx));
    }
    
    @Test
    void testDenyAll() {
        AllowListPermission permission = AllowListPermission.denyAll();
        
        NodeContext ctx = new NodeContext();
        Tool anyTool = new DummyTool("anything");
        
        assertFalse(permission.isAllowed(anyTool, null, ctx));
    }
    
    @Test
    void testGetReason() {
        AllowListPermission permission = AllowListPermission.allowOnly()
            .allow("calculator")
            .build();
        
        NodeContext ctx = new NodeContext();
        Tool allowed = new DummyTool("calculator");
        Tool blocked = new DummyTool("delete_file");
        
        assertNull(permission.getReason(allowed, null, ctx));
        assertNotNull(permission.getReason(blocked, null, ctx));
        assertTrue(permission.getReason(blocked, null, ctx).contains("allowlist"));
    }
    
    @Test
    void testAllowMethodInBlockOnlyModeThrows() {
        AllowListPermission.Builder builder = AllowListPermission.blockOnly();
        
        assertThrows(IllegalStateException.class, () ->
            builder.allow("test")
        );
    }
    
    @Test
    void testBlockMethodInAllowOnlyModeThrows() {
        AllowListPermission.Builder builder = AllowListPermission.allowOnly();
        
        assertThrows(IllegalStateException.class, () ->
            builder.block("test")
        );
    }
    
    @Test
    void testAddMultiple() {
        AllowListPermission permission = AllowListPermission.allowOnly()
            .addAll("calc", "search", "email")
            .build();
        
        assertEquals(3, permission.getAllowedTools().size());
    }
    
    @Test
    void testCaseNormalization() {
        AllowListPermission permission = AllowListPermission.allowOnly()
            .allow("Calculator")  // Mixed case
            .build();
        
        NodeContext ctx = new NodeContext();
        Tool tool = new DummyTool("calculator");  // lowercase
        
        assertTrue(permission.isAllowed(tool, null, ctx));
    }
    
    @Test
    void testNullToolNameThrows() {
        assertThrows(NullPointerException.class, () ->
            AllowListPermission.allowOnly()
                .allow(null)
                .build()
        );
    }
    
    @Test
    void testEmptyToolNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            AllowListPermission.allowOnly()
                .allow("")
                .build()
        );
    }
}