package io.oxyjen.tools.test;

import static java.lang.System.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.tools.ToolResult;

class ToolResultTest {
    
    @Test
    void testSuccessCreation() {
        ToolResult result = ToolResult.success("calculator", 42);
        out.println(result);
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals(42, result.getOutput());
        assertEquals("calculator", result.getToolName());
    }
    
    @Test
    void testFailureCreation() {
        ToolResult result = ToolResult.failure("calculator", "Division by zero");
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertEquals("Division by zero", result.getError());
    }
    
    @Test
    void testFailureFromException() {
        Exception ex = new IllegalArgumentException("Bad input");
        ToolResult result = ToolResult.failure("calculator", ex);
        assertTrue(result.isFailure());
        assertTrue(result.getError().contains("IllegalArgumentException"));
        assertTrue(result.getError().contains("Bad input"));
    }
    
    @Test
    void testGetOutputOnFailureThrows() {
        ToolResult result = ToolResult.failure("test", "error");
        assertThrows(IllegalStateException.class, result::getOutput);
    }
    
    @Test
    void testGetErrorOnSuccessThrows() {
        ToolResult result = ToolResult.success("test", "data");
        assertThrows(IllegalStateException.class, result::getError);
    }
    
    @Test
    void testGetOutputAs() {
        ToolResult result = ToolResult.success("test", "string value");
        String output = result.getOutputAs(String.class);
        assertEquals("string value", output);
    }
    
    @Test
    void testGetOutputAsWrongType() {
        ToolResult result = ToolResult.success("test", "string");   
        assertThrows(ClassCastException.class, () ->
            result.getOutputAs(Integer.class)
        );
    }
    
    @Test
    void testGetOutputSafe() {
        ToolResult success = ToolResult.success("test", "data");
        ToolResult failure = ToolResult.failure("test", "error");      
        assertTrue(success.getOutputSafe().isPresent());
        assertFalse(failure.getOutputSafe().isPresent());
    }
    
    @Test
    void testGetOutputSafeTyped() {
        ToolResult result = ToolResult.success("test", "data");
        
        assertTrue(result.getOutputSafe(String.class).isPresent());
        assertFalse(result.getOutputSafe(Integer.class).isPresent());
    }
    
    @Test
    void testGetErrorSafe() {
        ToolResult success = ToolResult.success("test", "data");
        ToolResult failure = ToolResult.failure("test", "error");
        assertFalse(success.getErrorSafe().isPresent());
        assertTrue(failure.getErrorSafe().isPresent());
        assertEquals("error", failure.getErrorSafe().get());
    }
    
    @Test
    void testMetadata() {
        ToolResult result = ToolResult.builder()
            .toolName("test")
            .success(true)
            .output("data")
            .metadata("key1", "value1")
            .metadata("key2", 42)
            .build();
        
        assertEquals("value1", result.getMetadata("key1", String.class).get());
        assertEquals(42, result.getMetadata("key2", Integer.class).get());
    }
    
    @Test
    void testMetadataWrongType() {
        ToolResult result = ToolResult.builder()
            .toolName("test")
            .success(true)
            .output("data")
            .metadata("num", 42)
            .build();
        
        assertFalse(result.getMetadata("num", String.class).isPresent());
    }
    
    @Test
    void testExecutionTime() {
        ToolResult result = ToolResult.builder()
            .toolName("test")
            .success(true)
            .output("data")
            .executionTimeMs(1500)
            .build();
        
        assertEquals(1500, result.getExecutionTimeMs());
    }
    
    @Test
    void testToObservation() {
        ToolResult success = ToolResult.success("calc", 42);
        ToolResult failure = ToolResult.failure("calc", "error");
        
        String successObs = success.toObservation();
        String failureObs = failure.toObservation();
        
        assertTrue(successObs.contains("SUCCESS"));
        assertTrue(successObs.contains("42"));
        
        assertTrue(failureObs.contains("ERROR"));
        assertTrue(failureObs.contains("error"));
    }
    
    @Test
    void testBuilderValidation() {
        // success without output
        assertThrows(IllegalStateException.class, () ->
            ToolResult.builder()
                .toolName("test")
                .success(true)
                .build()
        );
        
        // success with error
        assertThrows(IllegalStateException.class, () ->
            ToolResult.builder()
                .toolName("test")
                .success(true)
                .output("data")
                .error("error")
                .build()
        );
        
        // failure without error
        assertThrows(IllegalStateException.class, () ->
            ToolResult.builder()
                .toolName("test")
                .success(false)
                .build()
        );
        
        // missing tool name
        assertThrows(IllegalStateException.class, () ->
            ToolResult.builder()
                .success(true)
                .output("data")
                .build()
        );
    }
}