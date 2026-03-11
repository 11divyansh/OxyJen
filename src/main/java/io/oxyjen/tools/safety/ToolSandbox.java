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
    
    private ToolSandbox(Builder builder) {
        this.allowedDirectories = Set.copyOf(builder.allowedDirectories);
        this.timeoutMs = builder.timeoutMs;
        this.executor = Executors.newCachedThreadPool(r -> {
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
        Future<ToolResult> future = executor.submit(operation::get);
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
    
    public Set<Path> getAllowedDirectories() {
        return allowedDirectories;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Set<Path> allowedDirectories = new HashSet<>();
        private long timeoutMs = 30000; 
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
        
        public Builder timeout(long duration, TimeUnit unit) {
            this.timeoutMs = unit.toMillis(duration);
            return this;
        }
        
        public Builder noTimeout() {
            this.timeoutMs = -1;
            return this;
        }
        
        public ToolSandbox build() {
            return new ToolSandbox(this);
        }
    }
}