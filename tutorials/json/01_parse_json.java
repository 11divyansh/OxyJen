package tutorials.json;

import java.util.List;
import java.util.Map;

import io.oxyjen.util.JsonParser;

/**
 * JSON tutorial 1:
 * Parse JSON into Java trees.
 */
final class ParseJsonTutorial {

    private ParseJsonTutorial() {}

    public static void main(String[] args) {
        Object object = JsonParser.parse("{\"name\":\"Oxyjen\",\"count\":3,\"tags\":[\"graph\",\"llm\"]}");
        Object array = JsonParser.parse("[1,2,3]");
        Object value = JsonParser.parse("\"hello\"");

        System.out.println(object.getClass().getSimpleName());
        System.out.println(array.getClass().getSimpleName());
        System.out.println(value);

        Map<?, ?> map = (Map<?, ?>) object;
        List<?> tags = (List<?>) map.get("tags");
        System.out.println(tags);
    }
}

