package io.oxyjen.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.oxyjen.llm.exceptions.LLMException;
import io.oxyjen.llm.exceptions.NetworkException;
import io.oxyjen.llm.exceptions.RateLimitException;
import io.oxyjen.llm.exceptions.TimeoutException;
import io.oxyjen.llm.internal.TimedChatModel;

/**
* ChatModel with fallbacks and retries.
* 
* Features:
* - Primary + multiple fallback models
* - Automatic retries with exponential backoff
* - Timeout protection
* - Error classification
* - Jitter & Retry cap
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
   private final boolean exponentialBackoff;
   private final Duration maxBackoff;      
   private final double jitterFactor;
   
   private LLMChain(Builder builder) {
	   // Wrap primary with timeout
	   if (builder.timeout != null) {
		   this.primary= new TimedChatModel(builder.primary, builder.timeout);
	   } else {
		   this.primary = builder.primary;
	   }
	   // Wrap fallbacks with timeout
       this.fallbacks = new ArrayList<>(builder.fallbacks.size());
       for (ChatModel fallback : builder.fallbacks) {
    	   if (builder.timeout != null) {
    		   this.fallbacks.add(new TimedChatModel(fallback, builder.timeout));
    	   } else {
    		   this.fallbacks.add(fallback);
    	   }
       } 
       this.maxRetries = builder.maxRetries;
       this.exponentialBackoff = builder.exponentialBackoff;
       this.maxBackoff = builder.maxBackoff;        
       this.jitterFactor = builder.jitterFactor;
   }
   
   @Override
   public String chat(String input) {
       List<ChatModel> models = new ArrayList<>();
       models.add(primary);
       models.addAll(fallbacks);
       
       Exception lastException = null;
       
       for (ChatModel model : models) {
           for (int attempt = 1; attempt <= maxRetries; attempt++) {
               try {
                   log("Attempt " + attempt + " with " + modelName(model));
                   
                   String response = model.chat(input);
                   
                   log("Success with " + modelName(model));
                   return response;
                   
               } catch (Exception e) {
                   lastException = e;
                   log("Failed: " + e.getMessage());
                   
                   if (attempt < maxRetries && shouldRetry(e)) {
                       long backoffMs = calculateBackoff(attempt);
                       log("Retrying in " + backoffMs + "ms...");
                       sleep(backoffMs);
                   } else {
                       break; 
                   }
               }
           }
       }
       
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
       
       return e instanceof RateLimitException ||
              e instanceof NetworkException ||
              e instanceof TimeoutException ;
         
   }
   
   private long calculateBackoff(int attempt) {
	   // 1. Calculate base delay
	    Duration base;
	    if (exponentialBackoff) {
	        // Exponential: 1s, 2s, 4s, 8s, 16s....
	        // 1 << (attempt-1) = 2^(attempt-1)
	        long seconds = 1L << (attempt - 1);
	        base = Duration.ofSeconds(seconds);
	    } else {
	        // Fixed: always 1s
	        base = Duration.ofSeconds(1);
	    }
	    
	    // 2. Apply jitter first(if enabled)
	    if (jitterFactor > 0) {
	    	// Ex: jitterFactor=0.2 -> range is 0.8 to 1.2
	        double jitterMultiplier =java.util.concurrent.ThreadLocalRandom.current()
	                .nextDouble(1.0 - jitterFactor, 1.0 + jitterFactor);

	        long jitteredMs = (long) (base.toMillis() * jitterMultiplier);
	        base = Duration.ofMillis(jitteredMs);
	    }

	    // 3. Apply cap last(if set)
	    if (maxBackoff != null && base.compareTo(maxBackoff) > 0) {
	        base = maxBackoff;
	    }  
	    
	    return base.toMillis();
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
       private Duration timeout = null;
       private boolean exponentialBackoff = true;
       private Duration maxBackoff = null;     
       private double jitterFactor = 0.0;
       
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
       
       /**
        * Set maximum backoff duration (caps exponential growth).
        * Example: maxBackoff(Duration.ofSeconds(10))
        */
       public Builder maxBackoff(Duration maxBackoff) {
    	   if (maxBackoff.isZero() || maxBackoff.isNegative()) {
    		    throw new IllegalArgumentException("maxBackoff must be positive");
    	   }
           this.maxBackoff = maxBackoff;
           return this;
       }

       /**
        * Enable jitter to randomize retry delays.
        * @param factor Jitter factor (0.0 to 1.0). 
        *               0.2 means ±20% randomness.
        * Example: jitter(0.2)
        */
       public Builder jitter(double factor) {
           if (factor < 0 || factor > 1) {
               throw new IllegalArgumentException(
                   "Jitter factor must be between 0 and 1, got: " + factor
               );
           }
           this.jitterFactor = factor;
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