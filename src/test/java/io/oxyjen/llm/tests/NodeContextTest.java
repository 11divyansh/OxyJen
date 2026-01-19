package io.oxyjen.llm.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Memory;
import io.oxyjen.core.NodeContext;

public class NodeContextTest {

	 private void log(String title) {
	        System.out.println("\n==============================");
	        System.out.println(title);
	        System.out.println("==============================");
	    }

	    private void print(String label, Object value) {
	        System.out.println(label + " => " + value);
	    }
	    @Test
	    void sameMemoryNameReturnsSameInstance() {
	        log("NodeContext: same memory instance");

	        NodeContext ctx = new NodeContext();

	        Memory m1 = ctx.memory("chat");
	        Memory m2 = ctx.memory("chat");

	        print("m1 == m2", m1 == m2);

	        assertSame(m1, m2);
	    }
	    @Test
	    void differentMemoryNamesAreIsolated() {
	        log("NodeContext: memory isolation");

	        NodeContext ctx = new NodeContext();

	        Memory chat = ctx.memory("chat");
	        Memory system = ctx.memory("system");

	        chat.append("chat", "hello");
	        system.append("system", "boot");

	        print("chat.entries", chat.entries());
	        print("system.entries", system.entries());

	        assertEquals(1, chat.entries().size());
	        assertEquals(1, system.entries().size());
	        assertNotEquals(chat, system);
	    }


}
