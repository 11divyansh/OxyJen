package io.oxyjen.schema.serializer.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.JsonSerializer;
import io.oxyjen.llm.schema.JsonSerializer.JsonSerializationException;

public class JsonSerializerTest {

	 @Test
	    void shouldSerializePrimitiveTypes() {
	        assertEquals("hello", JsonSerializer.toJsonTree("hello"));
	        assertEquals(10, JsonSerializer.toJsonTree(10));
	        assertEquals(10L, JsonSerializer.toJsonTree(10L));
	        assertEquals(3.14, JsonSerializer.toJsonTree(3.14));
	        assertEquals(true, JsonSerializer.toJsonTree(true));
	        assertNull(JsonSerializer.toJsonTree(null));
	    }
	 
	    enum Status { ACTIVE, INACTIVE }
	    @Test
	    void shouldSerializeEnum() {
	        Object result = JsonSerializer.toJsonTree(Status.ACTIVE);
	        out.println(result);
	        assertEquals("ACTIVE", result);
	    }
	    @Test
	    void shouldSerializeOptionalPresent() {
	        Optional<String> opt = Optional.of("value");
	        out.println(opt);
	        assertEquals("value", JsonSerializer.toJsonTree(opt));
	    }
	    @Test
	    void shouldSerializeOptionalEmpty() {
	        Optional<String> opt = Optional.empty();
	        out.println(opt);
	        assertNull(JsonSerializer.toJsonTree(opt));
	    }
	    @Test
	    void shouldSerializeList() {
	        List<Integer> list = List.of(1, 2, 3);
	        Object result = JsonSerializer.toJsonTree(list);
	        out.println(result);
	        assertEquals(List.of(1, 2, 3), result);
	    }

	    @Test
	    void shouldSerializeSet() {
	        Set<String> set = new LinkedHashSet<>();
	        set.add("a");
	        set.add("b");
	        Object result = JsonSerializer.toJsonTree(set);
	        out.println(result);
	        assertEquals(List.of("a", "b"), result);
	    }
	    @Test
	    void shouldSerializeIntArray() {
	        int[] arr = {1, 2, 3};
	        Object result = JsonSerializer.toJsonTree(arr);
	        out.println(result);
	        assertEquals(List.of(1, 2, 3), result);
	    }

	    @Test
	    void shouldSerializeDoubleArray() {
	        double[] arr = {1.0, 2.0};
	        Object result = JsonSerializer.toJsonTree(arr);
	        out.println(result);
	        assertEquals(List.of(1.0, 2.0), result);
	    }
	    @Test
	    void shouldSerializeCharArrayAsList() {
	        char[] arr = {'a', 'b'};
	        Object result = JsonSerializer.toJsonTree(arr);
	        out.println(result);
	        assertEquals("ab", result);
	    }
	    @Test
	    void shouldSerializeObjectArray() {
	        String[] arr = {"a", "b"};
	        Object result = JsonSerializer.toJsonTree(arr);
	        out.println(result);
	        assertEquals(List.of("a", "b"), result);
	    }
	    @Test
	    void shouldSerializeMap() {
	        Map<String, Integer> map = new LinkedHashMap<>();
	        map.put("a", 1);
	        map.put("b", 2);
	        Object result = JsonSerializer.toJsonTree(map);
	        out.println(result);
	        Map<String, Object> expected = new LinkedHashMap<>();
	        expected.put("a", 1);
	        expected.put("b", 2);
	        assertEquals(expected, result);
	    }
	    @Test
	    void shouldFailOnNullMapKey() {
	        Map<String, String> map = new HashMap<>();
	        map.put(null, "value");
	        assertThrows(
	            JsonSerializer.JsonSerializationException.class,
	            () -> JsonSerializer.toJsonTree(map)
	        );
	    }
	    
	    record Person(String name, int age) {}
	    @Test
	    void shouldSerializeRecord() {
	        Person p = new Person("John", 30);
	        Object result = JsonSerializer.toJsonTree(p);
	        out.println("Record: "+result);
	        Map<String, Object> expected = new LinkedHashMap<>();
	        expected.put("name", "John");
	        expected.put("age", 30);
	        assertEquals(expected, result);
	    }
	    
	    static class User {
	        private String name;
	        private int age;
	        public String getName() { return name; }
	        public void setName(String name) { this.name = name; }
	        public int getAge() { return age; }
	        public void setAge(int age) { this.age = age; }
	    }
	    @Test
	    void shouldSerializePojo() {
	        User u = new User();
	        u.setName("Alice");
	        u.setAge(25);

	        Object result = JsonSerializer.toJsonTree(u);
	        out.println("Class: "+result);
	        Map<String, Object> expected = new LinkedHashMap<>();
	        expected.put("name", "Alice");
	        expected.put("age", 25);
	        assertEquals(expected, result);
	    }

	    record Order(String id, List<Person> people) {}
	    @Test
	    void shouldSerializeNestedStructures() {
	        Order order = new Order(
	            "ORD-1",
	            List.of(new Person("A", 1), new Person("B", 2))
	        );

	        Object result = JsonSerializer.toJsonTree(order);
	        out.println("Nested Structure: "+result);
	        Map<String, Object> map = (Map<String, Object>) result;
	        assertEquals("ORD-1", map.get("id"));
	        assertTrue(map.get("people") instanceof List);
	    }

	    @Test
	    void shouldSerializeDeepNestedStructure() {
	        Map<String, List<Integer>> inner = new LinkedHashMap<>();
	        inner.put("a", List.of(1,2,3));
	        List<Map<String, List<Integer>>> data = List.of(inner);
	        Object result = JsonSerializer.toJsonTree(data);
	        out.println("Nested generic: "+result);
	        assertTrue(result instanceof List);
	    }

	    static class Node {
	        Node next;
	    }
	    @Test
	    void shouldFailOnCyclicReference() {
	        Node n = new Node();
	        n.next = n;
	        assertThrows(
	            JsonSerializer.JsonSerializationException.class,
	            () -> JsonSerializer.toJsonTree(n)
	        );
	    }
}
