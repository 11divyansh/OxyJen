package io.oxyjen.llm.prompts;


/**
 * Thrown when template rendering fails.
 */
public class TemplateException extends RuntimeException {
    
	private final String template;
	private final String variable;


	public TemplateException(String message) {
		super(message);
		this.template = null;
		this.variable = null;
	}

	public TemplateException(String message, String template) {
		super(message);
		this.template = template;
		this.variable = null;
	}

	public TemplateException(String message, String template, String variable) {
		super(message);
		this.template = template;
		this.variable = variable;
	}

	public String getTemplate() {
	return template;
	}

	public String getVariable() {
		return variable;
	}
}