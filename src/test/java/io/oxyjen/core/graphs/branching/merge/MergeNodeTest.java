package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.branching.MergeNode;

class MergeNodeTest {

	 private NodeContext context;

	 @BeforeEach
	 void setup() {
	     context = new NodeContext();
	 }

	 private MergeNode createNode() {
	     return new MergeNode.Builder()
	             .expect("A", "B", "C")
	             .timeout(1, TimeUnit.SECONDS)
	             .build("merge");
	 }
	 @Test
	 void shouldMergeAllSuccess() {
	        MergeNode node = createNode();
	        node.register(context);

	        node.contribute("A", "a", context);
	        node.contribute("B", "b", context);
	        node.contribute("C", "c", context);
	        MergeNode.MergeResult result =
	                (MergeNode.MergeResult) node.process(null, context);
	        assertEquals(3, result.getSuccess().size());
	        assertTrue(result.getErrors().isEmpty());
	        assertEquals("a", result.get("A"));
	 }
	 @Test
	 void shouldHandleMixedResults() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("A", "a", context);
	        node.contribute("B", new ParallelExecutor.NodeFailure("B", new RuntimeException("fail")), context);
	        node.contribute("C", "c", context);
	        MergeNode.MergeResult result =
	                (MergeNode.MergeResult) node.process(null, context);

	        assertEquals(2, result.getSuccess().size());
	        assertEquals(1, result.getErrors().size());
	        assertTrue(result.hasErrors());
	  }
	 
	 @Test
	 void shouldHandleAllFailures() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("A", new ParallelExecutor.NodeFailure("A", new RuntimeException()), context);
	        node.contribute("B", new ParallelExecutor.NodeFailure("B", new RuntimeException()), context);
	        node.contribute("C", new ParallelExecutor.NodeFailure("C", new RuntimeException()), context);
	        MergeNode.MergeResult result =
	                (MergeNode.MergeResult) node.process(null, context);
	        assertTrue(result.getSuccess().isEmpty());
	        assertEquals(3, result.getErrors().size());
	    }

	    @Test
	    void shouldThrowTimeoutIfMissing() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("A", "a", context);
	        MergeNode.MergeTimeoutException ex =
	                assertThrows(MergeNode.MergeTimeoutException.class,
	                        () -> node.process(null, context));
	        assertTrue(ex.getMissingContributors().contains("B"));
	        assertTrue(ex.getMissingContributors().contains("C"));
	    }
	    
	    @Test
	    void shouldIgnoreDuplicateContributions() {
	        MergeNode node = createNode();
	        node.register(context);

	        node.contribute("A", "a1", context);
	        node.contribute("A", "a2", context); // duplicate

	        node.contribute("B", "b", context);
	        node.contribute("C", "c", context);

	        MergeNode.MergeResult result =
	                (MergeNode.MergeResult) node.process(null, context);

	        assertEquals("a1", result.get("A")); // first wins
	    }

	    @Test
	    void shouldIgnoreUnexpectedContributor() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("X", "invalid", context); // ignored
	        node.contribute("A", "a", context);
	        node.contribute("B", "b", context);
	        node.contribute("C", "c", context);
	        MergeNode.MergeResult result =
	                (MergeNode.MergeResult) node.process(null, context);

	        assertEquals(3, result.getSuccess().size());
	    }
	    @Test
	    void shouldReturnMissingContributors() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("A", "a", context);
	        Set<String> missing = node.getMissingContributors(context);
	        assertTrue(missing.contains("B"));
	        assertTrue(missing.contains("C"));
	        assertFalse(missing.contains("A"));
	    }

	    @Test
	    void shouldReturnSnapshotOfContributions() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("A", "a", context);
	        MergeNode.MergeResult snapshot = node.getContributions(context);
	        assertEquals(1, snapshot.getSuccess().size());
	        assertTrue(snapshot.getErrors().isEmpty());
	    }
	    @Test
	    void shouldResetStateOnRegister() {
	        MergeNode node = createNode();
	        node.register(context);
	        node.contribute("A", "a", context);
	        node.register(context); // reset
	        assertTrue(node.getContributions(context).getSuccess().isEmpty());
	    }

	    @Test
	    void shouldRetrieveFromContext() {
	        MergeNode node = createNode();
	        node.register(context);
	        MergeNode fetched = MergeNode.fromContext("merge", context);
	        assertNotNull(fetched);
	        assertEquals("merge", fetched.getName());
	    }
}