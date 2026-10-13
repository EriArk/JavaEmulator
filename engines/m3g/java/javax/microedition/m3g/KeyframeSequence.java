/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package javax.microedition.m3g;

public class KeyframeSequence extends Object3D
{

	public static final int CONSTANT = 192;
	public static final int LINEAR = 176;
	public static final int LOOP = 193;
	public static final int SLERP = 177;
	public static final int SPLINE = 178;
	public static final int SQUAD = 179;
	public static final int STEP = 180;


	private int duration;
	private int intType;
	private int keyframes;
	private int repeat = CONSTANT;
	private int rangeFirst;
	private int rangeLast;
	private int componentCount;
	private int[] orderedFrames;
	private boolean dirty = true;

	private float[][] keyFrames;
	private float[][] inTangents;
	private float[][] outTangents;
	private int[] keyFrameTimes;
	private float[][] a;
	private float[][] b;

	private final float[] tempQ0 = new float[4];
	private final float[] tempQ1 = new float[4];
	private final float[] tempS0 = new float[4];
	private final float[] tempS1 = new float[4];

	public KeyframeSequence(int numKeyframes, int numComponents, int interpolation)
	{
		if (numKeyframes < 1 || numComponents < 1)
		{
			throw new IllegalArgumentException("Number of keyframes and components must be >= 1");
		}

		switch (interpolation)
		{
			case SLERP:
			case SQUAD:
				if (numComponents != 4)
					throw new IllegalArgumentException("SLERP and SQUAD require 4 components");
				if (interpolation == SQUAD) {
					a = new float[numKeyframes][4];
					b = new float[numKeyframes][4];
				}
				break;
			case STEP:
			case LINEAR:
				break;
			case SPLINE:
				inTangents = new float[numKeyframes][numComponents];
				outTangents = new float[numKeyframes][numComponents];
				break;
			default:
				throw new IllegalArgumentException("Unknown interpolation mode");
		}

		this.keyframes = numKeyframes;
		this.componentCount = numComponents;
		this.intType = interpolation;

		keyFrames = new float[numKeyframes][numComponents];
		keyFrameTimes = new int[numKeyframes];
		rangeFirst = 0;
		rangeLast = numKeyframes - 1;
	}

	protected Object3D duplicateImpl()
	{
		KeyframeSequence copy = (KeyframeSequence) super.duplicateImpl();

		copy.duration = this.duration;
		copy.intType = this.intType;
		copy.keyframes = this.keyframes;
		copy.repeat = this.repeat;
		copy.rangeFirst = this.rangeFirst;
		copy.rangeLast = this.rangeLast;
		copy.componentCount = this.componentCount;
		copy.dirty = true;

		copy.keyFrames = new float[keyframes][componentCount];
		for (int i = 0; i < keyframes; i++) {
			System.arraycopy(this.keyFrames[i], 0, copy.keyFrames[i], 0, componentCount);
		}

		copy.keyFrameTimes = this.keyFrameTimes.clone();

		if (inTangents != null) {
			copy.inTangents = new float[keyframes][componentCount];
			copy.outTangents = new float[keyframes][componentCount];
		}

		if (a != null) {
			copy.a = new float[keyframes][4];
			copy.b = new float[keyframes][4];
		}

		return copy;
	}

	public int getComponentCount() { return this.componentCount; }

	public int getDuration() { return duration; }

	public int getInterpolationType() { return intType; }

	public int getKeyframeCount() { return keyframes; }

	public int getRepeatMode() { return repeat; }

	public int getValidRangeFirst() { return rangeFirst; }

	public int getValidRangeLast() { return rangeLast; }

	public void setDuration(int value)
	{
		if (value <= 0) {
			throw new IllegalArgumentException("Duration must be positive");
		}
		this.duration = value;
		this.dirty = true;
	}

	public void setRepeatMode(int mode) {
		if (mode != CONSTANT && mode != LOOP) {
			throw new IllegalArgumentException("Invalid repeat mode");
		}
		this.repeat = mode;
        this.dirty = true;
	}

	public void setValidRange(int first, int last)
	{
		if (first < 0 || first >= keyframes || last < 0 || last >= keyframes)
		{
			throw new IndexOutOfBoundsException("Invalid keyframe range");
		}
		this.rangeFirst = first;
		this.rangeLast = last;
		this.dirty = true;
	}

	public void setKeyframe(int index, int time, float[] value)
	{
		if (value == null) { throw new NullPointerException("Value vector must not be null"); }
		if (index < 0 || index >= keyframes) { throw new IndexOutOfBoundsException(); }
		if (value.length < componentCount || time < 0) { throw new IllegalArgumentException(); }

		System.arraycopy(value, 0, keyFrames[index], 0, componentCount);
		keyFrameTimes[index] = time;

		if (intType == SLERP || intType == SQUAD)
		{
			M3GMath.normalizeQuat(keyFrames[index]);
		}
		dirty = true;
	}

	public int getKeyframe(int index, float[] value)
	{
		if (index < 0 || index >= keyframes) { throw new IndexOutOfBoundsException(); }
		if (value != null && value.length < componentCount) { throw new IllegalArgumentException(); }

		if (value != null) {
			System.arraycopy(keyFrames[index], 0, value, 0, componentCount);
		}

		return keyFrameTimes[index];
	}

