package io.oxyjen.llm.schema.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.llm.schema.SchemaNode;

public class SchemaNodeTests {

	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }
	@Test
	void schemaNode_returnsTypedRecord() {
		log("Schema node returns typed record");
	    record Ticket(String name, int age){}
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("name"))
	        .property("age", PropertySchema.number("age"))
	        .required("name","age")
	        .build();
	    SchemaNode<Ticket> node =
	        SchemaNode.builder(Ticket.class)
	            .model(new FakeModel("{\"name\":\"John\",\"age\":30}"))
	            .schema(schema)
	            .build();
	    Ticket t = node.process("input", new NodeContext());
	    out.println(t);
	    assertEquals("John", t.name());
	    assertEquals(30, t.age());
	}
	@Test
	void schemaNode_handlesNestedObjects() {
		log("Schema node returns nested objects");
	    record Address(String city){}
	    record User(String name, Address address){}
	    JSONSchema address = JSONSchema.object()
	        .property("city", PropertySchema.string("city"))
	        .required("city")
	        .build();
	    JSONSchema schema = JSONSchema.object()
	        .property("name", PropertySchema.string("name"))
	        .property("address",
	            PropertySchema.object("address", address))
	        .required("name","address")
	        .build();
	    SchemaNode<User> node =
	        SchemaNode.<User>builder(User.class)
	            .model(new FakeModel(
	                "{\"name\":\"John\",\"address\":{\"city\":\"Delhi\"}}"))
	            .schema(schema)
	            .build();
	    User u = node.process("input", new NodeContext());
	    out.println(u);
	    assertEquals("Delhi", u.address().city());
	}
	@Test
	void schemaNode_handlesCollections() {
		log("SchemaNode handles collections");
	    record Tags(List<String> tags){}
	    JSONSchema schema = JSONSchema.object()
	        .property("tags",
	            JSONSchema.PropertySchema.array(
	                JSONSchema.PropertySchema.string("tag")
	            ))
	        .required("tags")
	        .build();
	    SchemaNode<Tags> node =
	        SchemaNode.builder(Tags.class)
	            .model(new FakeModel("{\"tags\":[\"a\",\"b\"]}"))
	            .schema(schema)
	            .build();

	    Tags t = node.process("input", new NodeContext());
	    out.println(t);
	    assertEquals(2, t.tags().size());
	}
}
