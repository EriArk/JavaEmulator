package javax.microedition.shell;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Adler32;
import javax.microedition.m3g.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Original fixtures derived from the public JSR-184 interpolation equations. */
@RunWith(AndroidJUnit4.class)
public class M3gAnimationTest {
    @Before public void setup() { Graphics3D.initGraphics3D(); }

    @Test public void loopInterpolatesAcrossTheLastToFirstGap() {
        KeyframeSequence keys = scalar(KeyframeSequence.LINEAR, new int[]{20, 80}, new float[]{2, 8}, 100);
        keys.setRepeatMode(KeyframeSequence.LOOP);
        Group node = target(keys, AnimationTrack.SCALE);
        for (int time : new int[]{0, 100, -100}) assertScale(node, time, 5);
        assertScale(node, 90, 6.5f);
        assertScale(node, -10, 6.5f);
    }

    @Test public void stepLoopUsesPreviousCycleBeforeFirstKey() {
        KeyframeSequence keys = scalar(KeyframeSequence.STEP, new int[]{20, 80}, new float[]{2, 8}, 100);
        keys.setRepeatMode(KeyframeSequence.LOOP);
        Group node = target(keys, AnimationTrack.SCALE);
        assertScale(node, 0, 8);
        assertScale(node, 20, 2);
        assertScale(node, 80, 8);
    }

    @Test public void circularValidRangeIgnoresUnusedFrames() {
        KeyframeSequence keys = scalar(KeyframeSequence.LINEAR, new int[]{100, 200, 999, 0},
                new float[]{10, 20, 999, 0}, 200);
        keys.setValidRange(3, 1);
        Group node = target(keys, AnimationTrack.SCALE);
        assertScale(node, 50, 5);
        assertScale(node, 150, 15);
        assertScale(node, 250, 20);
    }

    @Test public void validRangeRejectsBadIndicesWithTheSpecifiedException() {
        KeyframeSequence keys = scalar(KeyframeSequence.LINEAR, new int[]{0, 100}, new float[]{0, 1}, 100);
        try { keys.setValidRange(-1, 1); fail(); } catch (IndexOutOfBoundsException expected) { }
        try { keys.setValidRange(0, 2); fail(); } catch (IndexOutOfBoundsException expected) { }
        assertEquals(0, keys.getValidRangeFirst());
        assertEquals(1, keys.getValidRangeLast());
    }

    @Test public void coincidentFramesPreserveCutsAndRandomAccess() {
        Group node = target(scalar(KeyframeSequence.LINEAR, new int[]{0, 50, 50, 100},
                new float[]{0, 5, 15, 20}, 100), AnimationTrack.SCALE);
        assertScale(node, 75, 17.5f);
        assertScale(node, 25, 2.5f);
        assertScale(node, 50, 15);
    }

    @Test public void splineUsesTimeWeightedTangents() {
        Group node = target(curve(KeyframeSequence.SPLINE), AnimationTrack.SCALE);
        assertScale(node, 150, 8.90625f);
        assertScale(node, 50, 2.25f);
    }

    @Test public void changingRepeatModeRebuildsSplineEndpoints() {
        KeyframeSequence keys = curve(KeyframeSequence.SPLINE);
        Group node = target(keys, AnimationTrack.SCALE);
        assertScale(node, 50, 2.25f);
        keys.setRepeatMode(KeyframeSequence.LOOP);
        assertScale(node, 50, .9375f);
        keys.setRepeatMode(KeyframeSequence.CONSTANT);
        assertScale(node, 50, 2.25f);
    }

    @Test public void editsAndCopiesHaveIndependentSplineCaches() {
        KeyframeSequence keys = curve(KeyframeSequence.SPLINE);
        Group original = target(keys, AnimationTrack.SCALE);
        assertScale(original, 150, 8.90625f);
        KeyframeSequence copy = (KeyframeSequence) keys.duplicate();
        Group duplicate = target(copy, AnimationTrack.SCALE);
        copy.setKeyframe(3, 400, new float[]{37});
        assertScale(duplicate, 150, 8.59375f);
        assertScale(original, 150, 8.90625f);
    }

    @Test public void slerpDoesNotReverseAQuaternionEncodedLongRotation() {
        KeyframeSequence keys = rotations(KeyframeSequence.SLERP, new int[]{0, 100}, new float[]{0, 270});
        Group node = target(keys, AnimationTrack.ORIENTATION);
        assertRotation(node, 25, 67.5f);
        assertRotation(node, 50, 135);
    }

    @Test public void squadUsesQuaternionTangentsAndConstantEndpoints() {
        KeyframeSequence keys = rotations(KeyframeSequence.SQUAD,
                new int[]{0, 100, 300, 400}, new float[]{0, 60, 180, 270});
        Group node = target(keys, AnimationTrack.ORIENTATION);
        assertRotation(node, 150, 89.0625f);
        assertRotation(node, 50, 22.5f);
    }

