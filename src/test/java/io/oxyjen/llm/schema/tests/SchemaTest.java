package io.oxyjen.llm.schema.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.schema.FieldError;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.llm.schema.SchemaEnforcer;
import io.oxyjen.llm.schema.SchemaException;
import io.oxyjen.llm.schema.SchemaNode;
import io.oxyjen.llm.schema.SchemaResult;
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
	@Test
	void wrongTypeDetected() {
		log("Wrong type detected");
	    JSONSchema schema = JSONSchema.object()
	        .property("age", PropertySchema.number("Age"))
	        .build();
	    SchemaValidator validator = new SchemaValidator(schema);
	    ValidationResult result = validator.validate("{\"age\":\"thirty\"}");
	    out.println(result.errors().get(0).errorType());
	    assertFalse(result.isValid());
	    assertEquals(
	        FieldError.ErrorType.WRONG_TYPE,
	        result.errors().get(0).errorType()
	    );
	}
	@Test
	void enumValidationFails() {
		log("enum validation fails");
	    JSONSchema schema = JSONSchema.object()
	        .property("status",PropertySchema.enumOf("Status","open","closed"))
	        .required("status")
	        .build();
	    SchemaValidator validator = new SchemaValidator(schema);
	    ValidationResult result = validator.validate("{\"status\":\"pending\"}");
	    out.println(result.errors().get(0).errorType());
	    assertFalse(result.isValid());
	    assertEquals(
	        FieldError.ErrorType.INVALID_ENUM_VALUE,
	        result.errors().get(0).errorType()
	    );
	}
	@Test
	void invalidJsonDetected() {
		log("Invalid json test");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .build();
	    SchemaValidator validator = new SchemaValidator(schema);
	    ValidationResult result = validator.validate("{name:Alice}");
	    out.println(result.errors().get(0).errorType());
	    assertFalse(result.isValid());
	    assertEquals(
	        FieldError.ErrorType.PARSE_ERROR,
	        result.errors().get(0).errorType()
	    );
	}

	@Test
	void formatErrorsWorks() {
		log("Format errors test");
	    FieldError err =
	        new FieldError("$.name",
	            FieldError.ErrorType.MISSING_REQUIRED,
	            "present",
	            null,
	            "Missing");
	    ValidationResult vr = new ValidationResult(false, List.of(err));
	    assertTrue(vr.formatErrors().contains("name"));
	}

	@Test
	void enforcerRetriesThenSucceeds() {
		log("Enforcer success on retry");
	    ChatModel model = new FakeModel(
	        "bad json",
	        "{\"name\":\"Alice\"}"
	    );
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .required("name")
	        .build();
	    SchemaEnforcer enforcer = new SchemaEnforcer(model, schema, 2);
	    SchemaResult result = enforcer.execute("prompt");
	    out.println(result.getRawJson());
	    assertTrue(result.getRawJson().contains("Alice"));
	}

	@Test
	void enforcerFailsAfterMaxRetries() {
		log("Enforcer fails on max retries");
	    ChatModel model = new FakeModel("bad","bad","bad");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .required("name")
	        .build();
	    SchemaEnforcer enforcer = new SchemaEnforcer(model, schema, 2);
	    try {
	    	enforcer.execute("prompt");
	    }catch(SchemaException e) {
	    	out.println(e.getMessage());
	    }
	    assertThrows(SchemaException.class,
	        () -> enforcer.execute("prompt"));
	}

	@Test
	void schemaNodeMapsResultToRecord() {
		log("SchemaNode maps result to record");
		record User(String name) {}
	    ChatModel model = new FakeModel("{\"name\":\"Bob\"}");
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("Name"))
	        .required("name")
	        .build();
	    SchemaNode<User> node = SchemaNode.builder(User.class)
	        .model(model)
	        .schema(schema)
	        .build();
	    User result = node.process("prompt", new NodeContext());
	    out.println(result);
	    assertEquals("Bob", result.name());
	}
	
	@Test
	void httpToolSchemaTest() {
		log("Http tool schema test");
		JSONSchema schemaI = JSONSchema.object()
			        .property("method",
			            PropertySchema.enumOf(
			                "HTTP method",
			                "GET", "POST", "PUT", "DELETE", "PATCH"
			            ).build())
			        .property("url",
			            PropertySchema.string("Full URL to request").build())
			        .property("headers",
			            PropertySchema.map(
			                "Optional HTTP headers",
			                PropertySchema.string("header value")
			            ).build())
			        .property("query",
			                PropertySchema.map(
			                    "Query parameters",
			                    PropertySchema.string("parameter value")
			                ).build())
			        .property("body",
			            PropertySchema.string("Optional request body").build())
			        .required("method", "url")
			        .build();
		JSONSchema schemaO = JSONSchema.object()
		        .property("status",
			            PropertySchema.number("HTTP status code").minimum(100)
			            .maximum(599).build())
			        .property("headers",
			            PropertySchema.map(
			                "Response headers",
			                PropertySchema.string("header value")
			            ).build())
			        .property("body",
			            PropertySchema.string("Response body").build())
			        .required("status")
			        .build();
		out.println(schemaI.toJSON());
		out.println(schemaO.toJSON());
	}
}
