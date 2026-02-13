package io.oxyjen.schema.generator.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.annotations.Description;

public class SchemaGeneratorTest {
	
	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
	@Test
	void generatesStringProperty() {
		log("generate String property");
	    record Test(String name) {}

	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"name\""));
	    assertTrue(json.contains("\"type\":\"string\""));
	    assertTrue(json.contains("\"required\""));
	}
	@Test
	void generatesNumberAndBooleanProperties() {
		log("Generate Number and boolean property");
	    record Test(@Description("Counte variable")int count, boolean active) {}

	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"count\""));
	    assertTrue(json.contains("\"type\":\"number\""));

	    assertTrue(json.contains("\"active\""));
	    assertTrue(json.contains("\"type\":\"boolean\""));
	}
	enum Priority {
	    low, medium, high
	}

	@Test
	void generatesEnumProperty() {
		log("Generates enum property");
	    record Test(Priority priority) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"enum\""));
	    assertTrue(json.contains("low"));
	    assertTrue(json.contains("medium"));
	    assertTrue(json.contains("high"));
	}
	@Test
	void optionalFieldIsNotRequired() {
		log("Optional field not required");
	    record Test(String name, Optional<String> nickname) {}

	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"nickname\""));
	    assertFalse(json.contains("\"nickname\",\""));
	}
	@Test
	void listGeneratesArraySchema() {
		log("List generates array schema");
	    record Test(List<String> tags) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"type\":\"array\""));
	    assertTrue(json.contains("\"items\""));
	}
	@Test
	void nestedListGeneratesNestedArraySchema() {
		log("Nested list generates nested array schema");
	    record Test(List<List<String>> matrix) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"type\":\"array\""));
	    assertTrue(json.contains("\"items\":{\"type\":\"array\""));
	}


}