    @Test public void squadLoopClosesAcrossTheBoundary() {
        KeyframeSequence keys = rotations(KeyframeSequence.SQUAD,
                new int[]{0, 100, 300, 400}, new float[]{0, 60, 180, 270});
        keys.setRepeatMode(KeyframeSequence.LOOP);
        Group node = target(keys, AnimationTrack.ORIENTATION);
        assertRotation(node, 50, 9.375f);
        assertRotation(node, 550, 9.375f);
    }

    @Test public void unsetDurationIsRejectedWhenAnimationIsApplied() {
        KeyframeSequence keys = new KeyframeSequence(1, 1, KeyframeSequence.LINEAR);
        expectInvalid(keys);
    }

    @Test public void decreasingTimesAreRejectedButCanBeRepaired() {
        KeyframeSequence keys = scalar(KeyframeSequence.LINEAR, new int[]{80, 20}, new float[]{8, 2}, 100);
        expectInvalid(keys);
        keys.setKeyframe(0, 0, new float[]{0});
        assertScale(target(keys, AnimationTrack.SCALE), 10, 1);
    }

    @Test public void durationMustCoverTheLastValidKeyframe() {
        expectInvalid(scalar(KeyframeSequence.LINEAR, new int[]{0, 200}, new float[]{0, 2}, 100));
    }

    @Test public void reversePlaybackDoesNotSleepThroughAnEarlierKeyframe() {
        Group node = target(scalar(KeyframeSequence.STEP, new int[]{0, 100}, new float[]{1, 2}, 100), AnimationTrack.SCALE);
        AnimationController controller = node.getAnimationTrack(0).getController();
        controller.setSpeed(-1, 0);
        controller.setPosition(101, 0);
        assertScale(node, 0, 2);
        assertTrue(node.animate(0) <= 2);
        assertScale(node, 2, 1);
    }

    @Test public void fractionalSpeedDoesNotOverstateContinuousValidity() {
        Group node = target(scalar(KeyframeSequence.LINEAR, new int[]{0, 100}, new float[]{0, 10}, 100), AnimationTrack.SCALE);
        node.getAnimationTrack(0).getController().setSpeed(.1f, 0);
        assertTrue(node.animate(10) <= 1);
        assertScale(node, 20, .2f);
    }

    @Test public void stoppedControllerAndIntervalBoundariesPreserveState() {
        Group node = target(scalar(KeyframeSequence.LINEAR, new int[]{0, 100}, new float[]{0, 10}, 100), AnimationTrack.SCALE);
        AnimationController controller = node.getAnimationTrack(0).getController();
        controller.setActiveInterval(20, 80);
        node.setScale(7, 7, 7);
        assertScale(node, 19, 7);
        assertScale(node, 20, 2);
        controller.setSpeed(0, 30);
        assertScale(node, 50, 3);
        assertScale(node, 80, 3);
    }

    @Test public void singleKeyLoopIsConstantAtAnyTime() {
        KeyframeSequence keys = scalar(KeyframeSequence.SPLINE, new int[]{20}, new float[]{7}, 100);
        keys.setRepeatMode(KeyframeSequence.LOOP);
        Group node = target(keys, AnimationTrack.SCALE);
        assertScale(node, -100, 7);
        assertScale(node, 35, 7);
    }

    @Test public void controllerPositionDoesNotWrapAcrossSignedWorldTimes() {
        AnimationController controller = new AnimationController();
        controller.setPosition(0, Integer.MIN_VALUE);
        assertEquals((float) ((long) Integer.MAX_VALUE - Integer.MIN_VALUE),
                controller.getPosition(Integer.MAX_VALUE), 1);
        controller.setPosition(0, Integer.MAX_VALUE);
        assertEquals((float) ((long) Integer.MIN_VALUE - Integer.MAX_VALUE),
                controller.getPosition(Integer.MIN_VALUE), 1);
    }

    @Test public void slerpBetweenDifferentAxesProducesTheExpectedTransform() {
        KeyframeSequence keys = new KeyframeSequence(2, 4, KeyframeSequence.SLERP);
        float half = (float) Math.sqrt(.5);
        keys.setKeyframe(0, 0, new float[]{half, 0, 0, half});
        keys.setKeyframe(1, 100, new float[]{0, half, 0, half});
        keys.setDuration(100);
        Group node = target(keys, AnimationTrack.ORIENTATION);
        node.animate(50);
        node.setTranslation(3, 4, 5);
        node.setScale(2, 2, 2);
        Transform transform = new Transform();
        node.getCompositeTransform(transform);
        float[] vector = {1, 0, 0, 1};
        transform.transform(vector);
        assertArrayEquals(new float[]{3 + 4f/3, 4 + 2f/3, 5 - 4f/3, 1}, vector, .003f);
    }

