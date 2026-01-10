package io.oxyjen.core.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Memory;
import io.oxyjen.core.Memory.MemoryEntry;
import io.oxyjen.core.NodeContext;

public class MemoryTest {

	 @Test
	    void shouldSupportMultipleIndependentMemoryScopes() {

	        System.out.println("=== STARTING Memory Integration Test ===");

	        // given
	        NodeContext context = new NodeContext();

	        // ---- Chat history ----
	        Memory chat = context.memory("chat");
	        chat.append("incoming", "User said: Hello");
	        chat.append("outgoing", "Bot said: Hi there!");

	        System.out.println("Chat memory entries added");

	        // ---- Session data ----
	        Memory session = context.memory("session");
	        Instant loginTime = Instant.now();
	        session.put("user_id", "user_123");
	        session.put("login_time", loginTime);

	        System.out.println("Session memory populated");

	        // ---- Metrics ----
	        Memory metrics = context.memory("metrics");
	        metrics.append("latency", 150);
	        metrics.append("latency", 200);
	        metrics.append("latency", 175);

	        System.out.println("Metrics memory populated");

	        // ---- Facts ----
	        Memory facts = context.memory("facts");
	        facts.put("user_name", "John");
	        facts.put("user_preferences", Map.of("theme", "dark"));

	        System.out.println("Facts memory populated");

	        // when
	        String userName = facts.get("user_name", String.class);
	        List<MemoryEntry> chatHistory = chat.entries();
	        List<MemoryEntry> recentMetrics = metrics.recent(10);

	        // then
	        System.out.println("\n--- FACTS ---");
	        System.out.println("User name: " + userName);

	        System.out.println("\n--- CHAT HISTORY ---");
	        chatHistory.forEach(entry ->
	            System.out.printf("[%s] %s%n", entry.type(), entry.value())
	        );

	        System.out.println("\n--- METRICS (RECENT) ---");
	        recentMetrics.forEach(entry ->
	            System.out.printf("Latency: %s ms%n", entry.value())
	        );

	        System.out.println("\n--- SESSION ---");
	        System.out.println("User ID: " + session.get("user_id"));
	        System.out.println("Login time: " + session.get("login_time"));

	        // assertions
	        assertEquals("John", userName);
	        assertEquals(2, chatHistory.size());
	        assertEquals(3, recentMetrics.size());

	        System.out.println("\n=== TEST PASSED SUCCESSFULLY ===");
	    }
	 
	 @Test
	 void memoryScopesShouldBeIsolated() {
	     NodeContext context = new NodeContext();

	     context.memory("a").put("key", "value");

	     assertNull(context.memory("b").get("key"));
	 }
	 
	 @Test
	 void recentShouldReturnOnlyLastNEntries() {
	     Memory memory = new NodeContext().memory("metrics");

	     for (int i = 0; i < 20; i++) {
	         memory.append("latency", i);
	     }

	     List<MemoryEntry> recent = memory.recent(5);
	     assertEquals(5, recent.size());
	     assertEquals(15, recent.get(0).value());
	 }


}
