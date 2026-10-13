// SPDX-License-Identifier: GPL-3.0-or-later
package javax.microedition.m3g;

/** Immutable dependency snapshot; unchanged poses need no vertex-content scan. */
final class DeformationState {
    private final VertexBuffer buffer;
    final long bufferRevision;
    private final VertexArray[] arrays = new VertexArray[3 + Graphics3D.NUM_TEXTURE_UNITS];
    private final long[] revisions = new long[arrays.length];

    DeformationState(VertexBuffer buffer) {
        this.buffer = buffer;
        bufferRevision = buffer.revision;
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = array(buffer, i);
            revisions[i] = arrays[i] == null ? 0 : arrays[i].revision;
        }
    }

    boolean matches(VertexBuffer current) {
        if (current != buffer || current.revision != bufferRevision) return false;
        for (int i = 0; i < arrays.length; i++) {
            VertexArray value = array(current, i);
            if (value != arrays[i] || (value != null && value.revision != revisions[i])) return false;
        }
        return true;
    }

    boolean sameLayout(VertexBuffer current) {
        for (int i = 0; i < arrays.length; i++) {
            VertexArray old = arrays[i], value = array(current, i);
            if ((old == null) != (value == null)) return false;
            if (old != null && (old.getVertexCount() != value.getVertexCount()
                    || old.getComponentCount() != value.getComponentCount()
                    || old.getComponentType() != value.getComponentType())) return false;
        }
        return true;
    }

    private static VertexArray array(VertexBuffer buffer, int slot) {
        switch (slot) {
            case 0: return buffer.getPositions(null);
            case 1: return buffer.getNormals();
            case 2: return buffer.getColors();
            default: return buffer.getTexCoords(slot - 3, null);
        }
    }
}
