package io.oxyjen.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* Profile lookup (package-private helper).
* 
* NOT public API. Users access via LLM.profile().
*/
final class LLMProfiles {
   
   private static final Map<String, String> profiles = new ConcurrentHashMap<>();
   
   static {
       // Default profiles
       profiles.put("fast", "gpt-4o-mini");
       profiles.put("cheap", "gpt-3.5-turbo");
       profiles.put("smart", "gpt-4o");
   }
   
   /**
    * Get ChatModel for a profile.
    */
   static ChatModel get(String name) {
       String model = profiles.get(name);
       if (model == null) {
           throw new IllegalArgumentException(
               "Unknown profile: " + name + "\n" +
               "Available profiles: " + profiles.keySet()
           );
       }
       return LLM.of(model);
   }
   
   /**
    * Register a custom profile.
    */
   static void register(String name, String model) {
       profiles.put(name, model);
   }
   
   private LLMProfiles() {
       // No instances
   }
}