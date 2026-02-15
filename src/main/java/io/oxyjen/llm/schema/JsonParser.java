package io.oxyjen.llm.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight JSON parser.
 * 
 * Parses JSON strings into Java objects:
 * - JSON object -> Map<String, Object>
 * - JSON array -> List<Object>
 * - JSON string -> String
 * - JSON number -> Double/Long
 * - JSON boolean -> Boolean
 * - JSON null -> null
 * 
 * Full JSON support for deserialization
 */
public final class JsonParser {

	private final String json;
    private int pos = 0;
    
    private JsonParser(String json) {
        this.json = json.trim();
    }
    /**
     * Parse JSON string to Object tree
     * 
     * @param json JSON string
     * @return parsed object (Map, List, String, Number, Boolean, or null)
     */
    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON cannot be null or empty");
        }
        
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        
        if (parser.pos < parser.json.length()) {
            throw new IllegalArgumentException(
                "Unexpected characters after JSON: " + 
                parser.json.substring(parser.pos)
            );
        }
        
        return result;
    }
    //parse a JSON value
    private Object parseValue() {
        skipWhitespace();
        
        if (pos >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        char c = json.charAt(pos);
        return switch (c) {
        	case '{' -> parseObject();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }
   
    private void skipWhitespace() {
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }
    private Map<String, Object> parseObject() {
    	Map<String, Object> map = new LinkedHashMap<>();
    	
    	consume('{');
    	skipWhitespace();
    	if(peek() == '}') {
    		consume('}');
    		return map;
    	}
    	
    	while (true) {
    		skipWhitespace();
    		
    		String key = parseString();
    		skipWhitespace();
    		consume(':');
    		skipWhitespace();
    		Object value = parseValue();
    		
    		map.put(key, value);
    		skipWhitespace();
    		if(peek() == '}') {
    			consume('}');
    			break;
    		}
    		consume(',');
    	}
    	return map;	
    }
    private String parseString() {
        consume('"');
        
        StringBuilder sb = new StringBuilder();       
        while (pos < json.length()) {
            char c = json.charAt(pos++);            
            if (c == '"') {
                return sb.toString();
            }            
            if (c == '\\') {
                if (pos >= json.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence");
                }
                
                char escaped = json.charAt(pos++);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        // unicode escape: \\uXXXX
                        if (pos + 4 > json.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = json.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new IllegalArgumentException(
                        "Invalid escape sequence: \\" + escaped
                    );
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }
    private Number parseNumber() {
    	int start = pos;
    	if(peek() == '-') {
    		pos++;
    	}
    	
    	if (!Character.isDigit(peek())) {
    		throw new IllegalArgumentException(
    				"Expected digit, got: " + peek());
    	}
    	
    	while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
    		pos++;
    	}
    	boolean isDecimal = false;
    	if (pos < json.length() && json.charAt(pos) == '.') {
    		isDecimal = true;
    		pos++;
    		if (pos >= json.length() || !Character.isDigit(json.charAt(pos))) {
    			throw new IllegalArgumentException("Expected digit after decimal point");
    		}
    		while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
    			pos++;
    		}
    	}
    	if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
    		isDecimal = true;
    		pos++;
    		if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
    			pos++;
    		}
    		if( pos >= json.length() || !Character.isDigit(json.charAt(pos))) {
    			throw new IllegalArgumentException("Expected digit in exponent");
    		}
    		while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
    			pos++;
    		}
    	}
    	
    	String numberStr = json.substring(start, pos);
    	if (isDecimal) {
    		return Double.parseDouble(numberStr);
    	} else {
    		return Long.parseLong(numberStr);
    	}
    }
    
   private Boolean parseBoolean() {
	   if (json.startsWith("true", pos)) {
		   pos += 4;
		   return true;
	   }
	   if (json.startsWith("false", pos)) {
		   pos += 5;
		   return false;
	   }
	   throw new IllegalArgumentException("Expected 'true' or 'false'");
   }
   private Object parseNull() {
	   if (json.startsWith("null", pos)) {
		   pos += 4;
		   return null;
	   }
	   throw new IllegalArgumentException("Expected 'null'");
   }
    // peek at current char without consuming
    private char peek() {
        if (pos >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        return json.charAt(pos);
    }
    // consume expected char
    private void consume(char expected) {
        if (pos >= json.length()) {
            throw new IllegalArgumentException(
                "Expected '" + expected + "' but reached end of JSON"
            );
        }   
        char actual = json.charAt(pos);
        if (actual != expected) {
            throw new IllegalArgumentException(
                "Expected '" + expected + "' but got '" + actual + "'"
            );
        }  
        pos++;
    }
}
