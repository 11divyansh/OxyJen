package io.oxyjen.llm.exceptions;

public final class ModelNotFoundException extends LLMException {

    private final String model;

    public ModelNotFoundException(String model, String message) {
        super(message);
        this.model = model;
    }

    public String getModel() {
        return model;
    }
}
