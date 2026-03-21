package io.oxyjen.tools.builtin;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolExecutionException;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.safety.ToolSandbox;

/**
 * File reading tool with built-in safety.
 * 
 * Real-world use cases:
 * - Read configuration files
 * - Process data files
 * - Load documents
 * - Access logs
 * 
 * Safety:
 * - Path validation via sandbox
 * - Size limits
 * - Encoding detection
 * - Read-only (never writes)
 * 
 * @version 0.4
 */
public final class FileReaderTool implements Tool {
    
    private final ToolSandbox sandbox;
    private final long maxFileSize;
    
    private final Map<String, CachedFile> cache = new HashMap<>();
    
    public FileReaderTool(ToolSandbox sandbox) {
        this(sandbox, 10_000_000); // 10MB default
    }
    
    public FileReaderTool(ToolSandbox sandbox, long maxFileSize) {
        this.sandbox = Objects.requireNonNull(sandbox, "Sandbox cannot be null");
        this.maxFileSize = maxFileSize;
    }
    
    @Override
    public String name() {
        return "file_read";
    }
    
    @Override
    public String description() {
        return """
            Read contents of a text file with support for partial reads, chunking, and metadata.
            
            Use for:
            - Partial reading (offset, limit, line range)
            - Chunked reading for large files
            - Metadata-only mode
            - Binary-safe mode (base64)
            - Loading data files
            - Accessing documents
            - Reading logs
            
            Returns file content as string.
            Max size: """ + (maxFileSize / 1_000_000) + "MB";
    }
    
    @Override
    public JSONSchema inputSchema() {
        return JSONSchema.object()
            .property("path",
                PropertySchema.string("File path to read")
                .build())
            .property("encoding",
                PropertySchema.string("Text encoding (default: UTF-8)")
                .build())
            .property("offset", PropertySchema.number("Start byte offset").minimum(0).build())
            .property("limit", PropertySchema.number("Max bytes to read").minimum(1).build())
            .property("lineStart", PropertySchema.number("Start line number").minimum(1).build())
            .property("lineEnd", PropertySchema.number("End line number").minimum(1).build())
            .property("chunkSize", PropertySchema.number("Chunk size in bytes").minimum(1).build())
            .property("chunkIndex", PropertySchema.number("Chunk index (0-based)").minimum(0).build())
            .property("metadataOnly", PropertySchema.bool("Return only metadata").build())
            .property("binaryMode", PropertySchema.bool("Return base64 if binary").build())
            .property("maxLines", PropertySchema.number("Max lines to read").minimum(1).build())         
            .required("path")
            .build();
    }
    
    @Override
    public JSONSchema outputSchema() {
        return JSONSchema.object()
            .property("content",
                PropertySchema.string("File contents").build())
            .property("size",
                PropertySchema.number("File size in bytes").build())
            .property("lines",
                PropertySchema.number("Number of lines").build())
            .property("mime", PropertySchema.string("Detected MIME type").build())
            .property("lastModified", PropertySchema.number("Last modified timestamp").build())
            .property("hasMore", PropertySchema.bool("More chunks available").build())
            .property("nextChunk", PropertySchema.number("Next chunk index").build())
            .required("size")
            .build();
    }

