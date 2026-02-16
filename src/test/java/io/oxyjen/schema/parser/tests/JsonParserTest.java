package io.oxyjen.schema.parser.tests;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.schema.JsonParser;

public class JsonParserTest {
	
	@Test
	void shouldParseSimpleString() {
	    Object result = JsonParser.parse("\"hello\"");
	    out.println(result);
	    assertEquals("hello", result);
	}
	@Test
	void shouldParseEscapedString() {
	    Object result = JsonParser.parse("\"line1\\nline2\"");
	    out.println(result);
	    assertEquals("line1\nline2", result);
	}
	@Test
	void shouldParseUnicodeString() {
	    Object result = JsonParser.parse("\"\\u0041\"");
	    out.println(result);
	    assertEquals("A", result);
	}
	@Test
	void shouldFailOnUnterminatedString() {
	    assertThrows(IllegalArgumentException.class,
	            () -> JsonParser.parse("\"hello"));
	}
	@Test
	void shouldParseInteger() {
	    Object result = JsonParser.parse("123");
	    out.println(result);
	    assertEquals(123L, result);
	}
	@Test
	void shouldParseNegativeInteger() {
	    Object result = JsonParser.parse("-42");
	    out.println(result);
	    assertEquals(-42L, result);
	}
	@Test
	void shouldParseDecimal() {
	    Object result = JsonParser.parse("3.14");
	    out.println(result);
	    assertEquals(new BigDecimal("3.14"), result);
	}
	@Test
	void shouldParseExponent() {
	    Object result = JsonParser.parse("1e3");
	    out.println(result);
	    assertEquals(new BigDecimal("1e3"), result);
	}
	@Test
	void shouldParseBigInteger() {
	    Object result = JsonParser.parse("9999999999999999999999999");
	    out.println(result);
	    assertTrue(result instanceof BigInteger);
	}
	@Test
	void shouldFailOnLeadingZero() {
	    assertThrows(IllegalArgumentException.class,
	            () -> JsonParser.parse("0123"));
	}
	@Test
	void shouldFailOnInvalidExponent() {
	    assertThrows(IllegalArgumentException.class,
	            () -> JsonParser.parse("1e"));
	}
	@Test
	void shouldParseTrueAndFalse() {
	    Object resultT = JsonParser.parse("true");
	    Object resultF = JsonParser.parse("false");
	    out.println(resultT + " : " + resultF);
	    assertEquals(true, resultT);
	    assertEquals(false, resultF);
	}
	@Test
	void shouldParseNull() {
	    Object result = JsonParser.parse("null");
	    out.println(result);
	    assertNull(result);
	}
	@Test
	void shouldFailOnInvalidLiteral() {
	    assertThrows(IllegalArgumentException.class,
	            () -> JsonParser.parse("tru"));
	}


}
