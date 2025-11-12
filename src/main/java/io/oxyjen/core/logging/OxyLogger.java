package io.oxyjen.core.logging;

import java.time.LocalDateTime;

public class OxyLogger {

    private final String graphName;

    public OxyLogger(String graphName) {
        this.graphName = graphName;
    }

    public void info(String nodeName, String message) {
        log("INFO", nodeName, message);
    }

    public void error(String nodeName, String message, Throwable t) {
        log("ERROR", nodeName, message + " | Exception: " + t.getMessage());
    }

    private void log(String level, String nodeName, String message) {
        System.out.printf("[%s] [%s] [Graph: %s] [Node: %s] %s%n",
                LocalDateTime.now(), level, graphName, nodeName, message);
    }
}
