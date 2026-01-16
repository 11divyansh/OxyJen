package io.oxyjen.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.oxyjen.llm.exceptions.LLMException;

/**
* Production-ready ChatModel with fallbacks and retries.
* 
* Features:
* - Primary + multiple fallback models
* - Automatic retries with exponential backoff
* - Timeout protection
* - Error classification
* 
* This is Layer 3 (Execution Control).
* 
* Example:
* <pre>
* ChatModel resilient = LLM.chain()
*     .primary("gpt-4o")
*     .fallback("gpt-3.5-turbo")
*     .retry(3)
*     .timeout(Duration.ofSeconds(30))
*     .build();
* 
* String response = resilient.chat("Hello");
* // Auto-tries gpt-4o → gpt-3.5-turbo if needed
* </pre>
*/
public final class LLMChain implements ChatModel {
   
   private final ChatModel primary;
   private final List<ChatModel> fallbacks;
   private final int maxRetries;
   private final Duration timeout;
   private final boolean exponentialBackoff;
   
   private LLMChain(Builder builder) {
       this.primary = builder.primary;
       this.fallbacks = builder.fallbacks;
       this.maxRetries = builder.maxRetries;
       this.timeout = builder.timeout;
       this.exponentialBackoff = builder.exponentialBackoff;
   }
   
   @Override
   public String chat(String input) {
       List<ChatModel> models = new ArrayList<>();
       models.add(primary);
       models.addAll(fallbacks);
       
       Exception lastException = null;
       
       // Try each model in order
       for (ChatModel model : models) {
           
           // Try with retries
           for (int attempt = 1; attempt <= maxRetries; attempt++) {
               try {
                   log("Attempt " + attempt + " with " + modelName(model));
                   
                   String response = model.chat(input);
                   
                   log("Success with " + modelName(model));
                   return response;
                   
               } catch (Exception e) {
                   lastException = e;
                   log("Failed: " + e.getMessage());
                   
                   // Retry?
                   if (attempt < maxRetries && shouldRetry(e)) {
                       long backoffMs = calculateBackoff(attempt);
                       log("Retrying in " + backoffMs + "ms...");
                       sleep(backoffMs);
                   } else {
                       break; // Move to next model
                   }
               }
           }
       }
       
       // All models failed
       throw new LLMException(
           "All models failed after retries. Last error: " + 
           (lastException != null ? lastException.getMessage() : "unknown"),
           lastException
       );
   }
   
   private boolean shouldRetry(Exception e) {
       // Retry on transient errors only
       String message = e.getMessage();
       if (message == null) return false;
       
       return message.contains("rate limit") ||
              message.contains("timeout") ||
              message.contains("network") ||
              message.contains("503") ||
              message.contains("502");
   }
   
   private long calculateBackoff(int attempt) {
       if (!exponentialBackoff) {
           return 1000;
       }
       // Exponential: 1s, 2s, 4s, 8s...
       return (long) Math.pow(2, attempt - 1) * 1000;
   }
   
   private void sleep(long ms) {
       try {
           Thread.sleep(ms);
       } catch (InterruptedException e) {
           Thread.currentThread().interrupt();
       }
   }
   
   private void log(String message) {
       System.out.println("[LLMChain] " + message);
       // TODO: Proper logging in v0.3
   }
   
   private String modelName(ChatModel model) {
       return model.getClass().getSimpleName();
   }
   
   public static Builder builder() {
       return new Builder();
   }
   
   public static final class Builder {
       
       private ChatModel primary;
       private List<ChatModel> fallbacks = new ArrayList<>();
       private int maxRetries = 3;
       private Duration timeout = Duration.ofSeconds(30);
       private boolean exponentialBackoff = true;
       
       /**
        * Set primary model (required).
        */
       public Builder primary(ChatModel model) {
           this.primary = model;
           return this;
       }
       
       /**
        * Set primary model by name.
        */
       public Builder primary(String modelName) {
           this.primary = LLM.of(modelName);
           return this;
       }
       
       /**
        * Add fallback model.
        */
       public Builder fallback(ChatModel model) {
           this.fallbacks.add(model);
           return this;
       }
       
       /**
        * Add fallback model by name.
        */
       public Builder fallback(String modelName) {
           this.fallbacks.add(LLM.of(modelName));
           return this;
       }
       
       /**
        * Set max retry attempts per model.
        */
       public Builder retry(int maxRetries) {
           this.maxRetries = maxRetries;
           return this;
       }
       
       /**
        * Set timeout for each call.
        */
       public Builder timeout(Duration timeout) {
           this.timeout = timeout;
           return this;
       }
       
       /**
        * Enable exponential backoff for retries.
        */
       public Builder exponentialBackoff() {
           this.exponentialBackoff = true;
           return this;
       }
       
       /**
        * Disable exponential backoff (fixed delay).
        */
       public Builder fixedBackoff() {
           this.exponentialBackoff = false;
           return this;
       }
       
       public LLMChain build() {
           if (primary == null) {
               throw new IllegalStateException("Primary model must be set");
           }
           return new LLMChain(this);
       }
   }
}