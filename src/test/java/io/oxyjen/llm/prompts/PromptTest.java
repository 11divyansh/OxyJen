package io.oxyjen.llm.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.prompts.exception.TemplateException;

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
    	
    	assertThrows(TemplateException.class,()->{
    		template.render(new HashMap<>());
    	});
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
    
    @Test
    void testMultilineTemplateRendering() {

    	log("Multiline template test");
        PromptTemplate template = PromptTemplate.of(
            """
            You are a helpful assistant.

            User: {{user}}
            Question: {{question}}

            Please answer in {{language}}.
            """,
            Variable.required("user"),
            Variable.required("question"),
            Variable.optional("language", "English")
        );

        String result = template.render(
            "user", "Divyansh",
            "question", "What is Oxyjen?",
            "language", "English"
        );
        print("result",result);
        String expected = """
            You are a helpful assistant.

            User: Divyansh
            Question: What is Oxyjen?

            Please answer in English.
            """;

        assertEquals(expected, result);
    }
    @Test
    void testMissingOneRequiredVariable() {
        PromptTemplate template = PromptTemplate.of(
            "Hello {{name}} from {{city}}",
            Variable.required("name"),
            Variable.required("city")
        );

        assertThrows(TemplateException.class, () -> {
            template.render("name", "Divyansh");
        });
    }
    @Test
    void testExtraVariablesIgnored() {
        PromptTemplate template = PromptTemplate.of(
            "Hello {{name}}",
            Variable.required("name")
        );

        String result = template.render(
            "name", "Divyansh",
            "unused", "value"
        );
        print("result",result);
        assertEquals("Hello Divyansh", result);
    }
    @Test
    void testRepeatedVariable() {
        PromptTemplate template = PromptTemplate.of(
            "{{name}} is great. Yes, {{name}}!",
            Variable.required("name")
        );

        String result = template.render("name", "Oxyjen");
        print("result",result);
        assertEquals("Oxyjen is great. Yes, Oxyjen!", result);
    }

}
