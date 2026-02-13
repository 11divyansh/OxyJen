package io.oxyjen.schema.generator.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.annotations.Description;
import io.oxyjen.llm.schema.annotations.JsonIgnore;
import io.oxyjen.llm.schema.annotations.Max;
import io.oxyjen.llm.schema.annotations.Min;
import io.oxyjen.llm.schema.annotations.Pattern;
import io.oxyjen.llm.schema.annotations.Size;

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
	@Test
	void primitiveArrayGeneratesArraySchema() {
		log("Primitive array generates array schema");
	    record Test(int[] scores) {}

	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"type\":\"array\""));
	    assertTrue(json.contains("\"type\":\"number\""));
	}
	@Test
	void mapGeneratesAdditionalProperties() {
		log("Map generates additional properties");
	    record Test(Map<String, Integer> prices) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"additionalProperties\""));
	    assertTrue(json.contains("\"type\":\"number\""));
	}
	@Test
	void nestedMapGeneratesNestedSchemas() {
		log("Nested map generates nested schema");
	    record Test(Map<String, List<String>> data) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"additionalProperties\""));
	    assertTrue(json.contains("\"type\":\"array\""));
	}
	@Test
	void mapWithNonStringKeyThrows() {
	    record Test(Map<Integer, String> invalid) {}
	    assertThrows(
	        IllegalArgumentException.class,
	        () -> SchemaGenerator.fromClass(Test.class)
	    );
	}
	@Test
	void nestedRecordGeneratesNestedObjectSchemaTest() {
		log("Nested record generates nested object schema");
		record Address(String city) {}
		record User(String name, Address address) {}
		JSONSchema schema = SchemaGenerator.fromClass(User.class);
		String json = schema.toJSON();
		print("json",json);
		assertTrue(json.contains("\"address\""));
	    assertTrue(json.contains("\"type\":\"object\""));
	    assertTrue(json.contains("\"city\""));
	}
	@Test
	void pojoWithGettersGeneratesShemaTest() {
		log("Pojo with getter generates schema");
		 class User {
		        public String getName() { return "x"; }
		        public int getAge() { return 1; }
		 }
		 JSONSchema schema = SchemaGenerator.fromClass(User.class);
		 String json = schema.toJSON();
		 print("json",json);
		 assertTrue(json.contains("\"name\""));
		 assertTrue(json.contains("\"age\""));
	}
	@Test
	void jsonIgnoreOnRecordField() {
		log("@JsonIgnore on field test");
	    record Test(String name, @JsonIgnore String secret) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"name\""));
	    assertFalse(json.contains("secret"));
	}
	@Test
	void jsonIgnoreOnPojoGetter() {
		log("@JsonIgnore on pojo getter");
	    class User {
	        public String getName() { return "x"; }
	        @JsonIgnore
	        public String getSecret() { return "secret"; }
	    }
	    JSONSchema schema = SchemaGenerator.fromClass(User.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"name\""));
	    assertFalse(json.contains("secret"));
	}
	@Test
	void sizeAnnotationAppliedToString() {
		log("Size annotation test");
	    record Test(@Size(min=2, max=5) String name) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"minLength\":2"));
	    assertTrue(json.contains("\"maxLength\":5"));
	}
	@Test
	void patternAnnotationApplied() {
		log("Pattern annotation test");
	    record Test(@Pattern("\\d+") String code) {}
	    JSONSchema schema = SchemaGenerator.fromClass(Test.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"pattern\""));
	}
	@Test
	void minMaxAppliedToNumberTest() {
		log("MIN/MAX applied to number test");
		record Test(@Min(1) @Max(10) int count) {}
		JSONSchema schema = SchemaGenerator.fromClass(Test.class);
		String json = schema.toJSON();
		print("json",json);
		assertTrue(json.contains("\"minimum\":1"));
	    assertTrue(json.contains("\"maximum\":10"));
	}
	@Test
	void circularReferenceThrows() {
	    record Node(Node parent) {}
	    assertThrows(
	        IllegalArgumentException.class,
	        () -> SchemaGenerator.fromClass(Node.class)
	    );
	}
	@Test
	void rawListThrows() {
	    record Test(List raw) {}
	    assertThrows(
	        IllegalArgumentException.class,
	        () -> SchemaGenerator.fromClass(Test.class)
	    );
	}
	@Test
	void rawMapThrows() {
	    record Test(Map raw) {}
	    assertThrows(
	        IllegalArgumentException.class,
	        () -> SchemaGenerator.fromClass(Test.class)
	    );
	}
	@Test
	void emptyPojoGeneratesEmptyObjectSchema() {
		log("Empty pojo generates empty object schema");
	    class Empty {}
	    JSONSchema schema = SchemaGenerator.fromClass(Empty.class);
	    String json = schema.toJSON();
	    print("json",json);
	    assertTrue(json.contains("\"type\":\"object\""));
	}

	
}