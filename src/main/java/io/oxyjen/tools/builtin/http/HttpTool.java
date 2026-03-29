package io.oxyjen.tools.builtin.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolExecutionException;
import io.oxyjen.tools.ToolResult;

/**
 * HTTP request tool supporting GET, POST, PUT, DELETE, PATCH.
 * 
 * Real-world use cases:
 * - Call REST APIs
 * - Webhook integration
 * - Data fetching
 * - Service communication
 * 
 * Security:
 * - URL allowlist recommended
 * - Timeout enforced
 * - Size limits
 * 
 * @version 0.4
 */
public final class HttpTool implements Tool{

	private final Set<String> allowedDomains;
	private final long timeoutMs;
	private final int maxResponseSize;
	private final HttpClient client;
	
	public HttpTool(Set<String> allowedDomains, long timeoutMs, int maxResponseSize) {
		this.allowedDomains = Set.copyOf(allowedDomains);
		this.timeoutMs = timeoutMs;
		this.maxResponseSize = maxResponseSize;
		this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
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
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null ||
                    (!scheme.equalsIgnoreCase("http")
                            && !scheme.equalsIgnoreCase("https"))) {

                context.getLogger().warning("Blocked non-http scheme: " + scheme);
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            if (!allowedDomains.isEmpty()) {
                boolean allowed = allowedDomains.stream()
                        .anyMatch(domain ->
                                host.equals(domain)
                                        || host.endsWith("." + domain));
                if (!allowed) {
                    context.getLogger().warning(
                            "Domain not allowed: " + host + ". Allowed: " + allowedDomains
                    );
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            context.getLogger().severe("Invalid URL: " + e.getMessage());
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
	@Override
    public ToolResult execute(Map<String, Object> input, NodeContext context)
            throws ToolExecutionException {   
    	long start = System.currentTimeMillis();
        String method = (String) input.get("method");
        String url = (String) input.get("url");
        
        @SuppressWarnings("unchecked")
        Map<String, String> headers = input.containsKey("headers")
            ? (Map<String, String>) input.get("headers")
            : Map.of();
        
        @SuppressWarnings("Unchecked")
        Map<String, String> query = input.containsKey("query")
        	? (Map<String, String>) input.get("query")
        	: Map.of();
        String body = (String) input.get("body");       
        try {
            URI uri = buildUriWithQuery(url, query);           
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeoutMs));
            for (var header : headers.entrySet()) {
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
                case "PATCH" -> requestBuilder.method(
                        "PATCH",
                        body != null
                                ? HttpRequest.BodyPublishers.ofString(body)
                                : HttpRequest.BodyPublishers.noBody()
                );
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            }           
            HttpRequest request = requestBuilder.build();      
            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > maxResponseSize) {
                responseBody = responseBody.substring(0, maxResponseSize) +
                    "\n[TRUNCATED - exceeded " + maxResponseSize + " bytes]";
            }
            int status = response.statusCode();
            boolean isSuccess = status >= 200 && status < 300;

            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((key, values) ->
                    responseHeaders.put(key, String.join(", ", values))
            );

            long end = System.currentTimeMillis();
            if (isSuccess) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", status);
                result.put("headers", responseHeaders);
                result.put("body", responseBody);
                return ToolResult.success(name(), result, end - start);
            }
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("_errorType", "http_error");
            errorBody.put("status", status);
            errorBody.put("message", resolveMessage(status));

            return ToolResult.builder()
                    .toolName(name())
                    .success(false)
                    .error("HTTP " + status + " Error")
                    .executionTimeMs(end - start)
                    .metadata("status", status)
                    .metadata("headers", responseHeaders)
                    .metadata("response", errorBody)
                    .build();
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ToolExecutionException(name(),
                "Request timeout after " + timeoutMs + "ms",
                Map.of("_errorType", "timeout"));
        } catch (java.net.ConnectException e) {
            throw new ToolExecutionException(name(),
                "Connection failed: " + e.getMessage(),
                Map.of("_errorType", "connection"));
        } catch (Exception e) {
            throw new ToolExecutionException(name(),
                "HTTP request failed: " + e.getMessage(),e);
        }
    }
    private String resolveMessage(int status) {
	    return switch (status) {
	        case 400 -> "Bad Request";
	        case 401 -> "Unauthorized";
	        case 403 -> "Forbidden";
	        case 404 -> "Resource not found";
	        case 500 -> "Internal Server Error";
	        default -> "HTTP Error";
	    };
	}
    private URI buildUriWithQuery(String baseUrl, Map<String, String> query)
            throws URISyntaxException {
        if (query.isEmpty()) {
            return new URI(baseUrl);
        }
        StringJoiner joiner = new StringJoiner("&");
        for (var e : query.entrySet()) {
            joiner.add(
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                            + "=" +
                            URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)
            );
        }
        return new URI(baseUrl + "?" + joiner);
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