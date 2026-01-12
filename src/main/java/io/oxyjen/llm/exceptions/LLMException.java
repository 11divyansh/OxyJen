package io.oxyjen.llm.exceptions;

/**
 * Base exception for all LLM-related failures in Oxyjen.
 *
 * This represents failures during:
 * - model execution
 * - retries / fallbacks
 * - provider communication
 *
 * NOT for validation or graph errors.
 */
public class LLMException extends RuntimeException {
	
	public LLMException(String message) {
		super(message);
	}

	public LLMException(String message, Throwable cause) {
		super(message,cause);
	}
	
	public LLMException(Throwable cause) {
		super(cause);
	}
	
}
