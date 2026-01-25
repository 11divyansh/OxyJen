package io.oxyjen.llm.internal;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.TimeoutException;

public final class TimedChatModel implements ChatModel {

	private static final ExecutorService SHARED_EXECUTOR = createExecutor();
	
	private final ChatModel delegate;
	private final Duration timeout;
	
	public TimedChatModel(ChatModel delegate, Duration timeout) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate model cannot be null");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        
        this.delegate = delegate;
        this.timeout = timeout;
    }
	
	@Override
	public String chat(String input) {
		
		Future<String> future = SHARED_EXECUTOR.submit(() -> delegate.chat(input));

        try {
            // Wait for result with timeout
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            // Cancel the task (interrupts the thread)
            future.cancel(true);
            
            throw new TimeoutException(
                delegate.getClass().getSimpleName(),
                timeout,
                input.length()
            );
            
        } catch (ExecutionException e) {
            // Unwrap the actual exception from the model
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("LLM call failed", cause);
            
        } catch (InterruptedException e) {
            // Restore interrupt status and throw
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during LLM call", e);
        }
	}
	
	/**
     * Creates a shared executor with daemon threads.
     * CachedThreadPool grows as needed and reuses idle threads.
     */
    private static ExecutorService createExecutor() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("oxyjen-timeout-" + counter.getAndIncrement());
                t.setDaemon(true); // Won't prevent JVM shutdown
                return t;
            }
        });
    }

}
