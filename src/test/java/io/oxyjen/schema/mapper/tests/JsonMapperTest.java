package io.oxyjen.schema.mapper.tests;
import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.JsonMapper;

public class JsonMapperTest {

	record User(String name, int age, boolean active) {}
	@Test
	void shouldDeserializePrimitiveRecord() {
	    String json = """
	        {"name":"Divyansh","age":22,"active":true}
	    """;

	    User user = JsonMapper.deserialize(json, User.class);
	    out.println(user.name()+" "+user.age()+" "+user.active());
	    assertEquals("Divyansh", user.name());
	    assertEquals(22, user.age());
	    assertTrue(user.active());
	}
	@Test
	void shouldThrowWhenPrimitiveMissing() {
	    String json = """
	        {"name":"Divyansh"}
	    """;

	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, User.class));
	}
	
	record Data(Integer count, Boolean flag) {}
	@Test
	void shouldAllowNullWrapperTypes() {
	    String json = """
	        {"count":null,"flag":false}
	    """;
	  
	    Data data = JsonMapper.deserialize(json, Data.class);
	    out.println(data.count+" "+data.flag());
	    assertNull(data.count());
	    assertFalse(data.flag());
	}
	
	record Person(String name, Optional<Integer> age) {}
	@Test
	void shouldDeserializeOptionalPresent() {
	    String json = """
	        {"name":"A","age":30}
	    """;

	    Person p = JsonMapper.deserialize(json, Person.class);
	    out.println(p.name()+" "+p.age());
	    assertTrue(p.age().isPresent());
	    assertEquals(30, p.age().get());
	}
	@Test
	void shouldDeserializeOptionalMissing() {
	    String json = """
	        {"name":"A"}
	    """;

	    Person p = JsonMapper.deserialize(json, Person.class);
	    out.println(p.name()+" "+p.age());
	    assertTrue(p.age().isEmpty());
	}

	enum Role { ADMIN, USER }
	record Account(String username, Role role) {}
	@Test
	void shouldDeserializeEnum() {
	    String json = """
	        {"username":"div","role":"ADMIN"}
	    """;

	    Account account = JsonMapper.deserialize(json, Account.class);
	    out.println(account.username()+" "+account.role());
	    assertEquals(Role.ADMIN, account.role());
	}
	@Test
	void shouldThrowOnInvalidEnum() {
	    String json = """
	        {"username":"div","role":"INVALID"}
	    """;

	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, Account.class));
	}

	record Numbers(int[] values) {}
	@Test
	void shouldDeserializePrimitiveArray() {
	    String json = """
	        {"values":[1,2,3]}
	    """;

	    Numbers numbers = JsonMapper.deserialize(json, Numbers.class);
	    out.println(Arrays.toString(numbers.values()));
	    assertArrayEquals(new int[]{1,2,3}, numbers.values());
	}
	
	record Group(List<String> names) {}
	@Test
	void shouldDeserializeList() {
	    String json = """
	        {"names":["a","b","c"]}
	    """;

	    Group g = JsonMapper.deserialize(json, Group.class);
	    out.println(g.names());
	    assertEquals(List.of("a","b","c"), g.names());
	}

	record Scores(Map<String, Integer> scores) {}
	@Test
	void shouldDeserializeMap() {
	    String json = """
	        {"scores":{"math":90,"eng":80}}
	    """;

	    Scores s = JsonMapper.deserialize(json, Scores.class);
	    out.println(s.scores());
	    assertEquals(90, s.scores().get("math"));
	}

	record Address(String city, String country) {}
	record UserC(String name, Address address) {}
	@Test
	void shouldDeserializeNestedRecord() {
	    String json = """
	        {
	          "name":"Div",
	          "address":{"city":"Delhi","country":"India"}
	        }
	    """;

	    UserC user = JsonMapper.deserialize(json, UserC.class);
	    out.println(user.name+" "+user.address());
	    assertEquals("Delhi", user.address().city());
	}

	@Test
	void shouldThrowOnNullJson() {
	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(null, User.class));
	}
	@Test
	void shouldThrowOnEmptyJson() {
	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize("", User.class));
	}

	record Wrapper(List<Optional<String>> values) {}
	@Test
	void shouldDeserializeListOfOptionalString() {
	    String json = """
	        {"values":["a", null, "b"]}
	    """;

	    Wrapper w = JsonMapper.deserialize(json, Wrapper.class);
	    out.println(w.values());
	    assertEquals(3, w.values().size());
	    assertEquals(Optional.of("a"), w.values().get(0));
	    assertEquals(Optional.empty(), w.values().get(1));
	    assertEquals(Optional.of("b"), w.values().get(2));
	}
	
	record BadWrapper(List<Optional> values) {}
	@Test
	void shouldThrowForRawOptionalInList() {
	    String json = """
	        {"values":["a"]}
	    """;
	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, BadWrapper.class));
	}

	record Matrix(List<List<Integer>> values) {}
	@Test
	void shouldDeserializeNestedList() {
	    String json = """
	        {"values":[[1,2],[3,4]]}
	    """;

	    Matrix matrix = JsonMapper.deserialize(json, Matrix.class);
	    out.println(matrix.values());
	    assertEquals(List.of(
	        List.of(1,2),
	        List.of(3,4)
	    ), matrix.values());
	}
	@Test
	void shouldHandleEmptyNestedList() {
	    String json = """
	        {"values":[[],[]]}
	    """;

	    Matrix matrix = JsonMapper.deserialize(json, Matrix.class);
	    out.println(matrix.values());
	    assertEquals(2, matrix.values().size());
	}
	@Test
	void shouldThrowForInvalidNestedListType() {
	    String json = """
	        {"values":[[1,"a"]]}
	    """;

	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, Matrix.class));
	}

	record DataC(Map<String, List<Integer>> values) {}
	@Test
	void shouldDeserializeMapWithListValue() {
	    String json = """
	        {
	          "values": {
	            "a":[1,2],
	            "b":[3,4]
	          }
	        }
	    """;

	    DataC data = JsonMapper.deserialize(json, DataC.class);
	    out.println(data.values());
	    assertEquals(List.of(1,2), data.values().get("a"));
	    assertEquals(List.of(3,4), data.values().get("b"));
	}
	@Test
	void shouldHandleEmptyListInMap() {
	    String json = """
	        {
	          "values": {
	            "a":[]
	          }
	        }
	    """;

	    DataC data = JsonMapper.deserialize(json, DataC.class);
	    out.println(data.values());
	    assertTrue(data.values().get("a").isEmpty());
	}

	record Invalid(Map<Integer, List<Integer>> values) {}
	@Test
	void shouldThrowForNonStringKeyMap() {
	    String json = """
	        {"values":{"1":[1,2]}}
	    """;
	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, Invalid.class));
	}

	record UserR(String name, int age) {}
	record Container(Map<String, Optional<UserR>> users) {}
	@Test
	void shouldDeserializeMapWithOptionalRecord() {
	    String json = """
	        {
	          "users":{
	            "u1":{"name":"Div","age":22},
	            "u2":null
	          }
	        }
	    """;

	    Container container = JsonMapper.deserialize(json, Container.class);
	    out.println(container.users());
	    assertTrue(container.users().get("u1").isPresent());
	    assertEquals("Div", container.users().get("u1").get().name());
	    assertEquals(Optional.empty(), container.users().get("u2"));
	}
	@Test
	void shouldThrowIfInnerRecordInvalid() {
	    String json = """
	        {
	          "users":{
	            "u1":{"name":"Div"}
	          }
	        }
	    """;

	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, Container.class));
	}

	record Nested(Optional<Optional<String>> value) {}
	@Test
	void shouldDeserializeNestedOptional() {
	    String json = """
	        {"value":"hello"}
	    """;

	    Nested nested = JsonMapper.deserialize(json, Nested.class);
	    out.println(nested.value());
	    assertTrue(nested.value().isPresent());
	    assertTrue(nested.value().get().isPresent());
	    assertEquals("hello", nested.value().get().get());
	}

	record OptionalList(Optional<List<Integer>> values) {}
	@Test
	void shouldDeserializeOptionalList() {
	    String json = """
	        {"values":[1,2,3]}
	    """;

	    OptionalList result = JsonMapper.deserialize(json, OptionalList.class);
	    out.println(result.values());
	    assertTrue(result.values().isPresent());
	    assertEquals(List.of(1,2,3), result.values().get());
	}

	record OptionalMap(Optional<Map<String,Integer>> values) {}
	@Test
	void shouldDeserializeOptionalMap() {
	    String json = """
	        {"values":{"a":1}}
	    """;

	    OptionalMap result = JsonMapper.deserialize(json, OptionalMap.class);
	    out.println(result.values());
	    assertTrue(result.values().isPresent());
	    assertEquals(1, result.values().get().get("a"));
	}
	
	record Wild(Optional<? extends Number> value) {} //for future versions
	@Test
	void shouldThrowForWildcardOptional() {
	    String json = """
	        {"value":10}
	    """;

	    assertThrows(IllegalArgumentException.class,
	        () -> JsonMapper.deserialize(json, Wild.class));
	}

	@Test
	void shouldAllowNullList() {
	    record Wrapper(List<String> values) {}

	    String json = """
	        {"values":null}
	    """;

	    Wrapper w = JsonMapper.deserialize(json, Wrapper.class);
	    out.println(w.values());
	    assertNull(w.values());
	}

	@Test
	void shouldCoerceNumbersToCorrectTypes() {
		record Numeric(int i, long l, double d) {}
	    String json = """
	        {"i":1,"l":2,"d":3}
	        """;

	    Numeric result = JsonMapper.deserialize(json, Numeric.class);
	    out.println(result.i+" "+result.l+" "+result.d);
	    assertEquals(1, result.i());
	    assertEquals(2L, result.l());
	    assertEquals(3.0, result.d());
	}
	@Test
	void shouldDeserializeOptionalListMissing() {
		record OptionalList(Optional<List<String>> list) {}
	    String json = """
	        {}
	        """;

	    OptionalList result = JsonMapper.deserialize(json, OptionalList.class);
	    out.println(result.list());
	    assertTrue(result.list().isEmpty());
	}
	
	record Item(String name) {} 
	record Cart(Item[] items) {}
	@Test
	void shouldDeserializeArrayOfRecords() {
	    String json = """
	        {
	          "items":[
	            {"name":"a"},
	            {"name":"b"}
	          ]
	        }
	        """;

	    Cart cart = JsonMapper.deserialize(json, Cart.class);
	    out.println(Arrays.toString(cart.items()));
	    assertEquals(2, cart.items().length);
	    assertEquals("a", cart.items()[0].name());
	    assertEquals("b", cart.items()[1].name());
	}
	@Test
	void shouldFailOnUnknownField() {
	    String json = """
	        {"name":"A","age":10,"extra":"field"}
	        """;

	    assertThrows(IllegalArgumentException.class, () ->
	        JsonMapper.deserialize(json, Item.class)
	    );
	}
	@Test
	void shouldDeserializeDeepNestedGenerics() {
		record Complex(List<Map<String, List<Integer>>> data) {}
	    String json = """
	        {
	          "data":[
	            {"a":[1,2,3]}
	          ]
	        }
	        """;

	    Complex result = JsonMapper.deserialize(json, Complex.class);

	    List<Map<String, List<Integer>>> data = result.data();
	    out.println(data);
	    assertEquals(1, data.size());
	    assertEquals(List.of(1,2,3), data.get(0).get("a"));
	}

	record InvalidR(Map<Integer, String> data) {}
	@Test
	void shouldFailOnNonStringMapKey() {
	    String json = """
	        {"data":{"1":"value"}}
	        """;

	    assertThrows(IllegalArgumentException.class, () ->
	        JsonMapper.deserialize(json, InvalidR.class)
	    );
	}

	@Test
	void shouldDeserializeSetAndRemoveDuplicates() {
		record Tags(Set<String> tags) {}
	    String json = """
	        {"tags":["a","b","a"]}
	        """;

	    Tags result = JsonMapper.deserialize(json, Tags.class);
	    out.println(result.tags());
	    assertEquals(Set.of("a","b"), result.tags());
	}

	record Bad(List list) {}
	@Test
	void shouldFailOnRawList() {
	    String json = """
	        {"list":["a","b"]}
	        """;

	    assertThrows(IllegalArgumentException.class, () ->
	        JsonMapper.deserialize(json, Bad.class)
	    );
	}

	
	record Matrix2(int[][] grid) {}
	@Test
	void shouldDeserializeMultiDimensionalArray() {
	    String json = """
	        {
	          "grid":[
	            [1,2],
	            [3,4]
	          ]
	        }
	        """;

	    Matrix2 result = JsonMapper.deserialize(json, Matrix2.class);
	    out.println(result.grid());
	    assertEquals(2, result.grid().length);
	    assertArrayEquals(new int[]{1,2}, result.grid()[0]);
	    assertArrayEquals(new int[]{3,4}, result.grid()[1]);
	}


}
