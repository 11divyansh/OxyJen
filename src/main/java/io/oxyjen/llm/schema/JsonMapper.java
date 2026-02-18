package io.oxyjen.llm.schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
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
    	if (Optional.class.isAssignableFrom(targetType)) 
    		return (T) convertOptional(value, genericType);
    	if (targetType.isArray())
    		return (T) convertArray(value, genericType);
    	if (Collection.class.isAssignableFrom(targetType))
    		return (T) convertCollection(value, targetType, genericType);
    	if (Map.class.isAssignableFrom(targetType)) 
            return (T) convertMap(value, genericType);
    	if (targetType.isRecord()) 
            return deserializeRecord((Map<String, Object>) value, targetType);
  
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
    	Type innerFullType = extractNestedGenericType(genericType, 0);
    	if (innerFullType == null) 
    		throw new IllegalArgumentException(
    				"Optional must have generic type parameter");
    	Class<?> innerRawType;

        if (innerFullType instanceof Class<?>) {
            innerRawType = (Class<?>) innerFullType;
        } 
        else if (innerFullType instanceof ParameterizedType) {
            innerRawType = (Class<?>) 
                ((ParameterizedType) innerFullType).getRawType();
        } 
        else {
            throw new IllegalArgumentException(
                "Unsupported Optional inner type: " + innerFullType
            );
        }
        Object converted = convert(value, innerRawType, innerFullType);
        return Optional.ofNullable(converted);
    	
    }
    private static Object convertArray(Object value, Type arrayGenericType) {
        if (!(value instanceof List<?> jsonList)) {
            throw new IllegalArgumentException(
                "Expected JSON array for array type"
            );
        }
        Type componentFullType;

        if (arrayGenericType instanceof GenericArrayType genericArrayType) {
            componentFullType = genericArrayType.getGenericComponentType();
        } 
        else if (arrayGenericType instanceof Class<?> clazz) {
            componentFullType = clazz.getComponentType();
        } 
        else {
            throw new IllegalArgumentException("Unsupported array type");
        }
        Class<?> componentRawType;

        if (componentFullType instanceof Class<?> c) {
            componentRawType = c;
        } 
        else if (componentFullType instanceof ParameterizedType pt) {
            componentRawType = (Class<?>) pt.getRawType();
        } 
        else {
            throw new IllegalArgumentException("Unsupported component type");
        }
        int size = jsonList.size();
        Object array = Array.newInstance(componentRawType, size);
        for (int i = 0; i < size; i++) {
            Object element = jsonList.get(i);
            Object converted = convert(element, componentRawType, componentFullType);
            Array.set(array, i, converted);
        }
        return array;
    }
    private static Object convertCollection(Object value, Class<?> collectionType, Type genericType) {
    	if (value == null)return null;
    	if (!(value instanceof List<?> jsonList))
    		throw new IllegalArgumentException(
                    "Expected JSON array for collection, got: " + value.getClass());
    	
    	Type elementFullType = extractNestedGenericType(genericType,0);
    	if(elementFullType == null) {
    		throw new IllegalArgumentException(
    				"Collection must have generic type parameter. Use List<String>, not raw List");
    	}
    	Class<?> elementRawType;
    	if (elementFullType instanceof Class<?> c) {
    		elementRawType = c;
    	}
    	else if (elementFullType instanceof ParameterizedType pt) {
    		Type raw = pt.getRawType();
    		if(!(raw instanceof Class<?> rc)) {
    			throw new IllegalArgumentException(
    	                "Unsupported collection raw type: " + raw
    	            );
    		}
    		elementRawType = rc;
    	}
    	else {
            throw new IllegalArgumentException(
                "Unsupported collection element type: " + elementFullType
            );
        }
    	Collection<Object> result;
    	if (Set.class.isAssignableFrom(collectionType))
    		result = new LinkedHashSet<>();
    	else 
    		result = new ArrayList<>();
    	
    	for (Object element : jsonList) {
    		Object converted = convert(element, elementRawType, elementFullType);
    		result.add(converted);
    	}
    	return result;
    }
    @SuppressWarnings("unchecked")
	private static Map<?, ?> convertMap(Object value, Type genericType) {
    	if (value == null) return null;
        if (!(value instanceof Map<?,?> jsonMap)) {
            throw new IllegalArgumentException(
                "Expected JSON object for Map, got: " + value.getClass()
            );
        }
        Type keyFullType = extractNestedGenericType(genericType, 0);
        Type valueFullType = extractNestedGenericType(genericType, 1);
        if (keyFullType == null || valueFullType == null) {
            throw new IllegalArgumentException(
                "Map must have generic type parameters. Use Map<String, Integer>, not raw Map"
            );
        }
        Class<?> keyRawType = (Class<?>)
        		(keyFullType instanceof ParameterizedType pt
        				? pt.getRawType()
        				: keyFullType);
        if (keyRawType != String.class) {
            throw new IllegalArgumentException(
                "Only String keys are supported for Maps. Got: " + keyRawType
            );
        }
        
        Class<?> valueRawType;
        if (valueFullType instanceof Class<?> c) {
            valueRawType = c;
        } 
        else if (valueFullType instanceof ParameterizedType pt) {
            valueRawType = (Class<?>) pt.getRawType();
        } 
        else {
            throw new IllegalArgumentException(
                "Unsupported map value type: " + valueFullType
            );
        }
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (Map.Entry<?, ?> entry : jsonMap.entrySet()) {
            Object convertedValue = convert(entry.getValue(), valueRawType, valueFullType);
            result.put((String)entry.getKey(), convertedValue);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private static <T> T deserializeRecord(Map<String, Object> jsonMap, Class<T> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];
        
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String fieldName = component.getName();
            Class<?> fieldType = component.getType();
            Type genericType = component.getGenericType();
            
            Object jsonValue = jsonMap.get(fieldName);
            
            if (jsonValue == null) {
                if (isOptionalComponent(component)) {
                    args[i] = getDefaultValue(fieldType);
                    continue;
                }
                throw new IllegalArgumentException(
                    "Missing required field: " + fieldName + " in " + recordClass.getSimpleName()
                );
            }
            args[i] = convert(jsonValue, fieldType, genericType);
        }
        try {
            Constructor<T> constructor = recordClass.getDeclaredConstructor(
                Arrays.stream(components)
                    .map(RecordComponent::getType)
                    .toArray(Class[]::new)
            );
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(
                "Failed to instantiate record " + recordClass.getSimpleName() + 
                ": " + cause.getMessage(), cause
            );
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to instantiate record " + recordClass.getSimpleName() + 
                ": " + e.getMessage(), e
            );
        }
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
    private static Type extractNestedGenericType(Type type, int index) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType paramType = (ParameterizedType) type;
        Type[] args = paramType.getActualTypeArguments();

        if (index >= args.length) {
            return null;
        }
        return args[index];
    }
    private static boolean isOptionalComponent(RecordComponent component) {
        for (Annotation annotation : component.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if (name.equals("Nullable") || name.equals("Optional")) {
                return true;
            }
        }
        return component.getType() == Optional.class;
    }
    private static Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return null; 
    }
}
