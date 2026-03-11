package io.oxyjen.llm.schema.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Executor;
import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.ChatModel;
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
	@Test
	void schemaNode_handlesCollection() {
		log("SchemaNode handles list");
	    JSONSchema schema = JSONSchema.object()
	        .property("tags",
	            PropertySchema.array(
	            PropertySchema.string("tag")))
	        .required("tags")
	        .build();
	    SchemaNode<Map> node =
	        SchemaNode.builder(Map.class)
	            .model(new NewFakeModel("{\"tags\":[\"a\",\"b\"]}"))
	            .schema(schema)
	            .build();

	    Map t = node.process("input", new NodeContext());
	    out.println(t);
	    assertTrue(t instanceof Map);
	    //assertEquals(2, t.size());
	}
	@Test
	void schemaNode_handlesOptional() {
		log("Schema node handles optional");
	    record User(String name, Optional<String> nickname){}
	    JSONSchema schema = JSONSchema.object()
	        .property("name", JSONSchema.PropertySchema.string("name"))
	        .property("nickname", JSONSchema.PropertySchema.string("nick"))
	        .required("name")
	        .build();
	    SchemaNode<User> node =
	        SchemaNode.builder(User.class)
	            .model(new NewFakeModel("{\"name\":\"John\"}"))
	            .schema(schema)
	            .build();
	    User u = node.process("input", new NodeContext());
	    out.println(u);
	    assertTrue(u.nickname().isEmpty());
	}
	
	@Test
	void schemaNode_handlesEnum() {
		log("Schema node handles enum");
		enum Priority{low, high}
		record Ticket(Priority priority) {}
		JSONSchema schema = JSONSchema.object()
				.property("priority", PropertySchema.enumOf("p","low","high"))
				.required("priority")
				.build();
		SchemaNode<Ticket> node = SchemaNode.builder(Ticket.class)
				.model(new NewFakeModel("{\"priority\":\"low\"}"))
				.schema(schema)
				.build();
		Ticket t = node.process("input", new NodeContext());
		out.println(t);
		assertEquals(Priority.low, t.priority());
	}
	
	@Test
	void schemaNode_retriesUntilValid() {
		log("Schemanode retries until valid");
		record Ticket(String name) {}
		ChatModel model = new ChatModel() {
			int call = 0 ;
			@Override
			public String chat(String input) {
				call++;
				return call == 4 ? "{\"name\":\"ok\"}" : "{}";
			}
		};
		JSONSchema schema = JSONSchema.object()
				.property("name", PropertySchema.string("name"))
				.required("name")
				.build();
		SchemaNode<Ticket> node = SchemaNode.builder(Ticket.class)
				.model(model)
				.schema(schema)
				.maxRetries(4)
				.build();
		Ticket t = node.process("input", new NodeContext());
		out.println(t);
		assertEquals("ok",t.name());
	}
	@Test
	void schemaNode_storesMemory() {
		log("SchemaNode stores memory");
	    record Ticket(String name){}
	    NodeContext ctx = new NodeContext();
	    JSONSchema schema = JSONSchema.object()
	        .property("name", JSONSchema.PropertySchema.string("name"))
	        .required("name")
	        .build();

	    SchemaNode<Ticket> node =
	        SchemaNode.<Ticket>builder(Ticket.class)
	            .model(new FakeModel("{\"name\":\"John\"}"))
	            .schema(schema)
	            .memory("test")
	            .build();
	    node.process("hello", ctx);
	    out.println(ctx.memory("test").entries());
	    assertFalse(ctx.memory("test").entries().isEmpty());
	}
	@Test
	void schemaNode_worksInsideGraph() {
		log("SchemaNode works inside graph");
	    record Ticket(String name){}
	    JSONSchema schema = JSONSchema.object()
	        .property("name", JSONSchema.PropertySchema.string("name"))
	        .required("name")
	        .build();
	    SchemaNode<Ticket> node =
	        SchemaNode.<Ticket>builder(Ticket.class)
	            .model(new FakeModel("{\"name\":\"John\"}"))
	            .schema(schema)
	            .build();
	    Graph graph = GraphBuilder.named("test")
	        .addNode(node)
	        .build();
	    Executor executor = new Executor();
	    Object result = executor.run(graph,"input",new NodeContext());
	    out.println(result);
	    assertTrue(result instanceof Ticket);
	}
	@Test
	void schemaNode_throwsOnInvalidOutput() {
	    record Ticket(String name){}
	    JSONSchema schema = JSONSchema.object()
	        .property("name", JSONSchema.PropertySchema.string("name"))
	        .required("name")
	        .build();
	    SchemaNode<Ticket> node =
	        SchemaNode.builder(Ticket.class)
	            .model(new FakeModel("{}"))
	            .schema(schema)
	            .maxRetries(1)
	            .build();
	    assertThrows(Exception.class, () ->
	        node.process("input", new NodeContext())
	    );
	}
}