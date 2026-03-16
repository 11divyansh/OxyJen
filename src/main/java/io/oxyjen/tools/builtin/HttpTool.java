package io.oxyjen.tools.builtin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
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
		return "http_request";
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
			                PropertySchema.string("header value")
			            ).build())
			        .property("query",
			                PropertySchema.map(
			                    "Query parameters",
			                    PropertySchema.string("parameter value")
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
		                PropertySchema.string("header value")
		            ).build())
		        .property("body",
		            PropertySchema.string("Response body").build())
		        .required("status")
		        .build();
	}

	@Override
    public boolean isSafe(Map<String, Object> input, NodeContext context) {
        String url = (String) input.get("url");       
        if (url == null || url.isEmpty()) {
            return false;
        }
        if (!allowedDomains.isEmpty()) {
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                
                boolean allowed = allowedDomains.stream()
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));               
                if (!allowed) {
                    context.getLogger().warning(
                        "Domain not allowed: " + host + ". Allowed: " + allowedDomains
                    );
                    return false;
                }
            } catch (Exception e) {
                context.getLogger().severe("Invalid URL: " + e.getMessage());
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> input, NodeContext context)
            throws ToolExecutionException {        
        String method = (String) input.get("method");
        String url = (String) input.get("url");
        
        @SuppressWarnings("unchecked")
        Map<String, String> headers = input.containsKey("headers")
            ? (Map<String, String>) input.get("headers")
            : Map.of();
        
        String body = (String) input.get("body");       
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();           
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(new URI(url))
                .timeout(Duration.ofMillis(timeoutMs));
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(
                    body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody()
                );
                case "PUT" -> requestBuilder.PUT(
                    body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody()
                );
                case "DELETE" -> requestBuilder.DELETE();
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            }           
            HttpRequest request = requestBuilder.build();      
            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            String responseBody = response.body();
            if (responseBody.length() > maxResponseSize) {
                responseBody = responseBody.substring(0, maxResponseSize) +
                    "\n[TRUNCATED - exceeded " + maxResponseSize + " bytes]";
            }
            Map<String, Object> result = new HashMap<>();
            result.put("status", response.statusCode());
            
            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((key, values) ->
                responseHeaders.put(key, String.join(", ", values))
            );
            result.put("headers", responseHeaders);
            result.put("body", responseBody);   
            return ToolResult.success(name(), result);  
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ToolExecutionException(name(),
                "Request timeout after " + timeoutMs + "ms");
        } catch (java.net.ConnectException e) {
            throw new ToolExecutionException(name(),
                "Connection failed: " + e.getMessage());
        } catch (Exception e) {
            throw new ToolExecutionException(name(),
                "HTTP request failed: " + e.getMessage(), e);
        }
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