package tutorials.jsonschema;

import java.util.List;

import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.annotations.Description;
import io.oxyjen.llm.schema.annotations.JsonIgnore;
import io.oxyjen.llm.schema.annotations.Max;
import io.oxyjen.llm.schema.annotations.Min;
import io.oxyjen.llm.schema.annotations.Pattern;
import io.oxyjen.llm.schema.annotations.Size;

/**
 * JSONSchema tutorial 2:
 * Generate schemas from records and annotations.
 */
final class SchemaFromClassTutorial {

    record Vendor(
        @Description("Vendor display name")
        @Size(min = 1, max = 120)
        String name,
        @Pattern("^[A-Z0-9-]+$")
        String taxId,
        @JsonIgnore
        String internalNote
    ) {}

    record PurchaseOrder(
        @Description("Order identifier")
        String poNumber,
        @Min(0)
        long total,
        @Max(100)
        int confidence,
        List<String> lineItems
    ) {}

    private SchemaFromClassTutorial() {}

    public static void main(String[] args) {
        JSONSchema vendorSchema = SchemaGenerator.fromClass(Vendor.class);
        JSONSchema poSchema = SchemaGenerator.fromClass(PurchaseOrder.class);

        System.out.println(vendorSchema.toJSON());
        System.out.println(poSchema.toJSON());
    }
}

