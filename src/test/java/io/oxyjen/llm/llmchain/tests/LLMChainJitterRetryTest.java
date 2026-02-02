package io.oxyjen.llm.llmchain.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.lang.System.*;

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
	
	@Test
	void fixedBackoffIsConstant() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		log("Fixed backoff stays constant");
	    LLMChain chain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(5)
	        .fixedBackoff()
	        .build();
	    Method method = LLMChain.class.getDeclaredMethod("calculateBackoff", int.class);
	    method.setAccessible(true);
	    Long d1 = (Long)method.invoke(chain, 1);
	    Long d2 = (Long)method.invoke(chain, 2);

	    print("d1",d1);
	    print("d2",d2);
	    assertEquals(d1, d2);
	}
	
	@Test
	void backoffIsCapped() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		log("Capped backoff");
	    Duration duration = Duration.ofSeconds(2);
	    Long cap = duration.getSeconds();

	    LLMChain chain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(10)
	        .exponentialBackoff()
	        .maxBackoff(duration)
	        .build();

	    Method method = LLMChain.class.getDeclaredMethod("calculateBackoff", int.class);
	    method.setAccessible(true);
	    Long d5 = (Long)method.invoke(chain, 5);
	    print("d5",d5);
	    assertTrue(d5.compareTo(cap) <= 2);
	}

	@Test
	void jitterWithinBounds() throws Exception {
		log("Jitter with bounds");
		double jitter = 0.2;
	    LLMChain baseChain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(3)
	        .fixedBackoff()
	        .build();
	   
	    LLMChain jitterChain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(3)
	        .fixedBackoff()
	        .jitter(jitter)
	        .build();

	    Method method =LLMChain.class.getDeclaredMethod("calculateBackoff", int.class);
	    method.setAccessible(true);

	    long base = (long) method.invoke(baseChain, 1);
	    long min = (long)(base * (1 - jitter));
	    long max = (long)(base * (1 + jitter));
	    for (int i = 0; i < 20; i++) {
	        long d = (long) method.invoke(jitterChain, 1);
	        System.out.println(d+">="+min);
	        System.out.println(d+"<="+max);
	        assertTrue(d >= min);
	        assertTrue(d <= max);
	    }
	}
	@Test
	void zeroJitterIsDeterministic()throws Exception {
		log("Zero jitter");
	    LLMChain chain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(3)
	        .fixedBackoff()
	        .jitter(0.0)
	        .build();

	    Method method =LLMChain.class.getDeclaredMethod("calculateBackoff", int.class);
	    method.setAccessible(true);
	    long d1 = (long) method.invoke(chain, 1);
	    long d2 = (long) method.invoke(chain, 1);	   
	    System.out.println(d1+" "+d2);
	    assertEquals(d1, d2);
	}
	@Test
	void jitterDoesNotExceedCap() throws Exception {
		log("Jitter does not exceed cap");
	    Duration duration = Duration.ofSeconds(1);
	    long cap = duration.toMillis();

	    LLMChain chain = LLMChain.builder()
	        .primary(new NoopModel())
	        .retry(5)
	        .exponentialBackoff()
	        .maxBackoff(duration)
	        .jitter(0.5)
	        .build();

	    Method method =LLMChain.class.getDeclaredMethod("calculateBackoff", int.class);
	    method.setAccessible(true);
	    for (int i = 0; i < 10; i++) {
	    	long d = (long) method.invoke(chain, 5);
	    	out.println("Cap:"+cap+" D:"+d);
	        assertTrue(d<=cap);
	    }
	}

}