    @Override
    public boolean isSafe(Map<String, Object> input, NodeContext context) {
        String path = (String) input.get("path");        
        return path != null && !path.isEmpty() && path.length() < 4096;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> input, NodeContext context)
            throws ToolExecutionException {
        long start = System.currentTimeMillis();
        String pathStr = (String) input.get("path");
        String encoding = input.containsKey("encoding")
            ? (String) input.get("encoding")
            : "UTF-8";
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (Exception e) {
            return ToolResult.failure(name(), "Unsupported encoding: " + encoding);
        }
        Long offset = getLong(input, "offset");
        Long limit = getLong(input, "limit");
        Long lineStart = getLong(input, "lineStart");
        Long lineEnd = getLong(input, "lineEnd");
        Long chunkSize = getLong(input, "chunkSize");
        Long chunkIndex = getLong(input, "chunkIndex");
        Long maxLines = getLong(input, "maxLines");
        
        boolean metadataOnly = Boolean.TRUE.equals(input.get("metadataOnly"));
        boolean binaryMode = Boolean.TRUE.equals(input.get("binaryMode"));
        try {
            String normalizedPath = sandbox.normalizePath(pathStr);
            Path path = Paths.get(normalizedPath);
            if (!Files.exists(path)) {
            	return ToolResult.builder()
            			.success(false)
            			.toolName(name())
            			.error("File not found: " + pathStr)
            			.metadata(Map.of("_errorType", "file_not_found", "path", pathStr))
            			.executionTimeMs(elapsed(start))
            			.build();
            }
            if (!Files.isRegularFile(path)) {
                return ToolResult.builder()
                		.success(false)
                		.toolName(name())
                		.error("Path is not a regular file: " + pathStr)
                		.metadata(Map.of("_errorType", "not_regular_file"))
                		.executionTimeMs(elapsed(start))
                		.build();
            }
            long size = Files.size(path);
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            String mime = Files.probeContentType(path);
            if (mime == null) mime = "application/octet-stream";
            if (metadataOnly) {
                return ToolResult.success(name(), Map.of(
                    "size", size,
                    "lastModified", lastModified,
                    "mime", mime
                ), elapsed(start));
            }
            String cacheKey = normalizedPath + ":" + lastModified;
            CachedFile cached = cache.get(cacheKey);           
            byte[] bytes;
            if (cached != null) {
                bytes = cached.data;
            } else {
                if (size > maxFileSize) {
                    throw new ToolExecutionException(name(), "File too large");
                }
                bytes = Files.readAllBytes(path);
                cache.put(cacheKey, new CachedFile(bytes));
            }
            if (chunkSize != null) {
                long idx = chunkIndex != null ? chunkIndex : 0;
                long startByte = idx * chunkSize;
                long endByte = Math.min(startByte + chunkSize, bytes.length);               
                if (startByte >= bytes.length) {
                    return ToolResult.success(name(), Map.of(
                        "content", "",
                        "hasMore", false
                    ), elapsed(start));
                }      
                byte[] slice = Arrays.copyOfRange(bytes, (int) startByte, (int) endByte); 
                Map<String, Object> out = new HashMap<>();
                out.put("content", new String(slice, charset));
                out.put("hasMore", endByte < bytes.length);
                out.put("nextChunk", idx + 1);
                out.put("mime", mime);
                out.put("size", size);
                return ToolResult.success(name(), out, elapsed(start));
            }
            int startByte = offset != null ? Math.min(offset.intValue(), bytes.length) : 0;
            int endByte = limit != null
                ? Math.min(startByte + limit.intValue(), bytes.length)
                : bytes.length;            
            byte[] slice = Arrays.copyOfRange(bytes, startByte, endByte);
            
            String content;
            if (binaryMode) {
                content = Base64.getEncoder().encodeToString(slice);
            } else {
                content = new String(slice, charset);
            }
            if (lineStart != null || lineEnd != null || maxLines != null) {
                List<String> lines = content.lines().toList();               
                int from = lineStart != null ? lineStart.intValue() - 1 : 0;
                int to = lineEnd != null ? Math.min(lineEnd.intValue(), lines.size()) : lines.size();  
                from = Math.max(0, Math.min(from, lines.size()));
                to = Math.max(from, Math.min(to, lines.size()));
                List<String> sub = lines.subList(from, to);                
                if (maxLines != null && sub.size() > maxLines) {
                    sub = sub.subList(0, maxLines.intValue());
                }                
                content = String.join("\n", sub);
            }           
            long lineCount = content.lines().count();
            Map<String, Object> out = new HashMap<>();
            out.put("content", content);
            out.put("size", size);
            out.put("lines", lineCount);
            out.put("mime", mime);
            out.put("lastModified", lastModified);
            return ToolResult.success(name(), out, elapsed(start));
        } catch (SecurityException e) {
        	return ToolResult.builder()
        			.toolName(name())
        			.success(false)
        			.error("Access denied: " + e.getMessage())
        			.executionTimeMs(elapsed(start)).build();
        } catch (java.nio.charset.UnsupportedCharsetException e) {
            throw new ToolExecutionException(name(),
                "Unsupported encoding: " + encoding);
        } catch (java.io.IOException e) {
            throw new ToolExecutionException(name(),
                "Failed to read file: " + e.getMessage(), e);
        } catch (ToolExecutionException e) {
        	return ToolResult.failure(name(), e);
        } catch (Exception e) {
            throw new ToolExecutionException(name(),
                "Unexpected error: " + e.getMessage(), e);
        }
    }
    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
    
    private static Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).longValue() : null;
    }
    
    /**
     * Cache holder
     */
    private static class CachedFile {
        final byte[] data;
        CachedFile(byte[] data) {
            this.data = data;
        }
    }
    @Override
    public long estimateExecutionTime() {
        return 1000; // ~1 second for typical files
    }
}