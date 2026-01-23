package io.oxyjen.llm.error.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.oxyjen.llm.exceptions.InvalidAPIKeyException;
import io.oxyjen.llm.exceptions.ModelNotFoundException;
import io.oxyjen.llm.exceptions.NetworkException;
import io.oxyjen.llm.exceptions.RateLimitException;
import io.oxyjen.llm.exceptions.TokenLimitExceededException;
import io.oxyjen.llm.transport.openai.OpenAIClient;

@ExtendWith(MockitoExtension.class)
class OpenAIErrorClassificationTest {

    private final OpenAIClient client =
        new OpenAIClient("sk-test"); // dummy key, never used


    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private RuntimeException classify(
        HttpResponse<String> response,
        String model
    ) throws Exception {

        var method = OpenAIClient.class.getDeclaredMethod(
            "classifyError",
            HttpResponse.class,
            String.class
        );
        method.setAccessible(true);

        return (RuntimeException) method.invoke(client, response, model);
    }

    private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    // ---------------- tests ----------------

    @Test
    void tokenLimitExceededOnContextError() throws Exception {
        log("400 + context error → TokenLimitExceededException");

        HttpResponse<String> response =
            mockResponse(400, "maximum context length exceeded");

        RuntimeException ex = classify(response, "gpt-4o");

        assertTrue(ex instanceof TokenLimitExceededException);
    }

    @Test
    void invalidApiKeyOn401() throws Exception {
        log("401 → InvalidAPIKeyException");

        HttpResponse<String> response =
            mockResponse(401, "invalid api key");

        RuntimeException ex = classify(response, "gpt-4o");

        assertTrue(ex instanceof InvalidAPIKeyException);
    }

    @Test
    void modelNotFoundOn404() throws Exception {
        log("404 → ModelNotFoundException");

        HttpResponse<String> response =
            mockResponse(404, "model not found");

        RuntimeException ex = classify(response, "gpt-unknown");

        assertTrue(ex instanceof ModelNotFoundException);
    }

    @Test
    void rateLimitOn429() throws Exception {
        log("429 → RateLimitException");

        HttpResponse<String> response =
            mockResponse(429, "rate limit exceeded");

        RuntimeException ex = classify(response, "gpt-4o");

        assertTrue(ex instanceof RateLimitException);
    }

    @Test
    void serverErrorsMapToNetworkException() throws Exception {
        log("5xx → NetworkException");

        for (int status : new int[]{500, 502, 503}) {
            HttpResponse<String> response =
                mockResponse(status, "server error");

            RuntimeException ex = classify(response, "gpt-4o");

            assertTrue(ex instanceof NetworkException);
        }
    }
}
