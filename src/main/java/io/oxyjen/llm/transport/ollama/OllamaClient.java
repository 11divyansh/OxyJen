package io.oxyjen.llm.transport.ollama;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.oxyjen.llm.exceptions.LLMException;
import io.oxyjen.llm.exceptions.ModelNotFoundException;
import io.oxyjen.llm.exceptions.NetworkException;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;
import io.oxyjen.llm.semantics.Message;

public final class OllamaClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String CHAT_ENDPOINT = "/api/chat";
    
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public OllamaClient() {
        this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
    }
    
    public OllamaClient(String baseUrl) {
        this(baseUrl, DEFAULT_REQUEST_TIMEOUT);
    }

    public OllamaClient(String baseUrl, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
            throw new NetworkException("Ollama request failed: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildHttpRequest(ChatRequest request) {
        String json = buildJsonBody(request);
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + CHAT_ENDPOINT))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(requestTimeout) // local models can be slow
            .build();
    }

    private String buildJsonBody(ChatRequest request) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(request.model()).append("\",");
        json.append("\"stream\":false,"); // we want full response, not stream
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

        // Ollama options block for temperature, etc.
        if (request.temperature() != null || request.maxTokens() != null) {
            json.append(",\"options\":{");
            boolean first = true;
            if (request.temperature() != null) {
                json.append("\"temperature\":").append(request.temperature());
                first = false;
            }
            if (request.maxTokens() != null) {
                if (!first) json.append(",");
                json.append("\"num_predict\":").append(request.maxTokens());
            }
            json.append("}");
        }

        json.append("}");
        return json.toString();
    }

    private ChatResponse parseResponse(String responseBody) {
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

            // Ollama uses prompt_eval_count / eval_count 
            TokenUsage usage = parseTokenUsage(responseBody);
            return new ChatResponse(content, usage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response: " + e.getMessage(), e);
        }
    }

    private TokenUsage parseTokenUsage(String responseBody) {
        try {
            int promptTokens = extractNumber(responseBody, "\"prompt_eval_count\":");
            int completionTokens = extractNumber(responseBody, "\"eval_count\":");
            return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
        } catch (Exception e) {
            return new TokenUsage(0, 0, 0);
        }
    }

    private RuntimeException classifyError(HttpResponse<String> response, String model) {
        int status = response.statusCode();
        String body = response.body();

        return switch (status) {
            case 400 -> new LLMException("Bad request: " + body);
            case 404 -> new ModelNotFoundException(
                model,
                "Model '" + model + "' not found in Ollama. " +
                "Pull it first: ollama pull " + model
            );
            case 500 -> new NetworkException(
                "Ollama server error. Is Ollama running? Start with: ollama serve",
                null
            );
            default -> new LLMException("Ollama request failed with status " + status + ": " + body);
        };
    }

    // ---- same helpers as OpenAIClient ----

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