package io.oxyjen.tools.test;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.builtin.FileReaderTool;
import io.oxyjen.tools.safety.ToolSandbox;

class FileReaderToolTest {
    
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
        tempDir = Files.createTempDirectory("filereader-test-");
        sandbox = ToolSandbox.builder()
            .allowedDirectory(tempDir.toString())
            .timeout(5, java.util.concurrent.TimeUnit.SECONDS)
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
    void testBasicRead() throws Exception {
    	log("Read simple text file");
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, OxyJen!\nThis is a test.");        
        ToolResult result = tool.execute(
            Map.of("path", file.toString()),
            context
        );  
        out.println(result);
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput(); 
        out.println(output);
        long expectedSize = Files.size(file);
        String content = (String) output.get("content");
        assertTrue(content.contains("Hello, OxyJen"));
        assertEquals(expectedSize, ((Number) output.get("size")).longValue());
        assertEquals(2L, ((Number) output.get("lines")).longValue());
    }   
    @Test
    void testUTF8Read() throws Exception {
    	log("Read UTF-8 file with special characters");
        Path file = tempDir.resolve("utf8.txt");
        String content = "Hello 世界! 🎉\nMulti-byte characters: ñ, é, ü";
        Files.writeString(file, content);        
        ToolResult result = tool.execute(
            Map.of("path", file.toString()),
            context
        );
        out.println(result);
        assertTrue(result.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertEquals(content, output.get("content"));
    }
    @Test
    void testCustomEncoding() throws Exception {
    	log("Read file with custom encoding");
        Path file = tempDir.resolve("latin1.txt");
        String text = "café";
        Files.writeString(file, text, java.nio.charset.StandardCharsets.ISO_8859_1);        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "encoding", "ISO-8859-1"
            ),
            context
        );
        out.println(result);
        assertTrue(result.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertEquals(text, output.get("content"));
    }
    @Test
    void testReadWithOffset() throws Exception {
    	log("Read with offset");
        Path file = tempDir.resolve("offset.txt");
        Files.writeString(file, "0123456789ABCDEF");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "offset", 5
            ),
            context
        );
        out.println(result);
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        assertEquals("56789ABCDEF", output.get("content"));
    }
    
    @Test
    void testReadWithOffsetAndLimit() throws Exception {
    	log("Read with offset and limit");
        Path file = tempDir.resolve("slice.txt");
        Files.writeString(file, "0123456789ABCDEF");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "offset", 5,
                "limit", 3
            ),
            context
        );        
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();   
        out.println(output);
        assertEquals("567", output.get("content"));
    }
    @Test
    void testOffsetBeyondSize() throws Exception {
    	log("Offset beyond file size returns empty");
        Path file = tempDir.resolve("small.txt");
        Files.writeString(file, "short");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "offset", 1000
            ),
            context
        );
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput(); 
        out.println(output);
        assertEquals("", output.get("content"));
    }
    
    @Test
    void testLineRange() throws Exception {
    	log("Read specific line range");
        Path file = tempDir.resolve("lines.txt");
        Files.writeString(file, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "lineStart", 2,
                "lineEnd", 4
            ),
            context
        );
        assertTrue(result.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();  
        out.println(output);
        String content = (String) output.get("content");
        assertEquals("Line 2\nLine 3\nLine 4", content);
    }
    @Test
    void testMaxLines() throws Exception {
    	log("Read first N lines with maxLines");
        Path file = tempDir.resolve("many.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        Files.writeString(file, sb.toString());       
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "maxLines", 10
            ),
            context
        );
        assertTrue(result.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput(); 
        out.println(output);
        assertEquals(10L, ((Number) output.get("lines")).longValue());
    }
    
    @Test
    void testLineRangeWithMaxLines() throws Exception {
    	log("Line range with maxLines combines correctly");
        Path file = tempDir.resolve("combo.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        Files.writeString(file, sb.toString());        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "lineStart", 5,
                "lineEnd", 15,
                "maxLines", 5
            ),
            context
        );
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput(); 
        out.println(output);
        //should get lines 5-9 (5 lines from range 5-15)
        assertEquals(5L, ((Number) output.get("lines")).longValue());
    }
    @Test
    void testChunkedReading() throws Exception {
    	log("Read file in chunks");
        Path file = tempDir.resolve("chunks.txt");
        String content = "A".repeat(1000);
        Files.writeString(file, content);
        
        // read first chunk
        ToolResult chunk1 = tool.execute(
            Map.of(
                "path", file.toString(),
                "chunkSize", 300,
                "chunkIndex", 0
            ),
            context
        );        
        assertTrue(chunk1.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output1 = (Map<String, Object>) chunk1.getOutput();
        out.println(output1);
        assertEquals(300, ((String) output1.get("content")).length());
        assertTrue((Boolean) output1.get("hasMore"));
        assertEquals(1L, ((Number) output1.get("nextChunk")).longValue());
        
        // read second chunk
        ToolResult chunk2 = tool.execute(
            Map.of(
                "path", file.toString(),
                "chunkSize", 300,
                "chunkIndex", 1
            ),
            context
        );      
        assertTrue(chunk2.isSuccess());       
        @SuppressWarnings("unchecked")
        Map<String, Object> output2 = (Map<String, Object>) chunk2.getOutput();  
        out.println(output2);
        assertEquals(300, ((String) output2.get("content")).length());
        assertTrue((Boolean) output2.get("hasMore"));
    }
    
    @Test
    void testLastChunk() throws Exception {
    	log("Last chunk indicates no more chunks");
        Path file = tempDir.resolve("small-chunk.txt");
        Files.writeString(file, "12345");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "chunkSize", 10,
                "chunkIndex", 0
            ),
            context
        );
        out.println(result);
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();  
        out.println(output);
        assertFalse((Boolean) output.get("hasMore"));
    }
    @Test
    void testBeyondLastChunk() throws Exception {
    	log("Reading beyond last chunk returns empty with hasMore=false");
        Path file = tempDir.resolve("done.txt");
        Files.writeString(file, "abc");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "chunkSize", 10,
                "chunkIndex", 5 // way beyond
            ),
            context
        );
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();    
        out.println(output);
        assertEquals("", output.get("content"));
        assertFalse((Boolean) output.get("hasMore"));
    }
    
    @Test
    void testMetadataOnly() throws Exception {
    	log("Metadata-only mode returns no content");
        Path file = tempDir.resolve("meta.txt");
        Files.writeString(file, "Some content");        
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "metadataOnly", true
            ),
            context
        );
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput(); 
        out.println(output);
        assertFalse(output.containsKey("content"));
        assertTrue(output.containsKey("size"));
        assertTrue(output.containsKey("lastModified"));
        assertEquals(12L, ((Number) output.get("size")).longValue());
    }
    @Test
    void testBinaryMode() throws Exception {
    	log("Binary mode returns base64");
        Path file = tempDir.resolve("binary.dat");
        byte[] binaryData = {(byte)0xFF, (byte)0xFE, 0x00, 0x01};
        Files.write(file, binaryData);       
        ToolResult result = tool.execute(
            Map.of(
                "path", file.toString(),
                "binaryMode", true
            ),
            context
        );
        out.println(result);
        assertTrue(result.isSuccess());        
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        out.println(output);
        String base64 = (String) output.get("content");
        assertNotNull(base64);       
        // decode and verify
        byte[] decoded = Base64.getDecoder().decode(base64);
        assertArrayEquals(binaryData, decoded);
    }
    
    @Test
    void testFileNotFound() {
    	log("File not found returns failure");
        ToolResult result = tool.execute(
            Map.of("path", tempDir.resolve("nonexistent.txt").toString()),
            context
        );
        out.println(result);
        assertTrue(result.isFailure());
        assertTrue(result.getError().toLowerCase().contains("not found"));
        assertEquals("file_not_found", result.getMetadata("_errorType", String.class).orElse(null));
    }
    @Test
    void testDirectoryPath() {
    	log("Directory path returns failure");
        ToolResult result = tool.execute(
            Map.of("path", tempDir.toString()),
            context
        );
        out.println(result);
        assertTrue(result.isFailure());
        assertTrue(result.getError().toLowerCase().contains("not a regular file"));
    }

}