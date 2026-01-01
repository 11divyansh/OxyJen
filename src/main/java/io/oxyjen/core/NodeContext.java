package io.oxyjen.core;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


import io.oxyjen.core.exceptions.ExceptionHandler;
import io.oxyjen.core.logging.OxyLogger;

/**
 * Shared execution context for all nodes in an Oxyjen graph.
 * Provides logging, shared data, and metadata during graph execution.
 */
public class NodeContext {

    private final Map<String, Object> data = new HashMap<>();
    private final Logger logger = Logger.getLogger(NodeContext.class.getName());
    private final Map<String, Object> metadata = new HashMap<>();
    
    private OxyLogger oxyjenLogger;
    private ExceptionHandler exceptionHandler;
    
    

    /**
     * Stores a key-value pair in the shared context data.
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieves a value from the shared context data.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * Checks if a key exists in the shared data.
     */
    public boolean contains(String key) {
        return data.containsKey(key);
    }

    /**
     * @return The shared data map (for debugging or inspection).
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * @return The logger used by all nodes.
     */
    public Logger getLogger() {
        return logger;
    }
    
    /**
     * Enhancement for OxyLogger and ExceptionHandler
     */

    /**
     * @return The structured Oxyjen logger used for contextual logs.
     */
    public OxyLogger getOxyjenLogger() {
        if (oxyjenLogger == null) {
        	String graphName = getMetadata("graphName");
            oxyjenLogger = new OxyLogger(
                    graphName != null ? graphName : "default-graph"
            );
        }
        return oxyjenLogger;
    }

    /**
     * Allows setting a custom Oxyjen logger.
     */
    public void setOxyjenLogger(OxyLogger oxyjenLogger) {
        this.oxyjenLogger = oxyjenLogger;
    }

    /**
     * @return The current Oxyjen exception handler (defaults to basic handler).
     */
    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = ExceptionHandler.defaultHandler();
        }
        return exceptionHandler;
    }

    /**
     * Allows customizing the Oxyjen exception handler for this context.
     */
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    /**
     * Sets metadata about current graph or node execution.
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Retrieves metadata.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }
}
