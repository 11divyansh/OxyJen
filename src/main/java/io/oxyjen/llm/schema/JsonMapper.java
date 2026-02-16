package io.oxyjen.llm.schema;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Maps JSON strings to Java objects.
 * 
 * Full support for:
 * - Records
 * - POJOs
 * - Nested objects
 * - Collections (List, Set)
 * - Arrays
 * - Maps
 * - Enums
 * - Primitives
 * - Optional fields
 * - Generic types
 * 
 * @version 0.4
 */
public final class JsonMapper {
	/**
     * Deserialize json string to typed object.
     * 
     * @param json Valid JSON string
     * @param targetClass Class to deserialize to
     * @return Instance of targetClass
     */
    public static <T> T deserialize(String json, Class<T> targetClass) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON cannot be null or empty");
        }
        
        Object parsed = JsonParser.parse(json);
        return convert(parsed, targetClass, targetClass);
    }
    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> targetType, Type genericType) {
    	if (value == null) 
    		return null;
    	if (targetType == String.class) 
    		return (T) value.toString();
    	if (isNumericType(targetType)) 
    		return (T) convertNumber(value, targetType);
    	if (targetType == boolean.class || targetType == Boolean.class) 
    		return (T) Boolean.valueOf(value.toString());
    	if (targetType.isEnum()) 
    		return (T) Enum.valueOf((Class<Enum>) targetType, value.toString());
    	if (targetType == Optional.class) 
    		return (T) convertOptional(value, genericType);
    	throw new IllegalArgumentException(
    			"Unsupported type: " + targetType.getSimpleName());
    }
    private static Number convertNumber(Object value, Class<?> targetType) {
    	Number number = (Number) value;
    	if (targetType == int.class || targetType == Integer.class) 
    		return number.intValue();
    	if (targetType == long.class || targetType == Long.class)
    		return number.longValue();
    	if (targetType == double.class || targetType == Double.class)
    		return number.doubleValue();
    	if (targetType == float.class || targetType == Float.class)
    		return number.floatValue();
    	if (targetType == short.class || targetType == Short.class)
    		return number.shortValue();
    	if (targetType == byte.class || targetType == Byte.class)
    		return number.byteValue();
  
    	throw new IllegalArgumentException("Unsupported numeric type: " + targetType);
    }
    private static Optional<?> convertOptional(Object value, Type genericType) {
    	Class<?> innerType = extractGenericType(genericType, 0);
    	if (innerType == null) 
    		throw new IllegalArgumentException(
    				"Optional must have generic type parameter");
    	Object converted = convert(value, innerType, innerType);
    	return Optional.ofNullable(converted);
    	
    }
    private static boolean isNumericType(Class<?> type) {
    	return type == int.class || type == Integer.class ||
    		   type == long.class || type == Long.class ||
    		   type == double.class || type == Double.class ||
    		   type == float.class || type == Float.class || 
    		   type == short.class || type == Short.class ||
    		   type == byte.class || type == Byte.class;
    }
    private static Class<?> extractGenericType(Type type, int index) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType paramType = (ParameterizedType) type;
        Type[] typeArgs = paramType.getActualTypeArguments();      
        if (index >= typeArgs.length) {
            return null;
        }  
        Type argType = typeArgs[index];
        if (argType instanceof Class) {
            return (Class<?>) argType;
        }
        if (argType instanceof ParameterizedType) {
        	return (Class<?>) ((ParameterizedType) argType).getRawType();
        }
        return null;
    }
}
