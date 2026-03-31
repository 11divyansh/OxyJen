package io.oxyjen.util;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts Java objects into a JSON-compatible tree.
 *
 * Output types:
 *   Record / POJO  -> Map<String, Object>
 *   Collection     -> List<Object>
 *   Array          -> List<Object>
 *   Map            -> Map<String, Object>
 *   Enum           -> String
 *   Primitives     -> same
 *   null           -> null
 *
 * This is the reverse of JsonMapper - used to validate tool outputs
 * against an output schema before passing to the next node.
 */
public final class JsonSerializer {
    private JsonSerializer() {}
    /**
     * Convert any Java object to a JSON-compatible tree.
     *
     * @param obj the object to serialize
     * @return Map, List, String, Number, Boolean, or null
     */
    public static Object toJsonTree(Object obj) {
    	return toJsonTree(obj, new IdentityHashMap<>());
    }
    private static Object toJsonTree(Object obj, IdentityHashMap<Object, Boolean> visited) {
        if (obj == null) return null;
        if (obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean) {
            return obj;
        }
        if (obj instanceof Enum<?> e) return e.name();
        if (obj instanceof Optional<?> opt)
            return opt.isPresent() ? toJsonTree(opt.get(), visited) : null;
        if (isComplexType(obj)) {
            if (visited.containsKey(obj)) {
                throw new JsonSerializationException(
                    "Cyclic reference detected for type: "
                            + obj.getClass().getSimpleName(),
                    null
                );
            }
            visited.put(obj, Boolean.TRUE);
            try {
                if (obj instanceof Collection<?> col) {
                    List<Object> list = new ArrayList<>();
                    for (Object item : col) {
                        list.add(toJsonTree(item, visited));
                    }
                    return list;
                }
                if (obj.getClass().isArray()) {
                    return serializeArray(obj, visited);
                }
                if (obj instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        Object key = entry.getKey();
                        if (key == null) {
                            throw new JsonSerializationException(
                                    "Map contains null key",
                                    null
                            );
                        }
                        result.put(
                                key.toString(),
                                toJsonTree(entry.getValue(), visited)
                        );
                    }
                    return result;
                }
                if (obj.getClass().isRecord()) {
                    return serializeRecord(obj, visited);
                }
                return serializePojo(obj, visited);
            } finally {
                visited.remove(obj);
            }
        }
        throw new JsonSerializationException(
                "Unsupported type: " + obj.getClass(),
                null
        );
    }

    private static Map<String, Object> serializeRecord(Object record, IdentityHashMap<Object, Boolean> visited) {
        Map<String, Object> map = new LinkedHashMap<>();
        RecordComponent[] components = record.getClass().getRecordComponents();
        for (RecordComponent component : components) {
            String name = component.getName();
            try {
            	Method accessor = component.getAccessor();
            	accessor.setAccessible(true);
            	Object value = accessor.invoke(record);
                map.put(name, toJsonTree(value,visited));
            } catch (Exception e) {
                throw new JsonSerializationException(
                    "Failed to access record component '" + name + "' on " +
                    record.getClass().getSimpleName(), e
                );
            }
        }
        return map;
    }

    private static Map<String, Object> serializePojo(Object pojo, IdentityHashMap<Object, Boolean> visited) {
        Map<String, Object> map = new LinkedHashMap<>();
        Method[] methods = pojo.getClass().getMethods();
        for (Method method : methods) {
            if (!isGetter(method)) {
                continue;
            }
            String fieldName = fieldNameFromGetter(method);
            try {
            	method.setAccessible(true);
                Object value = method.invoke(pojo);
                map.put(fieldName, toJsonTree(value,visited));
            } catch (Exception e) {
                throw new JsonSerializationException(
                    "Failed to invoke getter '" + method.getName() + "' on " +
                    pojo.getClass().getSimpleName(), e
                );
            }
        }
        return map;
    }

    private static Object serializeArray(Object array, IdentityHashMap<Object, Boolean> visited) {
    	if (array instanceof char[] arr) return new String(arr);
        List<Object> list = new ArrayList<>();
        if (array instanceof int[] arr)     { for (int v    : arr) list.add(v);    return list; }
        if (array instanceof long[] arr)    { for (long v   : arr) list.add(v);    return list; }
        if (array instanceof double[] arr)  { for (double v : arr) list.add(v);    return list; }
        if (array instanceof float[] arr)   { for (float v  : arr) list.add(v);    return list; }
        if (array instanceof boolean[] arr) { for (boolean v: arr) list.add(v);    return list; }
        if (array instanceof byte[] arr)    { for (byte v   : arr) list.add(v);    return list; }
        if (array instanceof short[] arr)   { for (short v  : arr) list.add(v);    return list; }
        if (array instanceof char[] arr)    { for (char v   : arr) list.add(String.valueOf(v)); return list; }
        Object[] objArray = (Object[]) array;
        for (Object item : objArray) {
            list.add(toJsonTree(item, visited));
        }
        return list;
    }

    private static boolean isGetter(Method method) {
        if (method.isSynthetic()) return false;
        if (method.getDeclaringClass() == Object.class) return false;
        if (method.getParameterCount() != 0) return false;
        if (method.getReturnType() == void.class) return false;
        String name = method.getName();
        if (name.equals("getClass")) return false;
        if (name.startsWith("is")) {
            return method.getReturnType() == boolean.class
                || method.getReturnType() == Boolean.class;
        }
        return name.startsWith("get");
    }

    private static String fieldNameFromGetter(Method method) {
        String name = method.getName();
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else {
            name = name.substring(3);
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    private static boolean isComplexType(Object obj) {
        return !(obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Enum<?>
                || obj instanceof Optional<?>);
    }
    public static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}