    @Test public void multipleTracksBlendWithoutChangingTheirControllers() {
        Group node = target(scalar(KeyframeSequence.LINEAR, new int[]{0, 100}, new float[]{0, 10}, 100), AnimationTrack.SCALE);
        AnimationTrack first = node.getAnimationTrack(0);
        first.getController().setWeight(1);
        AnimationTrack second = new AnimationTrack(scalar(KeyframeSequence.LINEAR,
                new int[]{0, 100}, new float[]{10, 20}, 100), AnimationTrack.SCALE);
        AnimationController controller = new AnimationController();
        controller.setWeight(3);
        second.setController(controller);
        node.addAnimationTrack(second);
        assertScale(node, 50, 50);
        controller.setWeight(0);
        assertScale(node, 50, 5);
        assertEquals(1, first.getController().getWeight(), 0);
        first.getController().setWeight(.5f);
        assertScale(node, 50, 2.5f);
    }

    @Test public void loaderPreservesCircularRanges() throws Exception {
        Object3D[] loaded = Loader.load(circularSequenceFile(0, 100, 100), 0);
        KeyframeSequence keys = (KeyframeSequence) loaded[0];
        assertEquals(2, keys.getValidRangeFirst());
        assertScale(target(keys, AnimationTrack.SCALE), 50, 5);
    }

    @Test public void loaderRejectsInvalidSequencesBeforeAnimation() throws Exception {
        for (byte[] file : new byte[][]{circularSequenceFile(100, 20, 100), circularSequenceFile(0, 200, 100)}) {
            try { Loader.load(file, 0); fail("Invalid sequence file"); }
            catch (java.io.IOException expected) { }
        }
    }

    private static byte[] circularSequenceFile(int firstTime, int lastTime, int duration) {
        ByteBuffer body = bytes(59);
        body.putInt(84).putInt(0).putInt(0);
        body.put((byte) KeyframeSequence.LINEAR).put((byte) KeyframeSequence.CONSTANT).put((byte) 0);
        body.putInt(duration).putInt(2).putInt(0).putInt(1).putInt(3);
        body.putInt(lastTime).putFloat(10).putInt(999).putFloat(999).putInt(firstTime).putFloat(0);
        byte[] payload = bytes(64).put((byte) 19).putInt(59).put(body.array()).array();
        byte[] section = section(payload);
        int total = 12 + 30 + section.length;
        ByteBuffer header = bytes(17).put((byte) 0).putInt(12).put((byte) 1).put((byte) 0).put((byte) 0);
        header.putInt(total).putInt(1024).put((byte) 0);
        byte[] magic = {(byte) 0xab, 0x4a, 0x53, 0x52, 0x31, 0x38, 0x34, (byte) 0xbb, 13, 10, 26, 10};
        return bytes(total).put(magic).put(section(header.array())).put(section).array();
    }

    private static KeyframeSequence curve(int mode) {
        return scalar(mode, new int[]{0, 100, 300, 400}, new float[]{0, 6, 18, 27}, 500);
    }
    private static KeyframeSequence scalar(int mode, int[] times, float[] values, int duration) {
        KeyframeSequence keys = new KeyframeSequence(times.length, 1, mode);
        for (int i = 0; i < times.length; i++) keys.setKeyframe(i, times[i], new float[]{values[i]});
        keys.setDuration(duration);
        return keys;
    }
    private static KeyframeSequence rotations(int mode, int[] times, float[] degrees) {
        KeyframeSequence keys = new KeyframeSequence(times.length, 4, mode);
        for (int i = 0; i < times.length; i++) {
            double angle = Math.toRadians(degrees[i]) * .5;
            keys.setKeyframe(i, times[i], new float[]{0, 0, (float) Math.sin(angle), (float) Math.cos(angle)});
        }
        keys.setDuration(500);
        return keys;
    }
    private static Group target(KeyframeSequence keys, int property) {
        AnimationTrack track = new AnimationTrack(keys, property);
        track.setController(new AnimationController());
        Group node = new Group();
        node.addAnimationTrack(track);
        return node;
    }
    private static void expectInvalid(KeyframeSequence keys) {
        try { target(keys, AnimationTrack.SCALE).animate(0); fail("Invalid active sequence"); }
        catch (IllegalStateException expected) { }
    }
    private static void assertScale(Group node, int time, float expected) {
        node.animate(time);
        float[] scale = new float[3];
        node.getScale(scale);
        assertArrayEquals(new float[]{expected, expected, expected}, scale, .003f);
    }
    private static void assertRotation(Group node, int time, float degrees) {
        node.animate(time);
        Transform transform = new Transform();
        node.getCompositeTransform(transform);
        float[] vector = {1, 0, 0, 1};
        transform.transform(vector);
        assertEquals((float) Math.cos(Math.toRadians(degrees)), vector[0], .003f);
        assertEquals((float) Math.sin(Math.toRadians(degrees)), vector[1], .003f);
    }
    private static ByteBuffer bytes(int size) { return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN); }
    private static byte[] section(byte[] payload) {
        ByteBuffer result = bytes(payload.length + 13).put((byte) 0).putInt(payload.length + 13).putInt(payload.length).put(payload);
        Adler32 checksum = new Adler32();
        checksum.update(result.array(), 0, result.position());
        return result.putInt((int) checksum.getValue()).array();
    }
}
