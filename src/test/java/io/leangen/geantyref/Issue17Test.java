/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * https://github.com/leangen/geantyref/issues/17
 */
public class Issue17Test {

    @Test
    public void testTypeTokenEqualityAndHashCode() throws NoSuchMethodException {
        TypeToken<Set<? extends Number>> numberTypeToken = new TypeToken<Set<? extends Number>>() {};
        Method method = Issue17Test.class.getDeclaredMethod("dummyMethod", Set.class);
        TypeToken<?> otherTypeToken = TypeToken.get(method.getParameters()[0].getParameterizedType());

        assertEquals(numberTypeToken, otherTypeToken, "Both TypeTokens should be equal");
        assertEquals(numberTypeToken.getType(), otherTypeToken.getType(), "Both TypeTokens should have equal types");
        assertEquals(numberTypeToken.hashCode(), otherTypeToken.hashCode(), "Both TypeTokens should have equal hash codes");
    }

    @SuppressWarnings("unused")
    private void dummyMethod(Set<? extends Number> test) {
    }
}
