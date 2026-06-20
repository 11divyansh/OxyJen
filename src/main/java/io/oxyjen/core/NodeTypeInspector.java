package io.oxyjen.core;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Helper for resolving NodePlugin input/output types.
 *
 * Used by graph builders and validators to decide when adapters can be inserted
 * safely between nodes.
 */
public final class NodeTypeInspector {

    private NodeTypeInspector() {}

    public static Type resolveInputType(NodePlugin<?, ?> node) {
        return resolveTypeArg(node.getClass(), 0);
    }

    public static Type resolveOutputType(NodePlugin<?, ?> node) {
        return resolveTypeArg(node.getClass(), 1);
    }

    public static Class<?> rawType(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) return c;
        if (type instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0) return rawType(upper[0]);
        }
        if (type instanceof GenericArrayType gat) {
            Class<?> component = rawType(gat.getGenericComponentType());
            if (component != null) return Array.newInstance(component, 0).getClass();
        }
        return null;
    }

    public static String typeName(Type type) {
        if (type == null) return "null";
        if (type instanceof Class<?> c) return c.getSimpleName();
        if (type instanceof ParameterizedType pt) {
            String raw = rawType(pt) != null ? rawType(pt).getSimpleName() : "?";
            String args = Arrays.stream(pt.getActualTypeArguments())
                .map(NodeTypeInspector::typeName)
                .collect(Collectors.joining(", "));
            return raw + "<" + args + ">";
        }
        return type.getTypeName();
    }

    public static boolean isAssignable(Type sourceOutput, Type targetInput) {
        Class<?> sourceRaw = rawType(sourceOutput);
        Class<?> targetRaw = rawType(targetInput);
        if (sourceRaw == null || targetRaw == null) return false;
        return targetRaw.isAssignableFrom(sourceRaw);
    }

    private static Type resolveTypeArg(Class<?> clazz, int argIndex) {
        if (clazz == null || clazz == Object.class) return null;

        for (Type iface : clazz.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt
                    && pt.getRawType() instanceof Class<?> raw
                    && raw == NodePlugin.class) {
                Type arg = pt.getActualTypeArguments()[argIndex];
                if (arg instanceof TypeVariable<?>) return null;
                return arg;
            }
        }

        Type superclass = clazz.getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt
                && pt.getRawType() instanceof Class<?> raw
                && raw == NodePlugin.class) {
            Type arg = pt.getActualTypeArguments()[argIndex];
            if (arg instanceof TypeVariable<?>) return null;
            return arg;
        }

        if (superclass instanceof Class<?> sc) {
            Type result = resolveTypeArg(sc, argIndex);
            if (result != null) return result;
        }

        for (Type iface : clazz.getGenericInterfaces()) {
            if (iface instanceof Class<?> ic) {
                Type result = resolveTypeArg(ic, argIndex);
                if (result != null) return result;
            }
        }
        return null;
    }
}