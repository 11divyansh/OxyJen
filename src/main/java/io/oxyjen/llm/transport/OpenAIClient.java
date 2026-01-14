package io.oxyjen.llm.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
            // 1. Build HTTP request
            HttpRequest httpRequest = buildHttpRequest(request);
            
            // 2. Send request
            HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );
            
            // 3. Handle errors
            if (response.statusCode() != 200) {
                throw classifyError(response);
            }
            
            // 4. Parse response
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
        // Simple JSON building (no Jackson in v0.2)
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(request.model()).append("\",");
        json.append("\"messages\":[");
        
        // Add messages
        for (int i = 0; i < request.messages().size(); i++) {
            Message msg = request.messages().get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"role\":\"").append(msg.role()).append("\",");
            json.append("\"content\":\"").append(escapeJson(msg.content())).append("\"");
            json.append("}");
        }
        
        json.append("]");
        
        // Add optional parameters
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
            // Find "content": "..."
            int contentStart = responseBody.indexOf("\"content\":");
            if (contentStart == -1) {
                throw new RuntimeException("No content in response");
            }
            
            // Find the opening quote after "content":
            int quoteStart = responseBody.indexOf("\"", contentStart + 10);
            if (quoteStart == -1) {
                throw new RuntimeException("Malformed content field");
            }
            
            // Find the closing quote
            int quoteEnd = findClosingQuote(responseBody, quoteStart + 1);
            if (quoteEnd == -1) {
                throw new RuntimeException("Malformed content field");
            }
            
            String content = responseBody.substring(quoteStart + 1, quoteEnd);
            content = unescapeJson(content);
            
            // Extract token usage (only for cost tracking)
            TokenUsage usage = parseTokenUsage(responseBody);
            
            return new ChatResponse(content, usage);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response: " + e.getMessage(), e);
        }
    }
    
    private TokenUsage parseTokenUsage(String responseBody) {
        // Extract: usage.prompt_tokens, usage.completion_tokens, usage.total_tokens
        
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
    
    private RuntimeException classifyError(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();
        
        return switch (status) {
            case 401 -> new RuntimeException(
                "Invalid API key. Get your key from https://platform.openai.com/api-keys"
            );
            case 429 -> new RuntimeException(
                "Rate limit exceeded. Slow down or upgrade your plan."
            );
            case 500, 502, 503 -> new RuntimeException(
                "OpenAI server error. Try again in a moment."
            );
            default -> new RuntimeException(
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
        
        // Find end of number
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        
        String numStr = json.substring(start, end).trim();
        return numStr.isEmpty() ? 0 : Integer.parseInt(numStr);
    }
}