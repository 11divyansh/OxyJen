package io.oxyjen.llm.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.InMemoryMemory;
import io.oxyjen.core.Memory;

public class MemoryTest {

	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
	@Test
	void appendStoresEntriesInOrder() {
		//append() stores memory in order
	    log("Memory.append stores entries in order");

	    Memory memory = new InMemoryMemory("test");
	    memory.append("chat", "A");
        memory.append("chat", "B");
        memory.append("chat", "C");

        List<Memory.MemoryEntry> entries = memory.entries();

        print("entries", entries);

        assertEquals(3, entries.size());
        assertEquals("A", entries.get(0).value());
        assertEquals("B", entries.get(1).value());
        assertEquals("C", entries.get(2).value());
	}
	
    @Test
    void putAndGetStoresKeyValueData() {
    	//put()+get()(raw)
        log("Memory.put / get");

        Memory memory = new InMemoryMemory("test");

        memory.put("count", 42);
        memory.put("enabled", true);

        Object count = memory.get("count");
        Object enabled = memory.get("enabled");

        print("count", count);
        print("enabled", enabled);

        assertEquals(42, count);
        assertEquals(true, enabled);
    }

    @Test
    void getWithTypeReturnsCorrectType() {
    	//get(key,type)returns correct type
        log("Memory.get(key, type)");

        Memory memory = new InMemoryMemory("test");

        memory.put("count", 42);

        Integer count = memory.get("count", Integer.class);

        print("count (Integer)", count);

        assertEquals(42, count);
    }

    @Test
    void getWithWrongTypeThrowsException() {
    	//get(key,type)throws for wrong type
        log("Memory.get(key, wrong type)");

        Memory memory = new InMemoryMemory("test");
        memory.put("count", 42);

        print("stored value", memory.get("count"));

        assertThrows(ClassCastException.class, () ->
            memory.get("count", String.class)
        );
    }

    @Test
    void recentReturnsLastNEntries() {
        log("Memory.recent(n)");

        Memory memory = new InMemoryMemory("test");

        memory.append("chat", "A");
        memory.append("chat", "B");
        memory.append("chat", "C");

        List<Memory.MemoryEntry> recent = memory.recent(2);

        print("recent(2)", recent);

        assertEquals(2, recent.size());
        assertEquals("B", recent.get(0).value());
        assertEquals("C", recent.get(1).value());
    }


}
