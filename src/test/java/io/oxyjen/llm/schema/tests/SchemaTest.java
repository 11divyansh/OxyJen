package io.oxyjen.llm.schema.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;

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


}
