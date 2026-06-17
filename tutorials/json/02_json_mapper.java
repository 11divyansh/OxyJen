package tutorials.json;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.oxyjen.util.JsonMapper;

/**
 * JSON tutorial 2:
 * Map JSON into records, POJOs, collections, maps, and optionals.
 */
final class JsonMapperTutorial {

    record Address(String city, String country) {}
    record Person(String name, int age, Optional<String> email, Address address, List<String> tags, Map<String, Integer> scores) {}

    public static final class Settings {
        private String mode;
        private boolean enabled;

        public Settings() {}
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    private JsonMapperTutorial() {}

    public static void main(String[] args) {
        String json = """
            {
              "name": "Divyansh",
              "age": 24,
              "email": "dev@example.com",
              "address": {"city":"Delhi","country":"IN"},
              "tags": ["graph","llm"],
              "scores": {"docs": 10, "tests": 9}
            }
            """;

        Person person = JsonMapper.deserialize(json, Person.class);
        Settings settings = JsonMapper.deserialize("{\"mode\":\"safe\",\"enabled\":true}", Settings.class);

        System.out.println(person);
        System.out.println(settings.getMode());
        System.out.println(JsonMapper.fromJsonTree(Map.of("city", "Delhi", "country", "IN"), Address.class));
    }
}

