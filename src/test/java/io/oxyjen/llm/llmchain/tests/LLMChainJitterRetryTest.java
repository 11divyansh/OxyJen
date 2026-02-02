package io.oxyjen.llm.llmchain.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.LLMChain;

public class LLMChainJitterRetryTest {
	
	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }
	private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
		
	@Test
	void exponentialBackoffIncreases()throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		log("Exponential Backoff Increases");
	    LLMChain chain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(5)
	        .exponentialBackoff()
	        .build();
	    Method method = LLMChain.class.getDeclaredMethod("calculateBackoff", int.class);
	    method.setAccessible(true);
	    Long d1 =(Long)method.invoke(chain, 1);
	    Long d2 =(Long)method.invoke(chain, 2);
	    Long d3 =(Long)method.invoke(chain, 3);
	    print("d1",d1);
	    print("d2",d2);
	    print("d3",d3);
	    
//	    Duration d1 = chain.calculateBackoff(1);
//	    Duration d2 = chain.calculateBackoff(2);
//	    Duration d3 = chain.calculateBackoff(3);

	    assertTrue(d2.compareTo(d1) > 0);
	    assertTrue(d3.compareTo(d2) > 0);
	}


}
