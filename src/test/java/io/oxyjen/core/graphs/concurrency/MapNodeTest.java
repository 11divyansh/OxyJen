package io.oxyjen.core.graphs.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.graph.concurrency.MapNode;
import io.oxyjen.graph.concurrency.MapNode.MapResult;

class MapNodeTest {

	@Test
	void should_map_all_elements() {
	    MapNode<String, Integer> node = MapNode.<String, Integer>builder()
	            .mapWith(String::length)
	            .build("map");
	    MapResult<Integer> result =node.process(List.of("a", "bb", "ccc"),new NodeContext());
	    assertEquals(3, result.successCount());
	    assertEquals(List.of(1, 2, 3),result.toSuccessfulList());
	}
	
	@Test
	void should_preserve_input_order() {
	    MapNode<Integer, Integer> node = MapNode.<Integer, Integer>builder()
	            .mapWith(i -> i * 10)
	            .build("map");
	    MapResult<Integer> result = node.process(List.of(3, 1, 2),new NodeContext());
	    assertEquals(List.of(30, 10, 20),result.toSuccessfulList());
	}
	
	@Test
	void should_capture_failures_with_continue_on_error() {
	    MapNode<String, Integer> node = MapNode.<String, Integer>builder()
	            .mapWith(s -> {
	                if (s.equals("bad"))
	                    throw new RuntimeException();

	                return s.length();
	            })
	            .continueOnError()
	            .build("map");
	    MapResult<Integer> result = node.process(List.of("a", "bad", "ccc"), new NodeContext());
	    assertEquals(2, result.successCount());
	    assertEquals(1, result.errorCount());
	    assertTrue(result.failed(1));
	}
	
	@Test
	void should_fail_fast_by_default() {
	    MapNode<String, Integer> node = MapNode.<String, Integer>builder()
	            .mapWith(s -> {
	                if (s.equals("bad"))
	                    throw new RuntimeException("boom");
	                return s.length();
	            })
	            .build("map");
	    assertThrows(MapNode.MapElementException.class,
	        () -> node.process(
	            List.of("a", "bad", "ccc"),
	            new NodeContext()
	        )
	    );
	}
	
	@Test
	void should_handle_empty_input() {
	    MapNode<String, Integer> node = MapNode.<String, Integer>builder()
	            .mapWith(String::length)
	            .build("map");
	    MapResult<Integer> result = node.process(Collections.emptyList(),new NodeContext());
	    assertEquals(0, result.totalCount());
	}
}