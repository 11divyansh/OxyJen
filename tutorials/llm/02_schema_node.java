package tutorials.llm;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.SchemaNode;

/**
 * LLM tutorial 2:
 * Structured outputs with SchemaNode.
 */
final class SchemaNodeTutorial {

    public record InvoiceExtraction(String vendor, String invoiceNo, String totalDue) {}

    private SchemaNodeTutorial() {}

    public static void main(String[] args) {
        ChatModel model = LLM.gemini("gemini/gemini-flash-latest");

        SchemaNode<InvoiceExtraction> extractor = SchemaNode.builder(InvoiceExtraction.class)
            .model(model)
            .schema(SchemaGenerator.fromClass(InvoiceExtraction.class))
            .maxRetries(3)
            .failOnInvalid(true)
            .build();

        String prompt = "Extract vendor, invoice number, and total due from this invoice text.";
        System.out.println(extractor.getClass().getSimpleName());
        System.out.println(prompt);
    }
}
