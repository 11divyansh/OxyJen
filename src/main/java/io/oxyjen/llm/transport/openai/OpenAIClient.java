package io.oxyjen.llm.transport.openai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.oxyjen.llm.exceptions.InvalidAPIKeyException;
import io.oxyjen.llm.exceptions.LLMException;
import io.oxyjen.llm.exceptions.ModelNotFoundException;
import io.oxyjen.llm.exceptions.NetworkException;
import io.oxyjen.llm.exceptions.RateLimitException;
import io.oxyjen.llm.exceptions.TokenLimitExceededException;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;
import io.oxyjen.llm.semantics.Message;

/**
 * Low-level HTTP client for OpenAI API.
 * 
 * This is LAYER 1 - Transport. It knows NOTHING about:
 * - ChatModel interface
 * - Graphs
 * - Memory
 * - Nodes
 * 
 * It only knows how to:
 * - Make HTTP POST requests
 * - Handle auth
 * - Parse JSON responses
 * - Classify errors
 * 
 * This is intentionally kept dumb just pure transport logic.
 */
public final class OpenAIClient {
    
    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_ENDPOINT = BASE_URL + "/chat/completions";
    
    private final String apiKey;
    private final HttpClient httpClient;
    
    /**
     * Create client with API key.
     * 
     * @param apiKey OpenAI API key (starts with sk-)
     */
    public OpenAIClient(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Send a chat completion request.
     * 
     * @param request The chat request
     * @return The chat response
     * @throws RuntimeException if request fails
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            HttpRequest httpRequest = buildHttpRequest(request);
            HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                throw classifyError(response, request.model());
            }
            return parseResponse(response.body());    
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI request failed", e);
        }
    }
    
    private HttpRequest buildHttpRequest(ChatRequest request) {
        // Build JSON body
        String json = buildJsonBody(request);
        
        return HttpRequest.newBuilder()
            .uri(URI.create(CHAT_ENDPOINT))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(60))
            .build();
    }
    
    private String buildJsonBody(ChatRequest request) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(request.model()).append("\",");
        json.append("\"messages\":[");

        for (int i = 0; i < request.messages().size(); i++) {
            Message msg = request.messages().get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"role\":\"").append(msg.role()).append("\",");
            json.append("\"content\":\"").append(escapeJson(msg.content())).append("\"");
            json.append("}");
        }
        
        json.append("]");
        if (request.temperature() != null) {
            json.append(",\"temperature\":").append(request.temperature());
        }
        if (request.maxTokens() != null) {
            json.append(",\"max_tokens\":").append(request.maxTokens());
        }        
        json.append("}");
        return json.toString();
    }
    
    private ChatResponse parseResponse(String responseBody) {
        // Extract: choices[0].message.content        
        try {
            int contentStart = responseBody.indexOf("\"content\":");
            if (contentStart == -1) {
                throw new RuntimeException("No content in response");
            }
            int quoteStart = responseBody.indexOf("\"", contentStart + 10);
            if (quoteStart == -1) {
                throw new RuntimeException("Malformed content field");
            }
            int quoteEnd = findClosingQuote(responseBody, quoteStart + 1);
            if (quoteEnd == -1) {
                throw new RuntimeException("Malformed content field");
            }
            
            String content = responseBody.substring(quoteStart + 1, quoteEnd);
            content = unescapeJson(content);
            TokenUsage usage = parseTokenUsage(responseBody);
            return new ChatResponse(content, usage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response: " + e.getMessage(), e);
        }
    }
    
    private TokenUsage parseTokenUsage(String responseBody) {        
        try {
            int usageStart = responseBody.indexOf("\"usage\":");
            if (usageStart == -1) {
                return new TokenUsage(0, 0, 0);
            }
            
            String usageSection = responseBody.substring(usageStart);
            int promptTokens = extractNumber(usageSection, "\"prompt_tokens\":");
            int completionTokens = extractNumber(usageSection, "\"completion_tokens\":");
            int totalTokens = extractNumber(usageSection, "\"total_tokens\":");
            return new TokenUsage(promptTokens, completionTokens, totalTokens);         
        } catch (Exception e) {
            return new TokenUsage(0, 0, 0);
        }
    }
    
    private RuntimeException classifyError(HttpResponse<String> response, String model) {
        int status = response.statusCode();
        String body = response.body();
        
        return switch (status) {
        	case 400 -> {
        		if(body != null && body.contains("maximum context length")) {
        			yield new TokenLimitExceededException(
        					"Prompt exceeds model context length",
        		            Models.getInfo(model).contextLength()
        			);
        		}
        		yield new LLMException("Bad request: " + body);
        	}
            case 401 -> new InvalidAPIKeyException(
                "Invalid API key. Get your key from https://platform.openai.com/api-keys"
            );
            case 404 -> new ModelNotFoundException(
            		model,
            		"Model '" + model + "' does not exist. " +
            	    "Available models: " + String.join(", ", Models.getSupportedModels())
            );
            case 429 -> new RateLimitException(
                "Rate limit exceeded. Slow down or upgrade your plan."
            );
            case 500, 502, 503 -> new NetworkException(
                 "OpenAI server error (" + status + "). Try again later.",
                 null
            );
            default -> new LLMException(
                 "OpenAI request failed with status " + status + ": " + body
            );
        };
    }
 
    private String escapeJson(String str) {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    private String unescapeJson(String str) {
        return str
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\");
    }
    
    private int findClosingQuote(String str, int start) {
        for (int i = start; i < str.length(); i++) {
            if (str.charAt(i) == '"' && str.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }
    
    private int extractNumber(String json, String key) {
        int keyPos = json.indexOf(key);
        if (keyPos == -1) return 0;       
        int start = keyPos + key.length();
        int end = start;       
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }     
        String numStr = json.substring(start, end).trim();
        return numStr.isEmpty() ? 0 : Integer.parseInt(numStr);
    }
}