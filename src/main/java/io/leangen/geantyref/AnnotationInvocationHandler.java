/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * An implementation of {@link Annotation} that mimics the behavior of normal annotations.
 * It is an {@link InvocationHandler}, meant to be used via {@link TypeFactory#annotation(Class, Map)}.
 * <p>
 * The constructor checks that all the elements required by the annotation interface are provided
 * and that the types are compatible. If extra elements are provided, they are ignored.
 * If a value is of an incompatible type is provided or no value is provided for an element
 * without a default value, {@link AnnotationFormatException} is thrown.
 * </p>
 * <p>
 * Note: {@link #equals(Object)} and {@link #hashCode()} and implemented as specified
 * by {@link Annotation}, so instances are safe to mix with normal annotations.
 * {@link #toString} implementation copied from {@link sun.reflect.annotation.AnnotationInvocationHandler#toStringImpl()}
 *
 * @see Annotation
 */
@SuppressWarnings("JavadocReference")
class AnnotationInvocationHandler implements Annotation, InvocationHandler, Serializable {

    private static final long serialVersionUID = 8615044376674805680L;
    /**
     * Maps primitive {@code Class}es to their corresponding wrapper {@code Class}.
     */
    private static final Map<Class<?>, Class<?>> primitiveWrapperMap = new HashMap<>();

    static {
        primitiveWrapperMap.put(Boolean.TYPE, Boolean.class);
        primitiveWrapperMap.put(Byte.TYPE, Byte.class);
        primitiveWrapperMap.put(Character.TYPE, Character.class);
        primitiveWrapperMap.put(Short.TYPE, Short.class);
        primitiveWrapperMap.put(Integer.TYPE, Integer.class);
        primitiveWrapperMap.put(Long.TYPE, Long.class);
        primitiveWrapperMap.put(Double.TYPE, Double.class);
        primitiveWrapperMap.put(Float.TYPE, Float.class);
    }

    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> values;
    private final int hashCode;

    AnnotationInvocationHandler(Class<? extends Annotation> annotationType, Map<String, Object> values) throws AnnotationFormatException {
        Class<?>[] interfaces = annotationType.getInterfaces();
        if (annotationType.isAnnotation() && interfaces.length == 1 && interfaces[0] == Annotation.class) {
            this.annotationType = annotationType;
            this.values = Collections.unmodifiableMap(normalize(annotationType, values));
            this.hashCode = calculateHashCode();
        } else {
            throw new AnnotationFormatException(annotationType.getName() + " is not an annotation type");
        }
    }

    static Map<String, Object> normalize(Class<? extends Annotation> annotationType, Map<String, Object> values) throws AnnotationFormatException {
        Set<String> missing = new HashSet<>();
        Set<String> invalid = new HashSet<>();
        Map<String, Object> valid = new HashMap<>();
        for (Method element : annotationType.getDeclaredMethods()) {
            String elementName = element.getName();
            if (values.containsKey(elementName)) {
                Class<?> returnType = element.getReturnType();
                if (returnType.isPrimitive()) {
                    returnType = primitiveWrapperMap.get(returnType);
                }

                if (returnType.isInstance(values.get(elementName))) {
                    valid.put(elementName, values.get(elementName));
                } else {
                    invalid.add(elementName);
                }
            } else {
                if (element.getDefaultValue() != null) {
                    valid.put(elementName, element.getDefaultValue());
                } else {
                    missing.add(elementName);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new AnnotationFormatException("Missing value(s) for " + String.join(",", missing));
        }
        if (!invalid.isEmpty()) {
            throw new AnnotationFormatException("Incompatible type(s) provided for " + String.join(",", invalid));
        }
        return valid;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (values.containsKey(method.getName())) {
            return values.get(method.getName());
        }
        return method.invoke(this, args);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return annotationType;
    }

    /**
     * Performs an equality check as described in {@link Annotation#equals(Object)}.
     *
     * @param other The object to compare
     * @return Whether the given object is equal to this annotation or not
     * @see Annotation#equals(Object)
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!annotationType.isInstance(other)) {
            return false;
        }

        Annotation that = annotationType.cast(other);

        //compare annotation member values
        for (Entry<String, Object> element : values.entrySet()) {
            Object value = element.getValue();
            Object otherValue;
            try {
                otherValue = that.annotationType().getMethod(element.getKey()).invoke(that);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }

            if (!Objects.deepEquals(value, otherValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculates the hash code of this annotation as described in {@link Annotation#hashCode()}.
     *
     * @return The hash code of this annotation.
     * @see Annotation#hashCode()
     */
    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(128);
        result.append('@');
        result.append(annotationType.getName());
        result.append('(');
        boolean firstMember = true;
        TreeMap<String, Object> memberValues = new TreeMap<>(values);
        Set<Map.Entry<String, Object>> entries = memberValues.entrySet();
        boolean loneValue = entries.size() == 1;
        for (Map.Entry<String, Object> e : entries) {
            if (firstMember)
                firstMember = false;
            else
                result.append(", ");

            String key = e.getKey();
            if (!loneValue || !"value".equals(key)) {
                result.append(key);
                result.append('=');
            }
            loneValue = false;
            result.append(memberValueToString(e.getValue()));
        }
        result.append(')');
        return result.toString();
    }

    private static String memberValueToString(Object value) {
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            // primitive value, string, class, enum const, or annotation
            if (type == Class.class)
                return toSourceString((Class<?>) value);
            else if (type == String.class)
                return toSourceString((String) value);
            if (type == Character.class)
                return toSourceString((char) value);
            else if (type == Double.class)
                return  toSourceString((double) value);
            else if (type == Float.class)
                return  toSourceString((float) value);
            else if (type == Long.class)
                return  toSourceString((long) value);
            else if (type == Byte.class)
                return  toSourceString((byte) value);
            else
                return value.toString();
        } else {
            Stream<String> stringStream;
            if (type == byte[].class)
                stringStream = convert((byte[]) value);
            else if (type == char[].class)
                stringStream = convert((char[]) value);
            else if (type == double[].class)
                stringStream = DoubleStream.of((double[]) value)
                        .mapToObj(AnnotationInvocationHandler::toSourceString);
            else if (type == float[].class)
                stringStream = convert((float[]) value);
            else if (type == int[].class)
                stringStream = IntStream.of((int[]) value).mapToObj(String::valueOf);
            else if (type == long[].class) {
                stringStream = LongStream.of((long[]) value)
                        .mapToObj(AnnotationInvocationHandler::toSourceString);
            } else if (type == short[].class)
                stringStream = convert((short[]) value);
            else if (type == boolean[].class)
                stringStream = convert((boolean[]) value);
            else if (type == Class[].class)
                stringStream =
                        Arrays.stream((Class<?>[]) value).
                                map(AnnotationInvocationHandler::toSourceString);
            else if (type == String[].class)
                stringStream =
                        Arrays.stream((String[])value).
                                map(AnnotationInvocationHandler::toSourceString);
            else
                stringStream = Arrays.stream((Object[])value).map(Objects::toString);

            return stringStreamToString(stringStream);
        }
    }

    /**
     * Translates a Class value to a form suitable for use in the
     * string representation of an annotation.
     */
    private static String toSourceString(Class<?> clazz) {
        Class<?> finalComponent = clazz;
        StringBuilder arrayBrackets = new StringBuilder();

        while(finalComponent.isArray()) {
            finalComponent = finalComponent.getComponentType();
            arrayBrackets.append("[]");
        }

        return finalComponent.getName() + arrayBrackets + ".class";
    }

    private static String toSourceString(float f) {
        if (Float.isFinite(f))
            return f + "f" ;
        else {
            if (Float.isInfinite(f)) {
                return (f < 0.0f) ? "-1.0f/0.0f": "1.0f/0.0f";
            } else
                return "0.0f/0.0f";
        }
    }

    private static String toSourceString(double d) {
        if (Double.isFinite(d))
            return Double.toString(d);
        else {
            if (Double.isInfinite(d)) {
                return (d < 0.0f) ? "-1.0/0.0": "1.0/0.0";
            } else
                return "0.0/0.0";
        }
    }

    private static String toSourceString(char c) {
        return '\'' + quote(c) + '\'';
    }

    /**
     * Escapes a character if it has an escape sequence or is
     * non-printable ASCII.  Leaves non-ASCII characters alone.
     */
    private static String quote(char ch) {
        switch (ch) {
            case '\b':  return "\\b";
            case '\f':  return "\\f";
            case '\n':  return "\\n";
            case '\r':  return "\\r";
            case '\t':  return "\\t";
            case '\'':  return "\\'";
            case '\"':  return "\\\"";
            case '\\':  return "\\\\";
            default:
                return (isPrintableAscii(ch))
                        ? String.valueOf(ch)
                        : String.format("\\u%04x", (int) ch);
        }
    }

    /**
     * Is a character printable ASCII?
     */
    private static boolean isPrintableAscii(char ch) {
        return ch >= ' ' && ch <= '~';
    }

    private static String toSourceString(byte b) {
        return String.format("(byte)0x%02x", b);
    }

    private static String toSourceString(long ell) {
        return ell + "L";
    }

    /**
     * Return a string suitable for use in the string representation
     * of an annotation.
     */
    private static String toSourceString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            sb.append(quote(s.charAt(i)));
        }
        sb.append('"');
        return sb.toString();
    }

    private static Stream<String> convert(byte[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (byte b : values)
            list.add(toSourceString(b));
        return list.stream();
    }

    private static Stream<String> convert(char[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (char c : values)
            list.add(toSourceString(c));
        return list.stream();
    }

    private static Stream<String> convert(float[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (float f : values) {
            list.add(toSourceString(f));
        }
        return list.stream();
    }

    private static Stream<String> convert(short[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (short s : values)
            list.add(Short.toString(s));
        return list.stream();
    }

    private static Stream<String> convert(boolean[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (boolean b : values)
            list.add(Boolean.toString(b));
        return list.stream();
    }

    private static String stringStreamToString(Stream<String> stream) {
        return stream.collect(Collectors.joining(", ", "{", "}"));
    }

    private int calculateHashCode() {
        int hashCode = 0;

        for (Entry<String, Object> element : values.entrySet()) {
            hashCode += (127 * element.getKey().hashCode()) ^ calculateHashCode(element.getValue());
        }

        return hashCode;
    }

    private int calculateHashCode(Object element) {
        if (!element.getClass().isArray()) {
            return element.hashCode();
        }
        if (element instanceof Object[]) {
            return Arrays.hashCode((Object[]) element);
        }
        if (element instanceof byte[]) {
            return Arrays.hashCode((byte[]) element);
        }
        if (element instanceof short[]) {
            return Arrays.hashCode((short[]) element);
        }
        if (element instanceof int[]) {
            return Arrays.hashCode((int[]) element);
        }
        if (element instanceof long[]) {
            return Arrays.hashCode((long[]) element);
        }
        if (element instanceof char[]) {
            return Arrays.hashCode((char[]) element);
        }
        if (element instanceof float[]) {
            return Arrays.hashCode((float[]) element);
        }
        if (element instanceof double[]) {
            return Arrays.hashCode((double[]) element);
        }
        if (element instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) element);
        }

        return Objects.hashCode(element);
    }
}
