package io.oxyjen.tools;

public class ToolValidationException extends RuntimeException {

	public ToolValidationException(String message) {
		super(message);
	}
	public ToolValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
