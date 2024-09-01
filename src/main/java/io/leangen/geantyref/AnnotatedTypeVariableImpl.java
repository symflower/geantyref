/*
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or at <a href="http://www.apache.org/licenses/LICENSE-2">apache.org</a>.
 */

package io.leangen.geantyref;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.TypeVariable;

/*Implementation note #IMPLTNOTE1:
* Currently the principle everywhere is not to replace the underlying variable unless the bounds are being resolved.
* The annotations are never replaced on the TypeVariable itself. Not even when replacing the bounds.
* Find all places with #IMPLTNOTE1 if this principle changes.
*/
class AnnotatedTypeVariableImpl extends AnnotatedTypeImpl implements AnnotatedTypeVariable {

    private AnnotatedType[] annotatedBounds;

    AnnotatedTypeVariableImpl(TypeVariable<?> type) {
        this(type, type.getAnnotations());
    }

    AnnotatedTypeVariableImpl(TypeVariable<?> type, Annotation[] annotations) {
        super(type, annotations, null);
        AnnotatedType[] annotatedBounds =  type.getAnnotatedBounds();
        if (annotatedBounds == null || annotatedBounds.length == 0) {
            annotatedBounds = new AnnotatedType[0];
        }
        this.annotatedBounds = annotatedBounds;
    }

    AnnotatedTypeVariableImpl init(AnnotatedType[] annotatedBounds) {
        this.type = new TypeVariableImpl<>((TypeVariable<?>) this.type, /*#IMPLTNOTE1 this.getAnnotations(),*/ annotatedBounds);
        this.annotatedBounds = annotatedBounds;
        return this;
    }

    AnnotatedTypeVariableImpl setAnnotations(Annotation[] annotations) {
        //#IMPLTNOTE1 this.type = new TypeVariableImpl<>((TypeVariable<?>) this.type, annotations, this.annotatedBounds);
        this.annotations = toMap(annotations);
        return this;
    }

    @Override
    public AnnotatedType[] getAnnotatedBounds() {
        return annotatedBounds.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnnotatedTypeVariable && super.equals(other);
    }

    @Override
    public String toString() {
        return annotationsString() + ((TypeVariable<?>) type).getName();
    }
}
