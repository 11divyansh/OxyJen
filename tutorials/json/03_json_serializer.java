package tutorials.json;

import java.util.List;
import java.util.Map;

import io.oxyjen.util.JsonSerializer;

/**
 * JSON tutorial 3:
 * Serialize records, POJOs, lists, maps, and arrays to JSON trees.
 */
final class JsonSerializerTutorial {

    record Item(String name, int quantity) {}

    public static final class Box {
        private String label = "A";
        private boolean sealed = true;

        public String getLabel() { return label; }
        public boolean isSealed() { return sealed; }
    }

    private JsonSerializerTutorial() {}

    public static void main(String[] args) {
        Item item = new Item("paper", 10);
        Box box = new Box();

        Object itemTree = JsonSerializer.toJsonTree(item);
        Object boxTree = JsonSerializer.toJsonTree(box);
        String itemJson = JsonSerializer.toJsonString(item);
        String boxJson = JsonSerializer.toJsonString(box);

        System.out.println(itemTree);
        System.out.println(boxTree);
        System.out.println(itemJson);
        System.out.println(boxJson);
        System.out.println(JsonSerializer.toJsonTree(List.of(1, 2, 3)));
        System.out.println(JsonSerializer.toJsonTree(Map.of("a", 1, "b", 2)));
    }
}

