    // SPDX-License-Identifier: GPL-3.0-or-later
    public int getBoneVertices(Node bone, int[] indices, float[] weights) {
        if (bone == null) throw new NullPointerException("bone");
        if (bone != skeleton && !isChildOf(skeleton, bone)) {
            throw new IllegalArgumentException("Bone is outside the skeleton");
        }
        int end = 0;
        for (BoneData data : bones) {
            if (data.bone == bone) end = Math.max(end, data.firstVertex + data.numVertices);
        }
        double[] contributions = new double[end];
        for (BoneData data : bones) {
            if (data.bone == bone) {
                for (int i = data.firstVertex; i < data.firstVertex + data.numVertices; i++) {
                    contributions[i] += data.weight;
                }
            }
        }
        int count = 0;
        for (double weight : contributions) if (weight > 0) count++;
        if (indices == null || weights == null) return count;
        if (indices.length < count || weights.length < count) {
            throw new IllegalArgumentException("Bone query arrays are too small");
        }
        int index = 0;
        for (int vertex = 0; vertex < end; vertex++) {
            if (contributions[vertex] > 0) {
                indices[index] = vertex;
                weights[index++] = (float) (contributions[vertex] / totalWeightForVertex(vertex));
            }
        }
        return count;
    }
