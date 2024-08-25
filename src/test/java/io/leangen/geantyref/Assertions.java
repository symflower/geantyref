package io.leangen.geantyref;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Assertions {

    @SafeVarargs
    public static void assertAnnotationsPresent(AnnotatedType type, Class<? extends Annotation>... annotations) {
        assertArrayEquals(annotations, Arrays.stream(type.getAnnotations()).map(Annotation::annotationType).toArray());
    }

    public static void assertEqualTypeVariables(TypeVariable<?> expected, Type actual) {
        if (actual instanceof TypeVariableImpl<?> var) {
            //!!Expected and actual values are intentionally reversed.
            assertEquals(var, expected);
        } else {
            assertEquals(expected, actual);
        }
    }

    public static void assertTypeIsRecursive(AnnotatedParameterizedType type) {
        Set<AnnotatedTypeVariable> roots =  Arrays.stream(type.getAnnotatedActualTypeArguments())
                .filter(arg -> arg instanceof AnnotatedTypeVariable)
                .map(arg -> (AnnotatedTypeVariable) arg)
                .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));
        assertTrue("Variable " + type + " does not recur within given depth", isRecursive(roots, type, 0, 10));
    }

    private static boolean isRecursive(Set<AnnotatedTypeVariable> roots, AnnotatedType node, int currentDepth, int maxDepth) {
        if (currentDepth >= maxDepth) {
            return false;
        }
        if (node instanceof AnnotatedTypeVariable var) {
            if (roots.contains(var)) {
                return true;
            }
            return Arrays.stream(var.getAnnotatedBounds())
                    .allMatch(bound -> isRecursive(roots, bound, currentDepth + 1, maxDepth));
        } else if (node instanceof AnnotatedParameterizedType parameterized) {
            return Arrays.stream(parameterized.getAnnotatedActualTypeArguments())
                    .allMatch(typeArg -> isRecursive(roots, typeArg, currentDepth + 1, maxDepth));
        } else {
            return false;
        }
    }
}
