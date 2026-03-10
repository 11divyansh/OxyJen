package io.oxyjen.tools.safety;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.oxyjen.core.NodeContext;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolCall;

/**
 * Simple allowlist-based permission system.
 * Never ship without some form of permission control.
 * 
 * @version 0.4
 */
public final class AllowListPermission implements ToolPermission {
    
    private final Set<String> allowedTools;
    private final boolean defaultAllow;
    
    private AllowListPermission(Set<String> allowedTools, boolean defaultAllow) {
        this.allowedTools = Collections.unmodifiableSet(new HashSet<>(allowedTools));
        this.defaultAllow = defaultAllow;
    }
    
    @Override
    public boolean isAllowed(Tool tool, ToolCall call, NodeContext context) {
    	String toolName = tool.name();
        if (defaultAllow) {
            // Permissive mode: allow unless explicitly blocked
            return !allowedTools.contains(toolName);
        } else {
            // Restrictive mode (default): only allow if explicitly listed
            return allowedTools.contains(toolName);
        }
    }
    
    @Override
    public String getReason(Tool tool, ToolCall call, NodeContext context) {
    	String toolName = tool.name();
        if (isAllowed(tool, call, context)) {
            return null;
        } 
        if (defaultAllow) {
            return String.format("Tool '%s' is explicitly blocked", toolName);
        } else {
            return String.format(
                "Tool '%s' not in allowlist. Allowed tools: %s",
                toolName, allowedTools
            );
        }
    }
    
    public Set<String> getAllowedTools() {
        return allowedTools;
    }
    
    /**
     * Create restrictive allowlist (RECOMMENDED), only allowed tool will execute.
     */
    public static Builder allowOnly() {
        return new Builder(false);
    }
    
    /**
     * Create permissive blocklist.
     * All tools allowed except those explicitly blocked.
     */
    public static Builder blockOnly() {
        return new Builder(true);
    }
    
    public static AllowListPermission allowAll() {
        return new AllowListPermission(Set.of(), true);
    }
    
    public static AllowListPermission denyAll() {
        return new AllowListPermission(Set.of(), false);
    }
    
    public static class Builder {
        private final Set<String> tools = new HashSet<>();
        private final boolean defaultAllow;
        
        private Builder(boolean defaultAllow) {
            this.defaultAllow = defaultAllow;
        }
        
        /**
         * Add a tool to the list.
         * - In allowOnly mode: this tool is ALLOWED
         * - In blockOnly mode: this tool is BLOCKED
         */
        public Builder add(String toolName) {
            tools.add(toolName);
            return this;
        }
        
        /**
         * Convenience: explicitly mark as allowed.
         * works in allowOnly mode.
         */
        public Builder allow(String toolName) {
            if (defaultAllow) {
                throw new IllegalStateException(
                    "Cannot call allow() in blockOnly mode"
                );
            }
            return add(toolName);
        }
        
        /**
         * Convenience: explicitly mark as blocked.
         * works in blockOnly mode
         */
        public Builder block(String toolName) {
            if (!defaultAllow) {
                throw new IllegalStateException(
                    "Cannot call block() in allowOnly mode"
                );
            }
            return add(toolName);
        }
        
        public Builder addAll(String... toolNames) {
            Collections.addAll(tools, toolNames);
            return this;
        }
        
        public AllowListPermission build() {
            return new AllowListPermission(tools, defaultAllow);
        }
    }
}