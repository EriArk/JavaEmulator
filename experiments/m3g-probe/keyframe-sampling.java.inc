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
