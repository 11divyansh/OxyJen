package io.oxyjen.llm.schema;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    	if (targetType.isArray())
    		return (T) convertArray(value, targetType);
    	if (Collection.class.isAssignableFrom(targetType))
    		return (T) convertCollection(value, targetType, genericType);
    	if (Map.class.isAssignableFrom(targetType)) 
            return (T) convertMap(value, genericType);
    	throw new IllegalArgumentException(
    			"Unsupported type: " + targetType.getSimpleName());
    }
    private static Number convertNumber(Object value, Class<?> targetType) {
    	if (!(value instanceof Number))
    		throw new IllegalArgumentException("");
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
    private static Object convertArray(Object value, Class<?> arrayType) {
    	if (!(value instanceof List))
    		throw new IllegalArgumentException(
                    "Expected JSON array for array type, got: " + value.getClass());
    	List<?> jsonList = (List<?>) value;
    	int size = jsonList.size();
    	Class<?> componentType = arrayType.getComponentType();
    	Object array = Array.newInstance(componentType, size);
    	for (int i = 0; i <= size; i++) {
    		Object element = jsonList.get(i);
    		Object converted = convert(element, componentType, componentType);
    		Array.set(array, i, converted);
    	}
    	return array;
    }
    private static Object convertCollection(Object value, Class<?> collectionType, Type genericType) {
    	if (!(value instanceof List))
    		throw new IllegalArgumentException(
                    "Expected JSON array for collection, got: " + value.getClass());
    	List<?> jsonList = (List<?>) value;
    	Class<?> elementType = extractGenericType(genericType,0);
    	if(elementType == null) {
    		throw new IllegalArgumentException(
    				"Collection must have generic type parameter. Use List<String>, not raw List");
    	}
    	Collection<Object> result;
    	if (Set.class.isAssignableFrom(collectionType))
    		result = new LinkedHashSet<>();
    	else 
    		result = new ArrayList<>();
    	
    	for (Object element : jsonList) {
    		Object converted = convert(element, elementType, elementType);
    		result.add(converted);
    	}
    	return result;
    }
    @SuppressWarnings("unchecked")
	private static Map<?, ?> convertMap(Object value, Type genericType) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(
                "Expected JSON object for Map, got: " + value.getClass()
            );
        }
        Map<String, Object> jsonMap = (Map<String, Object>) value;
        Class<?> keyType = extractGenericType(genericType, 0);
        Class<?> valueType = extractGenericType(genericType, 1);
        if (keyType == null || valueType == null) {
            throw new IllegalArgumentException(
                "Map must have generic type parameters. Use Map<String, Integer>, not raw Map"
            );
        }
        if (keyType != String.class) {
            throw new IllegalArgumentException(
                "Only String keys are supported for Maps. Got: " + keyType
            );
        }
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
            Object convertedValue = convert(entry.getValue(), valueType, valueType);
            result.put(entry.getKey(), convertedValue);
        }
        return result;
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
