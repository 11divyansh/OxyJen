package io.oxyjen.llm.transport.openai;

import io.oxyjen.llm.ChatModel;

/**
* Factory for creating OpenAI ChatModels.
* 
* They access it via LLM.of("gpt-4o") which calls this.
* 
* Responsibilities:
* - Read API key from environment
* - Validate model name
* - Create OpenAIChatModel instances
* 
* This is the glue between LLM.of() and OpenAIChatModel.
*/
public final class OpenAIModels {
   
   private static final String ENV_API_KEY = "OPENAI_API_KEY";
   private static final String API_KEY_URL = "https://platform.openai.com/api-keys";
   
   /**
    * Create OpenAI ChatModel.
    * 
    * @param modelName Model name (e.g., "gpt-4o", "gpt-4o-mini")
    * @return ChatModel instance
    * @throws IllegalStateException if API key not set
    * @throws IllegalArgumentException if model unknown
    */
   public static ChatModel create(String modelName) {
       // 1. Get API key from environment
       String apiKey = System.getenv(ENV_API_KEY);
       if (apiKey == null || apiKey.trim().isEmpty()) {
           throw new IllegalStateException(
               "OPENAI_API_KEY environment variable not set.\n" +
               "\n" +
               "Get your API key from: " + API_KEY_URL + "\n" +
               "Then set it:\n" +
               "  export OPENAI_API_KEY=sk-your-key-here\n" +
               "\n" +
               "Or pass it explicitly:\n" +
               "  LLM.openai(\"" + modelName + "\", \"your-key-here\")"
           );
       }
       
       // 2. Validate model
       if (!Models.isSupported(modelName)) {
           throw new IllegalArgumentException(
               "Unknown OpenAI model: " + modelName + "\n" +
               "Supported models: " + String.join(", ", Models.getSupportedModels())
           );
       }
       
       // 3. Create and return
       return new OpenAIChatModel(apiKey, modelName);
   }
   
   /**
    * Create OpenAI ChatModel with explicit API key.
    */
   public static ChatModel create(String modelName, String apiKey) {
       if (!Models.isSupported(modelName)) {
           throw new IllegalArgumentException(
               "Unknown OpenAI model: " + modelName + "\n" +
               "Supported models: " + String.join(", ", Models.getSupportedModels())
           );
       }
       
       return new OpenAIChatModel(apiKey, modelName);
   }
   
   private OpenAIModels() {
       // No instances
   }
}