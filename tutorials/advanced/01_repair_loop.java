package tutorials.advanced;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.schema.SchemaEnforcer;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.SchemaNode;

/**
 * Advanced tutorial 1:
 * Bounded repair loop for invalid structured outputs.
 */
final class RepairLoopTutorial {

    public record Product(String name, String sku, String price) {}

    private RepairLoopTutorial() {}

    public static void main(String[] args) {
        ChatModel model = LLM.gemini("gemini/gemini-flash-latest");
        var schema = SchemaGenerator.fromClass(Product.class);
        SchemaNode<Product> node = SchemaNode.builder(Product.class)
            .model(model)
            .schema(schema)
            .maxRetries(3)
            .failOnInvalid(false)
            .build();

        SchemaEnforcer enforcer = new SchemaEnforcer(model, schema, 3, false);
        System.out.println(node.getClass().getSimpleName());
        System.out.println(enforcer.getClass().getSimpleName());
    }
}
