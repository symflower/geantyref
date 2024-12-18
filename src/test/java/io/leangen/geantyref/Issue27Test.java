/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import io.leangen.geantyref.Annotations.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.Map;
import java.util.Objects;

import static io.leangen.geantyref.Assertions.assertAnnotationsPresent;
import static io.leangen.geantyref.Assertions.assertEqualTypeVariables;
import static io.leangen.geantyref.Assertions.assertTypeIsRecursive;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <a href="https://github.com/leangen/geantyref/issues/27">StackOverflowError in VarMap.map when calling GenericTypeReflector.getParameterTypes()</a>
 */
public class Issue27Test {

    @Test
    public void getParameterTypesOnRecursiveType() throws NoSuchMethodException {
        AnnotatedType cls = GenericTypeReflector.annotate(SelfReferential.class);
        Method method = reflectSelfRefMethod((Class<?>) cls.getType());
        AnnotatedType[] paramTypes = GenericTypeReflector.getParameterTypes(method, cls);
        AnnotatedParameterizedType mapType = (AnnotatedParameterizedType) paramTypes[0];
        AnnotatedTypeVariable keyType = (AnnotatedTypeVariable) mapType.getAnnotatedActualTypeArguments()[0];
        AnnotatedTypeVariable valueType = (AnnotatedTypeVariable) mapType.getAnnotatedActualTypeArguments()[1];
        TypeVariable<Method> T = method.getTypeParameters()[0];
        assertEqualTypeVariables(T, keyType.getType());
        assertEqualTypeVariables(T, valueType.getType());
        assertAnnotationsPresent(keyType, A6.class, A3.class);
        assertAnnotationsPresent(valueType, A7.class, A3.class);
        for (AnnotatedTypeVariable var : new AnnotatedTypeVariable[] {keyType, valueType}) {
            AnnotatedParameterizedType bound = (AnnotatedParameterizedType) var.getAnnotatedBounds()[0];
            AnnotatedType keyA = bound.getAnnotatedActualTypeArguments()[0];
            AnnotatedType keyB = bound.getAnnotatedActualTypeArguments()[1];
            assertEqualTypeVariables(T, keyA.getType());
            assertAnnotationsPresent(keyA, A4.class, A3.class, A1.class);
            assertEqualTypeVariables(T, keyB.getType());
            assertAnnotationsPresent(keyB, A5.class, A3.class, A2.class);
            assertTypeIsRecursive(bound);
        }
    }

    @Test
    public void getExactParameterTypesOnRecursiveType() throws NoSuchMethodException {
        assertThrows(UnresolvedTypeVariableException.class, () -> {
            Class<?> cls = SelfReferential.class;
            Method method = reflectSelfRefMethod(cls);
            GenericTypeReflector.getExactParameterTypes(method, cls);
        });
    }

    private static Method reflectSelfRefMethod(Class<?> cls) throws NoSuchMethodException {
        return Objects.requireNonNull(cls.getMethod("selfRef", Map.class));
    }

    @SuppressWarnings("unused")
    static abstract class Ref<@A1 A, @A2 B> {
    }

    static class SelfReferential {
        @SuppressWarnings("unused")
        public <@A3 T extends Ref<@A4 T, @A5 T>> void selfRef(Map<@A6 T, @A7 T> map) {
            throw new UnsupportedOperationException("Just for testing");
        }
    }
}
