// SPDX-License-Identifier: GPL-3.0-or-later
package javax.microedition.m3g;

import java.util.IdentityHashMap;
import java.util.Map;

/** Repairs node-to-node links only after every descendant has its own copy. */
final class SceneGraphCopies {
    private SceneGraphCopies() { }

    static void remap(Node source, Node copy) {
        IdentityHashMap<Node, Node> copies = new IdentityHashMap<>();
        collect(source, copy, copies);
        for (Map.Entry<Node, Node> pair : copies.entrySet()) {
            Node original = pair.getKey();
            Node duplicate = pair.getValue();
            duplicate.setAlignment(reference(original.getAlignmentReference(Node.Z_AXIS), copies),
                    original.getAlignmentTarget(Node.Z_AXIS),
                    reference(original.getAlignmentReference(Node.Y_AXIS), copies),
                    original.getAlignmentTarget(Node.Y_AXIS));
            if (original instanceof World) {
                Camera camera = ((World) original).getActiveCamera();
                if (camera != null) {
                    ((World) duplicate).setActiveCamera((Camera) reference(camera, copies));
                }
            }
        }
    }

    private static Node reference(Node original, IdentityHashMap<Node, Node> copies) {
        Node copy = copies.get(original);
        return copy == null ? original : copy;
    }

    private static void collect(Node source, Node copy, IdentityHashMap<Node, Node> copies) {
        copies.put(source, copy);
        if (source instanceof Group) {
            Group group = (Group) source;
            Group copiedGroup = (Group) copy;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChild(i), copiedGroup.getChild(i), copies);
            }
        } else if (source instanceof SkinnedMesh) {
            collect(((SkinnedMesh) source).getSkeleton(),
                    ((SkinnedMesh) copy).getSkeleton(), copies);
        }
    }
}