    // SPDX-License-Identifier: GPL-3.0-or-later
    public int getSample(float sequenceTime, float[] sample) {
        if (sample == null || sample.length < componentCount) {
            throw new IllegalArgumentException("Sample array is null or too small");
        }
        validateSequence();
        int count = orderedFrames.length;
        double time = sequenceTime;
        if (repeat == LOOP) {
            time %= duration;
            if (time < 0) time += duration;
        }
        if (count == 1 || (repeat == CONSTANT && time >= keyFrameTimes[rangeLast])) {
            System.arraycopy(keyFrames[rangeLast], 0, sample, 0, componentCount);
            return Integer.MAX_VALUE;
        }
        if (repeat == CONSTANT && time < keyFrameTimes[rangeFirst]) {
            System.arraycopy(keyFrames[rangeFirst], 0, sample, 0, componentCount);
            return (int) Math.min(Integer.MAX_VALUE, Math.floor(keyFrameTimes[rangeFirst] - time));
        }

        // Upper bound selects the last coincident key on the outgoing side of a cut.
        int low = 0, high = count;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (keyFrameTimes[orderedFrames[mid]] <= time) low = mid + 1;
            else high = mid;
        }
        int start = orderedFrames[(low + count - 1) % count];
        int end = orderedFrames[low % count];
        double from = keyFrameTimes[start], to = keyFrameTimes[end];
        if (low == 0) from -= duration;
        if (low == count) to += duration;
        if (intType == STEP) {
            System.arraycopy(keyFrames[start], 0, sample, 0, componentCount);
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.floor(to - time)));
        }
        float s = (float) ((time - from) / (to - from));
        switch (intType) {
            case LINEAR:
                for (int c = 0; c < componentCount; c++) {
                    sample[c] = (1 - s) * keyFrames[start][c] + s * keyFrames[end][c];
                }
                break;
            case SLERP:
                AnimationQuaternion.slerp(sample, s, keyFrames[start], keyFrames[end]);
                break;
            case SPLINE:
                float s2 = s * s, s3 = s2 * s;
                for (int c = 0; c < componentCount; c++) {
                    sample[c] = (2*s3 - 3*s2 + 1) * keyFrames[start][c]
                            + (s3 - 2*s2 + s) * outTangents[start][c]
                            + (-2*s3 + 3*s2) * keyFrames[end][c]
                            + (s3 - s2) * inTangents[end][c];
                }
                break;
            case SQUAD:
                AnimationQuaternion.slerp(tempQ0, s, keyFrames[start], keyFrames[end]);
                AnimationQuaternion.slerp(tempQ1, s, a[start], b[end]);
                AnimationQuaternion.slerp(sample, 2*s*(1-s), tempQ0, tempQ1);
                break;
        }
        return 0;
    }

    void validateSequence() {
        if (!dirty) return;
        if (duration <= 0) throw new IllegalStateException("Sequence duration is not set");
        int count = rangeLast >= rangeFirst ? rangeLast - rangeFirst + 1
                : keyframes - rangeFirst + rangeLast + 1;
        int[] order = new int[count];
        int previousTime = -1;
        for (int i = 0; i < count; i++) {
            int index = (rangeFirst + i) % keyframes;
            int time = keyFrameTimes[index];
            if (time < previousTime || time > duration) {
                throw new IllegalStateException("Valid keyframe times must be ordered and within duration");
            }
            order[i] = index;
            previousTime = time;
        }
        if (intType == SPLINE || intType == SQUAD) {
            for (int i = 0; i < count; i++) {
                int current = order[i];
                int previous = order[(i + count - 1) % count];
                int next = order[(i + 1) % count];
                double before = keyFrameTimes[previous] - (i == 0 ? (double) duration : 0);
                double after = keyFrameTimes[next] + (i == count - 1 ? (double) duration : 0);
                double now = keyFrameTimes[current];
                boolean flat = (repeat == CONSTANT && (i == 0 || i == count - 1)) || after == before;
                float outgoing = flat ? 0 : (float) ((after - now) / (after - before));
                float incoming = flat ? 0 : (float) ((now - before) / (after - before));
                if (intType == SPLINE) {
                    for (int c = 0; c < componentCount; c++) {
                        float delta = keyFrames[next][c] - keyFrames[previous][c];
                        outTangents[current][c] = outgoing * delta;
                        inTangents[current][c] = incoming * delta;
                    }
                } else {
                    AnimationQuaternion.logDelta(tempS0, keyFrames[previous], keyFrames[current]);
                    AnimationQuaternion.logDelta(tempS1, keyFrames[current], keyFrames[next]);
                    for (int c = 0; c < 3; c++) {
                        float sum = tempS0[c] + tempS1[c];
                        tempQ0[c] = (outgoing * sum - tempS1[c]) * .5f;
                        tempQ1[c] = (tempS0[c] - incoming * sum) * .5f;
                    }
                    AnimationQuaternion.multiplyExp(a[current], keyFrames[current], tempQ0);
                    AnimationQuaternion.multiplyExp(b[current], keyFrames[current], tempQ1);
                }
            }
        }
        orderedFrames = order;
        dirty = false;
    }

}
