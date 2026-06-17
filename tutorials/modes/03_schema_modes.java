package tutorials.modes;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.SchemaNode;
import io.oxyjen.llm.schema.SchemaGenerator;

/**
 * Modes tutorial 3:
 * Schema hard-fail versus soft-fail.
 */
final class SchemaModesTutorial {

    public record Ticket(String id, String priority, String summary) {}

    private SchemaModesTutorial() {}

    public static void main(String[] args) {
        ChatModel model = LLM.gemini("gemini/gemini-flash-latest");
        JSONSchema schema = SchemaGenerator.fromClass(Ticket.class);

        SchemaNode<Ticket> hardFail = SchemaNode.builder(Ticket.class)
            .model(model)
            .schema(schema)
            .failOnInvalid(true)
            .maxRetries(2)
            .build();

        SchemaNode<Ticket> softFail = SchemaNode.builder(Ticket.class)
            .model(model)
            .schema(schema)
            .failOnInvalid(false)
            .maxRetries(2)
            .build();

        System.out.println(hardFail.getClass().getSimpleName());
        System.out.println(softFail.getClass().getSimpleName());
    }
}
