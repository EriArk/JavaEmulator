// SPDX-License-Identifier: GPL-3.0-or-later
package javax.microedition.m3g;

/** JSR-184 interpolation preserves quaternion signs, including rotations over 180 degrees. */
final class AnimationQuaternion {
    private AnimationQuaternion() { }

    static void slerp(float[] out, float s, float[] from, float[] to) {
        double dot = 0;
        for (int i = 0; i < 4; i++) dot += (double) from[i] * to[i];
        double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
        double sine = Math.sin(angle);
        double left = 1 - s, right = s;
        if (Math.abs(sine) > 1e-8) {
            left = Math.sin((1 - s) * angle) / sine;
            right = Math.sin(s * angle) / sine;
        }
        for (int i = 0; i < 4; i++) out[i] = (float) (left * from[i] + right * to[i]);
    }

    static void logDelta(float[] out, float[] from, float[] to) {
        double x = from[3]*to[0] - from[0]*to[3] - from[1]*to[2] + from[2]*to[1];
        double y = from[3]*to[1] - from[1]*to[3] - from[2]*to[0] + from[0]*to[2];
        double z = from[3]*to[2] - from[2]*to[3] - from[0]*to[1] + from[1]*to[0];
        double w = from[3]*to[3] + from[0]*to[0] + from[1]*to[1] + from[2]*to[2];
        double length = Math.sqrt(x*x + y*y + z*z);
        double factor = length > 1e-12 ? Math.atan2(length, w) / length : 0;
        out[0] = (float) (x * factor);
        out[1] = (float) (y * factor);
        out[2] = (float) (z * factor);
    }

    static void multiplyExp(float[] out, float[] q, float[] log) {
        double angle = Math.sqrt((double) log[0]*log[0] + (double) log[1]*log[1] + (double) log[2]*log[2]);
        double factor = angle > 1e-12 ? Math.sin(angle) / angle : 1;
        double x = log[0] * factor, y = log[1] * factor, z = log[2] * factor, w = Math.cos(angle);
        out[0] = (float) (q[3]*x + q[0]*w + q[1]*z - q[2]*y);
        out[1] = (float) (q[3]*y + q[1]*w + q[2]*x - q[0]*z);
        out[2] = (float) (q[3]*z + q[2]*w + q[0]*y - q[1]*x);
        out[3] = (float) (q[3]*w - q[0]*x - q[1]*y - q[2]*z);
    }
}
