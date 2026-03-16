package io.oxyjen.tools.builtin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolExecutionException;
import io.oxyjen.tools.ToolResult;

public final class HttpTool implements Tool{

	private final Set<String> allowedDomains;
	private final long timeoutMs;
	private final int maxResponseSize;
	
	public HttpTool(Set<String> allowedDomains, long timeoutMs, int maxResponseSize) {
		this.allowedDomains = Set.copyOf(allowedDomains);
		this.timeoutMs = timeoutMs;
		this.maxResponseSize = maxResponseSize;
	}
	@Override
	public String name() {
		return "http_requesta";
	}

	@Override
	public String description() {
		return """
	            Make HTTP requests (GET, POST, PUT, DELETE).
	            
	            Use for:
	            - Calling REST APIs
	            - Fetching web data
	            - Webhook triggers
	            
	            Returns: status code, headers, body
	            """;
	}

	@Override
	public JSONSchema inputSchema() {
		 return JSONSchema.object()
			        .property("method",
			            PropertySchema.enumOf(
			                "HTTP method",
			                "GET", "POST", "PUT", "DELETE", "PATCH"
			            ).build())
			        .property("url",
			            PropertySchema.string("Full URL to request").build())
			        .property("headers",
			            PropertySchema.map(
			                "Optional HTTP headers",
			                PropertySchema.string("header value").build()
			            ).build())
			        .property("query",
			                PropertySchema.map(
			                    "Query parameters",
			                    PropertySchema.string("parameter value").build()
			            ).build())
			        .property("body",
			            PropertySchema.string("Optional request body").build())
			        .required("method", "url")
			        .build();
	}

	@Override
	public JSONSchema outputSchema() {
		return JSONSchema.object()
		        .property("status",
		            PropertySchema.number("HTTP status code")
		            .minimum(100)
		            .maximum(599).build())
		        .property("headers",
		            PropertySchema.map(
		                "Response headers",
		                PropertySchema.string("header value").build()
		            ).build())
		        .property("body",
		            PropertySchema.string("Response body").build())
		        .required("status")
		        .build();
	}

	@Override
	public ToolResult execute(Map<String, Object> input, NodeContext context) throws ToolExecutionException {
		
		return null;
	}
	@Override
	public long estimateExecutionTime() {
		return timeoutMs;
	}
	    
	@Override
    public long timeoutMs() {
        return timeoutMs;
    }
    
    public static Builder builder() {
        return new Builder();
    }
	    
    public static class Builder {
        private Set<String> allowedDomains = new HashSet<>();
        private long timeoutMs = 30000;
        private int maxResponseSize = 1_000_000;
        
        public Builder allowDomain(String domain) {
            allowedDomains.add(domain);
            return this;
        }
        
        public Builder allowDomains(String... domains) {
            Collections.addAll(allowedDomains, domains);
            return this;
        }
        
        public Builder timeout(long ms) {
            this.timeoutMs = ms;
            return this;
        }
        
        public Builder maxResponseSize(int bytes) {
            this.maxResponseSize = bytes;
            return this;
        }
        
        public HttpTool build() {
            return new HttpTool(allowedDomains, timeoutMs, maxResponseSize);
        }
    }
}