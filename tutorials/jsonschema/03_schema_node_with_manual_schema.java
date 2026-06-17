package tutorials.jsonschema;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.SchemaNode;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;

/**
 * JSONSchema tutorial 3:
 * Feed a manual schema into SchemaNode.
 */
final class SchemaNodeWithManualSchemaTutorial {

    record Classification(String type, String reason) {}

    private SchemaNodeWithManualSchemaTutorial() {}

    public static void main(String[] args) {
        ChatModel model = LLM.gemini("gemini/gemini-flash-latest");

        JSONSchema schema = JSONSchema.object()
            .property("type", PropertySchema.enumOf("Type", "invoice", "contract", "receipt", "other").build())
            .property("reason", PropertySchema.string("Why the type was chosen").build())
            .required("type", "reason")
            .build();

        SchemaNode<Classification> node = SchemaNode.builder(Classification.class)
            .model(model)
            .schema(schema)
            .maxRetries(3)
            .failOnInvalid(true)
            .build();

        System.out.println(node.getClass().getSimpleName());
    }
}

