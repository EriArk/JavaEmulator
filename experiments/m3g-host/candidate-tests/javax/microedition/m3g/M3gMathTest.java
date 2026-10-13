package javax.microedition.m3g;

import org.junit.Test;
import static org.junit.Assert.*;

public class M3gMathTest {
    @Test public void angleUsesPlatformMathInEveryQuadrant() {
        for (float y : new float[]{-100, -1, -0.01f, 0.01f, 1, 100}) {
            for (float x : new float[]{-100, -1, -0.01f, 0.01f, 1, 100}) {
                assertEquals((float) Math.atan2(y, x), M3GMath.atan2(y, x), 0);
            }
        }
    }

    @Test public void anglePreservesSignedZeroAndNonFiniteInputs() {
        float[] values = {0f, -0f, Float.MIN_VALUE, -Float.MIN_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
        for (float y : values) for (float x : values) {
            assertEquals(Float.floatToIntBits((float) Math.atan2(y, x)),
                    Float.floatToIntBits(M3GMath.atan2(y, x)));
        }
    }
}
