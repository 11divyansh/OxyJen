package io.oxyjen.llm.transport.gemini;

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
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;
import io.oxyjen.llm.semantics.Message;

/**
 * Low-level HTTP client for GEMINI API.
 * 
 * This is the Transport layer. It knows NOTHING about:
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
public final class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String GENERATE_ENDPOINT = ":generateContent";

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public GeminiClient(String apiKey) {
        this(apiKey, DEFAULT_REQUEST_TIMEOUT);
    }

    public GeminiClient(String apiKey, Duration requestTimeout) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
            .build();
    }

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
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new NetworkException("Gemini request failed: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildHttpRequest(ChatRequest request) {
        String url = BASE_URL + request.model() + GENERATE_ENDPOINT + "?key=" + apiKey;
        String json = buildJsonBody(request);
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(requestTimeout)
            .build();
    }

    private String buildJsonBody(ChatRequest request) {
        // Gemini format:
        // {
        //   "contents": [{"role": "user", "parts": [{"text": "..."}]}],
        //   "generationConfig": {"temperature": 0.0, "maxOutputTokens": 1000}
        // }
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"contents\":[");

        for (int i = 0; i < request.messages().size(); i++) {
            Message msg = request.messages().get(i);
            if (i > 0) json.append(",");
            json.append("{");
            String role = msg.role().equals("assistant") ? "model" : msg.role();
            json.append("\"role\":\"").append(role).append("\",");
            json.append("\"parts\":[{\"text\":\"")
                .append(escapeJson(msg.content()))
                .append("\"}]");
            json.append("}");
        }

        json.append("]");

        // generationConfig block
        boolean hasConfig = request.temperature() != null || request.maxTokens() != null;
        if (hasConfig) {
            json.append(",\"generationConfig\":{");
            boolean first = true;
            if (request.temperature() != null) {
                json.append("\"temperature\":").append(request.temperature());
                first = false;
            }
            if (request.maxTokens() != null) {
                if (!first) json.append(",");
                json.append("\"maxOutputTokens\":").append(request.maxTokens());
            }
            json.append("}");
        }

        json.append("}");
        return json.toString();
    }

    private ChatResponse parseResponse(String responseBody) {
        // Gemini response:
        // {
        //   "candidates": [{
        //     "content": {
        //       "parts": [{"text": "..."}],
        //       "role": "model"
        //     }
        //   }],
        //   "usageMetadata": {
        //     "promptTokenCount": 10,
        //     "candidatesTokenCount": 20,
        //     "totalTokenCount": 30
        //   }
        // }
        try {
            // Extract candidates[0].content.parts[0].text
            int partsIndex = responseBody.indexOf("\"parts\":");
            if (partsIndex == -1) {
                throw new RuntimeException("No parts in response");
            }
            int textIndex = responseBody.indexOf("\"text\":", partsIndex);
            if (textIndex == -1) {
                throw new RuntimeException("No text in response parts");
            }
            int quoteStart = responseBody.indexOf("\"", textIndex + 7);
            if (quoteStart == -1) {
                throw new RuntimeException("Malformed text field");
            }
            int quoteEnd = findClosingQuote(responseBody, quoteStart + 1);
            if (quoteEnd == -1) {
                throw new RuntimeException("Malformed text field");
            }
            String content = responseBody.substring(quoteStart + 1, quoteEnd);
            content = unescapeJson(content);
            TokenUsage usage = parseTokenUsage(responseBody);
            return new ChatResponse(content, usage);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to parse Gemini response: " + e.getMessage(), e
            );
        }
    }

    private TokenUsage parseTokenUsage(String responseBody) {
        try {
            int promptTokens = extractNumber(responseBody, "\"promptTokenCount\":");
            int completionTokens = extractNumber(responseBody, "\"candidatesTokenCount\":");
            int totalTokens = extractNumber(responseBody, "\"totalTokenCount\":");
            return new TokenUsage(promptTokens, completionTokens, totalTokens);
        } catch (Exception e) {
            return new TokenUsage(0, 0, 0);
        }
    }

    private RuntimeException classifyError(HttpResponse<String> response, String model) {
        int status = response.statusCode();
        String body = response.body();

        return switch (status) {
            case 400 -> new LLMException("Bad request: " + body);
            case 401, 403 -> new InvalidAPIKeyException(
                "Invalid Gemini API key. Get one free at: https://aistudio.google.com/apikey"
            );
            case 404 -> new ModelNotFoundException(
                model,
                "Model '" + model + "' not found. " +
                "Available: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-1.5-pro"
            );
            case 429 -> new RateLimitException(
                "Gemini rate limit exceeded. " +
                "Free tier: 15 RPM, 1M TPM. Consider upgrading or adding delays."
            );
            case 500, 502, 503 -> new NetworkException(
                "Gemini server error (" + status + "). Try again later.", null
            );
            default -> new LLMException(
                "Gemini request failed with status " + status + ": " + body
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
        while (start < json.length() && !Character.isDigit(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        String numStr = json.substring(start, end).trim();
        return numStr.isEmpty() ? 0 : Integer.parseInt(numStr);
    }
}