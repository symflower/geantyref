/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

import static io.leangen.geantyref.GenericTypeReflector.annotate;
import static io.leangen.geantyref.GenericTypeReflector.merge;
import static io.leangen.geantyref.GenericTypeReflector.updateAnnotations;
import static java.util.Arrays.stream;

/**
 * Mapping between type variables and actual parameters.
 *
 * @author Wouter Coekaerts {@literal (wouter@coekaerts.be)}
 * @author Bojan Tomic {@literal (veggen@gmail.com)}
 */
@SuppressWarnings("rawtypes")
class VarMap {

    private final Map<TypeVariable, AnnotatedType> map = new HashMap<>();
    private final Map<AnnotatedTypeVariable, AnnotatedTypeVariable> varCache = new HashMap<>();

    /**
     * Creates an empty VarMap
     */
    VarMap() {
    }

    /**
     * Creates a VarMap mapping the type parameters of the class used in {@code type} to their
     * actual value.
     */
    VarMap(AnnotatedParameterizedType type) {
        // loop over the type and its generic owners
        do {
            Class<?> clazz = (Class<?>) ((ParameterizedType) type.getType()).getRawType();
            AnnotatedType[] arguments = type.getAnnotatedActualTypeArguments();
            TypeVariable[] typeParameters = clazz.getTypeParameters();

            // since we're looping over two arrays in parallel, just to be sure check they have the same size
            if (arguments.length != typeParameters.length) {
                throw new IllegalStateException(
                        "The given type [" + type + "] is inconsistent: it has " +
                        arguments.length + " arguments instead of " + typeParameters.length);
            }

            for (int i = 0; i < arguments.length; i++) {
                add(typeParameters[i], arguments[i]);
            }

            AnnotatedType owner = type.getAnnotatedOwnerType();
            type = (owner instanceof AnnotatedParameterizedType) ? (AnnotatedParameterizedType) owner : null;
        } while (type != null);
    }

    VarMap(ParameterizedType type) {
        this((AnnotatedParameterizedType) annotate(type));
    }

    VarMap(TypeVariable[] variables, AnnotatedType[] values) {
        addAll(variables, values);
    }

    void add(TypeVariable variable, AnnotatedType value) {
        map.put(variable, value);
    }

    void addAll(TypeVariable[] variables, AnnotatedType[] values) {
        assert variables.length == values.length;
        for (int i = 0; i < variables.length; i++) {
            map.put(variables[i], values[i]);
        }
    }

    AnnotatedType map(AnnotatedType type) {
        return map(type, MappingMode.EXACT);
    }

    AnnotatedType map(AnnotatedType type, MappingMode mappingMode) {
        if (type.getType() instanceof Class) {
            return updateAnnotations(type, ((Class) type.getType()).getAnnotations());
        } else if (type instanceof AnnotatedTypeVariable) {
            TypeVariable<?> tv = (TypeVariable) type.getType();
            if (!map.containsKey(tv)) {
                if (mappingMode.equals(MappingMode.ALLOW_INCOMPLETE)) {
                    AnnotatedTypeVariable variable = cloneVar((AnnotatedTypeVariable) type);

                    if (varCache.containsKey(variable)) {
                        return varCache.get(variable);
                    }
                    Annotation[] merged = merge(variable.getAnnotations(), tv.getAnnotations());
                    AnnotatedTypeVariableImpl v = new AnnotatedTypeVariableImpl(tv, merged);
                    varCache.put(variable, v);
                    AnnotatedType[] bounds = map(variable.getAnnotatedBounds(), mappingMode);
                    return v.init(bounds);
                } else {
                    throw new UnresolvedTypeVariableException(tv);
                }
            }
            //#IMPLTNOTE1 Flip key.equals(tv), as the equality check will fail if the underlying variable is replaced
            TypeVariable varFromClass = map.keySet().stream().filter(key -> key.equals(tv)).findFirst().get();
            Annotation[] merged = merge(type.getAnnotations(), tv.getAnnotations(), map.get(tv).getAnnotations(), varFromClass.getAnnotations());
            return updateAnnotations(map.get(tv), merged);
        } else if (type instanceof AnnotatedParameterizedType) {
            AnnotatedParameterizedType pType = (AnnotatedParameterizedType) type;
            ParameterizedType inner = (ParameterizedType) pType.getType();
            Class raw = (Class) inner.getRawType();
            AnnotatedType[] typeParameters = new AnnotatedType[raw.getTypeParameters().length];
            for (int i = 0; i < typeParameters.length; i++) {
                AnnotatedType typeParameter = map(pType.getAnnotatedActualTypeArguments()[i], mappingMode);
                typeParameters[i] = updateAnnotations(typeParameter, raw.getTypeParameters()[i].getAnnotations());
            }
            Type[] rawArgs = stream(typeParameters).map(AnnotatedType::getType).toArray(Type[]::new);
            AnnotatedType ownerType = pType.getAnnotatedOwnerType() == null ? null : map(pType.getAnnotatedOwnerType(), mappingMode);
            ParameterizedType newInner = new ParameterizedTypeImpl((Class) inner.getRawType(), rawArgs, ownerType != null ? ownerType.getType() : null);
            return new AnnotatedParameterizedTypeImpl(newInner, merge(pType.getAnnotations(), raw.getAnnotations()), typeParameters, ownerType);
        } else if (type instanceof AnnotatedWildcardType) {
            AnnotatedWildcardType wType = (AnnotatedWildcardType) type;
            AnnotatedType[] up = map(wType.getAnnotatedUpperBounds(), mappingMode);
            AnnotatedType[] lw = map(wType.getAnnotatedLowerBounds(), mappingMode);
            Type[] upperBounds;
            if (up == null || up.length == 0) {
                upperBounds = ((WildcardType) wType.getType()).getUpperBounds();
            } else {
                upperBounds = stream(up).map(AnnotatedType::getType).toArray(Type[]::new);
            }
            WildcardType w = new WildcardTypeImpl(upperBounds, stream(lw).map(AnnotatedType::getType).toArray(Type[]::new));
            return new AnnotatedWildcardTypeImpl(w, wType.getAnnotations(), lw, up);
        } else if (type instanceof AnnotatedArrayType) {
            return AnnotatedArrayTypeImpl.createArrayType(map(((AnnotatedArrayType) type).getAnnotatedGenericComponentType(), mappingMode), type.getAnnotations());
        } else {
            throw new RuntimeException("Not implemented: mapping " + type.getClass() + " (" + type + ")");
        }
    }

    AnnotatedType[] map(AnnotatedType[] types) {
        return map(types, MappingMode.EXACT);
    }

    AnnotatedType[] map(AnnotatedType[] types, MappingMode mappingMode) {
        AnnotatedType[] result = new AnnotatedType[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = map(types[i], mappingMode);
        }
        return result;
    }

    Type[] map(Type[] types) {
        AnnotatedType[] result = map(stream(types).map(GenericTypeReflector::annotate).toArray(AnnotatedType[]::new));
        return stream(result).map(AnnotatedType::getType).toArray(Type[]::new);
    }

    Type map(Type type) {
        return map(annotate(type)).getType();
    }

    public enum MappingMode {
        EXACT, ALLOW_INCOMPLETE
    }

    AnnotatedTypeVariable cloneVar(AnnotatedTypeVariable v) {
        if (v instanceof AnnotatedTypeVariableImpl) {
            return v;
        }
        return new AnnotatedTypeVariableImpl((TypeVariable<?>) v.getType(), v.getAnnotations());
    }
}
