package tutorials.jsonschema;

import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;

/**
 * JSONSchema tutorial 1:
 * Build a schema manually.
 */
final class ManualSchemaBuilderTutorial {

    private ManualSchemaBuilderTutorial() {}

    public static void main(String[] args) {
        JSONSchema address = JSONSchema.object()
            .description("Postal address")
            .property("line1", PropertySchema.string("Address line 1").minLength(1).build())
            .property("city", PropertySchema.string("City").build())
            .property("country", PropertySchema.enumOf("Country", "IN", "US", "GB").build())
            .required("line1", "city", "country")
            .build();

        JSONSchema invoice = JSONSchema.object()
            .description("Invoice extraction schema")
            .property("invoiceNo", PropertySchema.string("Invoice number").build())
            .property("vendor", PropertySchema.string("Vendor name").build())
            .property("status", PropertySchema.enumOf("Processing state", "NEW", "REVIEW", "APPROVED").build())
            .property("amounts", PropertySchema.array("Amounts", PropertySchema.number("Amount item").minimum(0).build()).build())
            .property("metadata", PropertySchema.map("Extra metadata", PropertySchema.string("Value")).build())
            .property("shipTo", PropertySchema.object("Shipping address", address))
            .required("invoiceNo", "vendor", "status")
            .build();

        System.out.println(invoice.toJSON());
    }
}
