package io.oxyjen.tools.test;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.tools.ToolCall;
import io.oxyjen.tools.ToolExecutor;
import io.oxyjen.tools.ToolNode;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.builtin.file_read.FileReaderTool;
import io.oxyjen.tools.safety.AllowListPermission;
import io.oxyjen.tools.safety.ToolSandbox;

class FileReaderToolIntegrationTest {
    
    private Path tempDir;
    private ToolSandbox sandbox;
    private FileReaderTool tool;
    private NodeContext context;
    
    private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }
    
    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("integration-test-");
        sandbox = ToolSandbox.builder()
            .allowedDirectory(tempDir.toString())
            .timeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        tool = new FileReaderTool(sandbox);
        context = new NodeContext();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        sandbox.shutdown();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (Exception ignored) {}
                });
        }
    }
    
    @Test
    void testPermissionAllowed() throws Exception {
    	log("Permission: AllowList permits file_read");
        AllowListPermission permission = AllowListPermission.allowOnly()
            .allow("file_read")
            .build();       
        ToolExecutor executor = ToolExecutor.builder()
            .addTool(tool)
            .sandbox(sandbox)
            .permission(permission)
            .build();        
        Path file = tempDir.resolve("allowed.txt");
        Files.writeString(file, "allowed content");       
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", file.toString()
        ));       
        ToolResult result = executor.execute(call, context);       
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertEquals("allowed content", output.get("content"));
    }
    
    @Test
    void testPermissionDenied() throws Exception {
    	log("Permission: AllowList blocks file_read when not allowed");
        AllowListPermission permission = AllowListPermission.allowOnly()
            .allow("calculator") // Only allow calculator
            .build();        
        ToolExecutor executor = ToolExecutor.builder()
            .addTool(tool)
            .sandbox(sandbox)
            .permission(permission)
            .build();        
        Path file = tempDir.resolve("blocked.txt");
        Files.writeString(file, "blocked content");       
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", file.toString()
        ));        
        ToolResult result = executor.execute(call, context);
        out.println(result);
        assertTrue(result.isFailure());
        assertTrue(result.getError().toLowerCase().contains("allowlist") ||
                   result.getError().toLowerCase().contains("denied"));
    }
    
    @Test
    void testBlockOnlyPermission() throws Exception {
    	log("Permission: BlockOnly allows file_read by default");
        AllowListPermission permission = AllowListPermission.blockOnly()
            .block("dangerous_tool")
            .build();        
        ToolExecutor executor = ToolExecutor.builder()
            .addTool(tool)
            .sandbox(sandbox)
            .permission(permission)
            .build();        
        Path file = tempDir.resolve("allowed.txt");
        Files.writeString(file, "content");        
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", file.toString()
        ));        
        ToolResult result = executor.execute(call, context);
        out.println(result.getOutput());
        assertTrue(result.isSuccess());
    }
    
    @Test
    void testValidationMissingPath() {
    	log("Validation: Missing required path fails");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);        
        ToolCall call = ToolCall.of("file_read", Map.of(
            "encoding", "UTF-8" // Missing 'path'
        ));        
        ToolResult result = executor.execute(call, context); 
        out.println(result.getError());
        assertTrue(result.isFailure());
        assertTrue(result.getError().toLowerCase().contains("validation") ||
                   result.getError().toLowerCase().contains("required"));
    }
    
    @Test
    void testValidationWrongTypes() {
    	log("Validation: Invalid parameter types fail");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);       
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", "/tmp/test.txt",
            "offset", "not a number" // Should be number
        ));       
        ToolResult result = executor.execute(call, context);
        out.println(result);
        assertTrue(result.isFailure());
    }
    
    @Test
    void testMetadataTracking() throws Exception {
    	log("Metadata: Execution time is captured");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);        
        Path file = tempDir.resolve("meta.txt");
        Files.writeString(file, "test content");        
        ToolCall call = ToolCall.of("call_123", "file_read", Map.of(
            "path", file.toString()
        ));        
        ToolResult result = executor.execute(call, context);  
        out.println(result.getOutput());
        assertTrue(result.isSuccess());
        assertTrue(result.getExecutionTimeMs() >= 0);      
        // check call ID is preserved
        assertEquals("call_123",
            result.getMetadata("_callId", String.class).orElse(null));
    }
    
    @Test
    void testScenarioChunkedLogAnalysis() throws Exception {
    	log("Scenario: Read log file in chunks for analysis");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);       
        // create large log file
        Path logFile = tempDir.resolve("app.log");
        StringBuilder logs = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            logs.append(String.format("[INFO] %s - Log entry %d\n",
                java.time.Instant.now(), i));
        }
        Files.writeString(logFile, logs.toString());       
        // read first chunk
        ToolCall chunk1Call = ToolCall.of("file_read", Map.of(
            "path", logFile.toString(),
            "chunkSize", 500,
            "chunkIndex", 0
        ));      
        ToolResult chunk1 = executor.execute(chunk1Call, context);
        assertTrue(chunk1.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output1 = (Map<String, Object>) chunk1.getOutput();
        out.println(output1);
        assertTrue((Boolean) output1.get("hasMore"));
        
        // read next chunk
        int nextChunk = ((Number) output1.get("nextChunk")).intValue();
        assertEquals(1, nextChunk);       
        ToolCall chunk2Call = ToolCall.of("file_read", Map.of(
            "path", logFile.toString(),
            "chunkSize", 500,
            "chunkIndex", nextChunk
        ));       
        ToolResult chunk2 = executor.execute(chunk2Call, context);
        out.println(chunk2.getOutput());
        assertTrue(chunk2.isSuccess());
    }
    
    @Test
    void testScenarioConfigExtraction() throws Exception {
    	log("Scenario: Extract specific lines from config file");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);       
        // create config file
        Path configFile = tempDir.resolve("config.ini");
        String config = """
            [database]
            host=localhost
            port=5432
            name=mydb
            
            [cache]
            enabled=true
            ttl=3600
            
            [logging]
            level=INFO
            file=/var/log/app.log
            """;
        Files.writeString(configFile, config);       
        // extract only database section (lines 2-4)
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", configFile.toString(),
            "lineStart", 2,
            "lineEnd", 4
        ));       
        ToolResult result = executor.execute(call, context);
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        String extracted = (String) output.get("content");
        assertTrue(extracted.contains("host=localhost"));
        assertTrue(extracted.contains("port=5432"));
        assertFalse(extracted.contains("[cache]"));
    }
    @Test
    void testScenarioMetadataCheck() throws Exception {
    	log("Scenario: Check file metadata without reading content");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);        
        Path file = tempDir.resolve("data.json");
        String data = "{\"users\": []}";
        Files.writeString(file, data);       
        // Get metadata only (fast check)
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", file.toString(),
            "metadataOnly", true
        ));       
        ToolResult result = executor.execute(call, context);        
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertFalse(output.containsKey("content"));
        assertEquals(data.length(), ((Number) output.get("size")).intValue());
        assertTrue(output.containsKey("lastModified"));
    }
    
    @Test
    void testScenarioCSVPreview() throws Exception {
    	log("Scenario: Read CSV file and limit to first 10 rows");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);        
        // create CSV with 100 rows
        Path csvFile = tempDir.resolve("data.csv");
        StringBuilder csv = new StringBuilder("id,name,value\n");
        for (int i = 1; i <= 100; i++) {
            csv.append(String.format("%d,Item%d,%d\n", i, i, i * 10));
        }
        Files.writeString(csvFile, csv.toString());
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", csvFile.toString(),
            "maxLines", 10
        ));        
        ToolResult result = executor.execute(call, context);        
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertEquals(10L, ((Number) output.get("lines")).longValue());
        String content = (String) output.get("content");
        assertTrue(content.startsWith("id,name,value"));
    }
    
    @Test
    void testScenarioBinaryFileRead() throws Exception {
    	log("Scenario: Binary file read with base64 encoding");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);        
        // Create binary file (e.g., small image)
        Path binaryFile = tempDir.resolve("image.bin");
        byte[] imageData = new byte[100];
        new Random().nextBytes(imageData);
        Files.write(binaryFile, imageData);      
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", binaryFile.toString(),
            "binaryMode", true
        ));       
        ToolResult result = executor.execute(call, context);       
        assertTrue(result.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        String base64 = (String) output.get("content");
        assertNotNull(base64);
        
        // verify we can decode it back
        byte[] decoded = Base64.getDecoder().decode(base64);
        assertEquals(100, decoded.length);
    }
    
    @Test
    void testScenarioLogTail() throws Exception {
    	log("Scenario: Tail of log file (last N lines)");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);       
        // Create log file with 50 lines
        Path logFile = tempDir.resolve("app.log");
        StringBuilder logs = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            logs.append("Log line ").append(i).append("\n");
        }
        Files.writeString(logFile, logs.toString());       
        // Get last 5 lines (lines 46-50)
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", logFile.toString(),
            "lineStart", 46,
            "lineEnd", 50
        ));       
        ToolResult result = executor.execute(call, context);       
        assertTrue(result.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        String content = (String) output.get("content");
        assertTrue(content.contains("Log line 46"));
        assertTrue(content.contains("Log line 50"));
        assertFalse(content.contains("Log line 45"));
    }
    @Test
    void testScenarioDeletedFile() throws Exception {
    	log("Scenario: Graceful handling of deleted file");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);       
        Path file = tempDir.resolve("temporary.txt");
        Files.writeString(file, "temp");
        Files.delete(file);        
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", file.toString()
        ));        
        ToolResult result = executor.execute(call, context); 
        out.println(result);
        assertTrue(result.isFailure());
        assertTrue(result.getError().toLowerCase().contains("not found"));
        assertEquals("file_not_found",
            result.getMetadata("_errorType", String.class).orElse(null));
    }
    
    @Test
    void testScenarioPathTraversalBlocked() throws Exception {
    	log("Scenario: Path traversal attempt blocked");
        ToolExecutor executor = ToolExecutor.of(List.of(tool), sandbox);        
        // try to read outside sandbox using ../
        String maliciousPath = tempDir.toString() + "/../../../etc/passwd";        
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", maliciousPath
        ));        
        ToolResult result = executor.execute(call, context);
        out.println(result.getError());
        assertTrue(result.isFailure());
        assertTrue(result.getError().toLowerCase().contains("denied") ||
                   result.getError().toLowerCase().contains("not allowed"));
    }
    
    @Test
    void testToolNodeIntegration() throws Exception {
    	log("ToolNode: FileReaderTool works in ToolNode");
        ToolNode toolNode = new ToolNode("file-tools", tool);       
        Path file = tempDir.resolve("node-test.txt");
        Files.writeString(file, "ToolNode test");       
        ToolCall call = ToolCall.of("file_read", Map.of(
            "path", file.toString()
        ));        
        ToolResult result = toolNode.process(call, context);
        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertEquals("ToolNode test", output.get("content"));
    }
    
    @Test
    void testToolNodeLifecycle() throws Exception {
    	log("ToolNode: Lifecycle hooks execute");
        ToolNode toolNode = new ToolNode(tool);
        assertDoesNotThrow(() -> toolNode.onStart(context));
        assertDoesNotThrow(() -> toolNode.onFinish(context));
        assertDoesNotThrow(() ->
            toolNode.onError(new Exception("test"), context));
    }
}