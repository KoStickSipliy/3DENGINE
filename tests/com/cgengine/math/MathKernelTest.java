package com.cgengine.math;

import com.cgengine.math.affine.Translator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MathKernelTest {
    private static final float EPSILON = 0.00001f;

    @Test void vectorOperationsHaveExpectedResults() {
        assertEquals(new Vector3f(0, 0, 1), VectorMath.cross(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0)));
        assertEquals(32, VectorMath.dot(new Vector3f(1, 2, 3), new Vector3f(4, 5, 6)), EPSILON);
        assertEquals(new Vector3f(0, 1, 0), VectorMath.normalize(new Vector3f(0, 2, 0)));
    }

    @Test void matrixUsesColumnVectorConvention() {
        assertEquals(new Vector3f(6, 0, 5), new Translator(5, -2, 2).transform(new Vector3f(1, 2, 3)));
        assertEquals(new Vector4f(1, 2, 3, 1), MatrixMath.multiply(Matrix4f.identity(), new Vector4f(1, 2, 3, 1)));
    }

    @Test void invalidNormalizationAndPerspectiveDivideAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VectorMath.normalize(new Vector3f(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> MatrixMath.perspectiveDivide(new Vector4f(1, 2, 3, 0)));
    }
}
