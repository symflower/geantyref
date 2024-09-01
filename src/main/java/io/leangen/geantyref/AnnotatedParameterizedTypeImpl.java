/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;

import static io.leangen.geantyref.GenericTypeReflector.typeArraysEqual;

class AnnotatedParameterizedTypeImpl extends AnnotatedTypeImpl implements AnnotatedParameterizedType {

    private final AnnotatedType[] typeArguments;

    AnnotatedParameterizedTypeImpl(ParameterizedType rawType, Annotation[] annotations, AnnotatedType[] typeArguments, AnnotatedType ownerType) {
        super(rawType, annotations);
        this.typeArguments = typeArguments;
        this.ownerType = ownerType;
    }

    @Override
    public AnnotatedType[] getAnnotatedActualTypeArguments() {
        return typeArguments;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AnnotatedParameterizedType) || !super.equals(other)) {
            return false;
        }
        return typeArraysEqual(typeArguments, ((AnnotatedParameterizedType) other).getAnnotatedActualTypeArguments());
    }

    @Override
    public int hashCode() {
        return 127 * super.hashCode() ^ GenericTypeReflector.hashCode(typeArguments);
    }

    @Override
    public String toString() {
        ParameterizedType rawType = (ParameterizedType) type;
        String rawName = GenericTypeReflector.getTypeName(rawType.getRawType());

        StringBuilder typeName = new StringBuilder();
        if (ownerType != null) {
            typeName.append(ownerType).append('$');

            String prefix = (rawType.getOwnerType() instanceof ParameterizedType) ? ((Class<?>) ((ParameterizedType) rawType.getOwnerType()).getRawType()).getName() + '$'
                    : ((Class<?>) rawType.getOwnerType()).getName() + '$';
            if (rawName.startsWith(prefix))
                rawName = rawName.substring(prefix.length());
        }
        typeName.append(rawName);
        return annotationsString() + typeName + (typeArguments != null && typeArguments.length > 0 ? "<" + typesString(typeArguments) + ">" : "");
    }
}
