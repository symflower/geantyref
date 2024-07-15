/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

/**
 * <a href="https://github.com/leangen/geantyref/issues/27">StackOverflowError in VarMap.map when calling GenericTypeReflector.getParameterTypes()</a>
 */
public class Issue27Test {

    @Test
    public void testIssue27_Stackoverflow_VarMap() throws NoSuchMethodException {
        Class<?> cls = ProjectValidator.class;
        Method method = reflectValidateMethod(cls);
        Type[] types = GenericTypeReflector.getParameterTypes(method, cls);

        assertEquals(2, types.length);
        assertEquals(long.class, types[0]);
        ParameterizedType clsType = (ParameterizedType) types[1];
        assertEquals(Class.class.getName() + "<T>", clsType.getTypeName());
        assertEquals(Class.class, clsType.getRawType());

        TypeVariable<?> typeVar = (TypeVariable<?>) clsType.getActualTypeArguments()[0];
        assertEquals(Resource.class.getName() + "<T>", typeVar.getBounds()[0].getTypeName());
    }

    @Test(expected = UnresolvedTypeVariableException.class)
    public void testIssue27_Map_exact_shall_throw_UnresolvedTypeVariableException() throws NoSuchMethodException {
        Class<?> cls = ProjectValidator.class;
        Method method = reflectValidateMethod(cls);
        GenericTypeReflector.getExactParameterTypes(method, cls);
    }

    private static Method reflectValidateMethod(Class<?> cls) throws NoSuchMethodException {
        return Objects.requireNonNull(cls.getMethod("validate", long.class, Class.class));
    }

    @SuppressWarnings("unused")
    static abstract class Resource<StatusT> {
    }

    static class ProjectValidator {
        @SuppressWarnings("unused")
        public <T extends Resource<T>> void validate(long id, Class<T> klass) {
            throw new UnsupportedOperationException("Just for testing");
        }
    }
}
