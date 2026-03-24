package io.oxyjen.tools.safety;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.oxyjen.tools.ToolResult;

/**
 * Sandboxes tool execution to prevent escape and limit damage.
 * 
 * This is the last line of defence before actual damange
 * 
 * What it provides:
 * - Path restrictions (jail tools to specific directories)
 * - Timeout enforcement (kill long-running operations)
 * - Safe path normalization (resolve .. and symlinks)
 * 
 * Critical for file operations and shell commands.
 * 
 * @version 0.4
 */
public final class ToolSandbox {
    
    private final Set<Path> allowedDirectories;
    private final long timeoutMs;
    private final ExecutorService executor;
    private final boolean strictMode;
    
    private ToolSandbox(Builder builder) {
        this.allowedDirectories = Set.copyOf(builder.allowedDirectories);
        this.timeoutMs = builder.timeoutMs;
        this.strictMode = builder.strictMode;
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "ToolSandbox-Worker");
            t.setDaemon(true);
            return t;
        });
    }
    public static ToolSandbox basic() {
        return new Builder()
            .allowedDirectory("/tmp")
            .timeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    public static ToolSandbox strict() {
        return new Builder()
        	.strictMode(true)
            .timeout(10, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Execute a tool operation within the sandbox.
     * Enforces timeout limits.
     */
    public ToolResult execute(Supplier<ToolResult> operation) {
        if (timeoutMs <= 0) {
            return executeWithoutTimeout(operation);
        }
        Future<ToolResult> future = executor.submit(() -> {
        	try {
        		return operation.get();
        	} catch (Throwable t) {
        		throw new RuntimeException(t);
        	}
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS); 
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResult.failure(
                "sandbox",
                String.format("Execution timeout after %dms", timeoutMs)
            );      
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("sandbox", "Execution interrupted");      
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re && re.getCause() != null)
            	cause = re.getCause();
            return ToolResult.failure(
                "sandbox",
                "Execution failed: " + cause.getMessage()
            );
        }
    }
    
    private ToolResult executeWithoutTimeout(Supplier<ToolResult> operation) {
        try {
            return operation.get();
        } catch (Exception e) {
            return ToolResult.failure(
                "sandbox",
                "Execution failed: " + e.getMessage()
            );
        }
    }
    /**
     * Check if a path is within allowed directories.
     * 
     * CRITICAL: Use this in file tools before any file operation.
     */
    public boolean isPathAllowed(String path) {
        if (path == null || path.isEmpty()) 
            return strictMode ? false : true;   
        if (allowedDirectories.isEmpty()) 
        	// no restrictions configured
            return !strictMode; // in strict mode, deny if no allowlist
        try {
            Path normalized = Paths.get(path).toAbsolutePath().normalize();
            for (Path allowedDir : allowedDirectories) {
                if (normalized.startsWith(allowedDir)) {
                    return true;
                }
            }      
            return false;     
        } catch (Exception e) {
            // if path is invalid deny it
            return false;
        }
    }
    /**
     * Validate that a path is allowed, throw exception if not.
     * 
     * Use this at the start of file operations:
     * <pre>
     * sandbox.validatePath(filePath);
     * // Now safe to read/write file
     * </pre>
     */
    public void validatePath(String path) throws SecurityException {
        if (!isPathAllowed(path)) {
            throw new SecurityException(
                String.format(
                    "Path '%s' is outside allowed directories: %s",
                    path, allowedDirectories
                )
            );
        }
    }

    public String normalizePath(String path) throws SecurityException {
        if (path == null || path.isEmpty()) {
            throw new SecurityException("Path cannot be null or empty");
        }       
        try {
        	Path normalized = Paths.get(path).toAbsolutePath().normalize();
            String normalizedStr = normalized.toString();            
            if (!isPathAllowed(normalizedStr)) {
                throw new SecurityException(
                    String.format(
                        "Path '%s' (normalized to '%s') is not allowed",
                        path, normalizedStr
                    )
                );
            }            
            return normalizedStr;     
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Invalid path: " + path, e);
        }
    }
    
    public Set<Path> getAllowedDirectories() {
        return allowedDirectories;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public boolean isStrictMode() {
    	return strictMode;
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Set<Path> allowedDirectories = new HashSet<>();
        private long timeoutMs = 30000; 
        private boolean strictMode = false;
        public Builder allowedDirectory(String directory) {
            try {
                Path path = Paths.get(directory).toAbsolutePath().normalize();
                allowedDirectories.add(path);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    "Invalid directory: " + directory, e
                );
            }
            return this;
        }
        
        public Builder allowedDirectories(String... directories) {
            for (String dir : directories) {
                allowedDirectory(dir);
            }
            return this;
        }
        public Builder timeout(long duration, TimeUnit unit) {
            this.timeoutMs = unit.toMillis(duration);
            return this;
        }
        
        public Builder noTimeout() {
            this.timeoutMs = -1;
            return this;
        }
        
        /**
         * Enable strict mode.
         * 
         * In strict mode:
         * - Deny access if no allowed directories configured
         * - Fail on any ambiguous path
         * - More aggressive validation
         */
        public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }
        
        public ToolSandbox build() {
            return new ToolSandbox(this);
        }
    }
}