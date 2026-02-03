package io.oxyjen.llm.schema.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.FieldError;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.llm.schema.SchemaValidator;
import io.oxyjen.llm.schema.SchemaValidator.ValidationResult;

public class SchemaTest {

	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

	@Test
	void schemaBuilds() {
		log("Schema build test");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .required("name")
	        .build();
	    out.println(schema.toJSON());
	    assertNotNull(schema);
	}
	@Test
	void schemaToJsonContainsProperties() {

		log("Schema to json test");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .property("age", PropertySchema.number("Age"))
	        .required("name")
	        .build();

	    String json = schema.toJSON();
	    out.println(json);
	    assertTrue(json.contains("\"name\""));
	    assertTrue(json.contains("\"age\""));
	    assertTrue(json.contains("\"required\""));
	}

	@Test
	void enumSchemaSerialized() {
		log("Enum schema builder");
	    JSONSchema schema = JSONSchema.object()
	        .property("status",
	            PropertySchema.enumOf("Status", "open","closed"))
	        .build();

	    String json = schema.toJSON();
	    out.println(json);
	    assertTrue(json.contains("\"enum\""));
	    assertTrue(json.contains("open"));
	}

	@Test
	void validJsonPasses() {
		log("Validate json test");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .required("name")
	        .build();
	    SchemaValidator validator = new SchemaValidator(schema);
	    ValidationResult result = validator.validate("{\"name\":\"Alice\"}");
	    assertTrue(result.isValid());
	}

	@Test
	void missingRequiredFails() {
		log("Missing required field test");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .required("name")
	        .build();
	    SchemaValidator validator = new SchemaValidator(schema);
	    ValidationResult result = validator.validate("{}");
	    assertFalse(result.isValid());
	    assertEquals(
	        FieldError.ErrorType.MISSING_REQUIRED,
	        result.errors().get(0).errorType()
	    );
	}


}
