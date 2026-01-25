package io.oxyjen.llm.exceptions;

import java.time.Duration;

/**
 * Thrown when an LLM call exceeds its execution timeout.
 * retryable(transient)
 * may need different timeout or fallback
 */
public final class TimeoutException extends LLMException {
	
	private final Duration timeout;
	private final String model;
	private final int inputLength;
	
    public TimeoutException(String model, Duration timeout, int inputLength) {
        super("LLM["+model+"] call exceeded timeout of " + timeout.toSeconds() + "s");
        this.model=model;
        this.timeout=timeout;
        this.inputLength=inputLength;
    }
    
    public Duration timeout() {
    	return timeout;
    }
    
    public String model() {
    	return model;
    }
    
    public int inputLength() {
    	return inputLength;
    }
}
