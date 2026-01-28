package io.oxyjen.llm.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PromptTest {
	
	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void testBasicRendering() {
    	log("Testing basic rendering");
        PromptTemplate template = PromptTemplate.of(
            "Hello {{name}}, you are {{age}} years old"
        );
        
        String result = template.render(
            "name", "Alice",
            "age", 25
        );
        print("result",result);
        
        assertEquals("Hello Alice, you are 25 years old", result);
    }
    
    @Test
    void testRequiredVariables() {
    	log("Testing Required variables");
    	PromptTemplate template = PromptTemplate.of(
    		"hello {{user}}",
    		Variable.required("user")
    	);
    	String result = template.render(
    			"user","Divyansh"
    	);
    	print("result",result);
    	assertEquals("hello Divyansh", result);
    }
    
    @Test
    void testOptionalVariables() {
    	log("Testing Required Variables");
    	PromptTemplate template = PromptTemplate.of(
    			"Hello {{name}}, role:{{role}}",
    			Variable.required("name"),
    			Variable.optional("role", "guest")	
    	);
    	String result = template.render("name","Divyansh");
    	print("result",result);
    	assertEquals("Hello Divyansh, role:guest",result);
    }

}
