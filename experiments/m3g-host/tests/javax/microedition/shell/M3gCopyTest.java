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

/** Public JSR-184 reference and duplication contracts, run against both engines. */
@RunWith(AndroidJUnit4.class)
public class M3gCopyTest {
    @Before public void setup() { Graphics3D.initGraphics3D(); }

    @Test public void nodeCopySharesAnimationTrackButNotTrackList() {
        Group node = new Group();
        AnimationTrack track = new AnimationTrack(
                new KeyframeSequence(2, 1, KeyframeSequence.LINEAR), AnimationTrack.ALPHA);
        node.addAnimationTrack(track);
        Group copy = (Group) node.duplicate();
        assertSame(track, copy.getAnimationTrack(0));
        assertEquals(1, references(copy, track));
        copy.removeAnimationTrack(track);
        assertEquals(0, copy.getAnimationTrackCount());
        assertSame(track, node.getAnimationTrack(0));
    }

    @Test public void trackCopyReferencesSequenceWithoutChangingOriginal() {
        KeyframeSequence sequence = new KeyframeSequence(2, 1, KeyframeSequence.LINEAR);
        AnimationTrack track = new AnimationTrack(sequence, AnimationTrack.ALPHA);
        AnimationController controller = new AnimationController();
        track.setController(controller);
        AnimationTrack copy = (AnimationTrack) track.duplicate();
        assertSame(sequence, copy.getKeyframeSequence());
        assertSame(controller, copy.getController());
        assertEquals(1, references(copy, sequence));
        assertEquals(1, references(copy, controller));
        assertEquals(1, references(track, sequence));
        assertEquals(2, track.getReferences(null));
    }

    @Test public void repeatedTextureReferencesSurviveRemovingOneSlot() {
        Texture2D texture = new Texture2D(new Image2D(Image2D.RGB, 2, 2));
        texture.setUserID(71);
        Appearance appearance = new Appearance();
        appearance.setTexture(0, texture);
        appearance.setTexture(1, texture);
        assertEquals(2, references(appearance, texture));
        Appearance copy = (Appearance) appearance.duplicate();
        assertEquals(2, references(copy, texture));
        copy.setTexture(0, null);
        assertSame(texture, copy.find(71));
        assertEquals(1, references(copy, texture));
        assertEquals(2, references(appearance, texture));
    }

    @Test public void sharedVertexArraySurvivesRemovingNormals() {
        VertexArray array = new VertexArray(3, 3, 2);
        array.setUserID(72);
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(array, 1, null);
        buffer.setNormals(array);
        assertEquals(2, references(buffer, array));
        VertexBuffer copy = (VertexBuffer) buffer.duplicate();
        copy.setNormals(null);
        assertSame(array, copy.find(72));
        assertEquals(1, references(copy, array));
        assertSame(array, buffer.getNormals());
    }

    @Test public void meshCopyPreservesRepeatedSharedResources() {
        IndexBuffer indices = new TriangleStripArray(0, new int[]{3});
        Appearance appearance = new Appearance();
        Mesh mesh = new Mesh(vertices(), new IndexBuffer[]{indices, indices},
                new Appearance[]{appearance, appearance});
        Mesh copy = (Mesh) mesh.duplicate();
        assertSame(mesh.getVertexBuffer(), copy.getVertexBuffer());
        assertEquals(2, references(copy, indices));
        assertEquals(2, references(copy, appearance));
        copy.setAppearance(0, null);
        assertSame(appearance, copy.getAppearance(1));
        assertEquals(1, references(copy, appearance));
        assertSame(appearance, mesh.getAppearance(0));
    }

    @Test public void siblingAlignmentIsRemappedAfterWholeTreeIsCopied() {
        Group root = new Group();
        Group first = new Group();
        Group second = new Group();
        root.addChild(first);
        root.addChild(second);
        first.setAlignment(second, Node.Z_AXIS, null, Node.Y_AXIS);
        second.setAlignment(null, Node.Z_AXIS, first, Node.Y_AXIS);
        Group copy = (Group) root.duplicate();
        assertSame(copy.getChild(1), copy.getChild(0).getAlignmentReference(Node.Z_AXIS));
        assertSame(copy.getChild(0), copy.getChild(1).getAlignmentReference(Node.Y_AXIS));
        assertEquals(Node.Z_AXIS, copy.getChild(0).getAlignmentTarget(Node.Z_AXIS));
        assertSame(second, first.getAlignmentReference(Node.Z_AXIS));
        assertEquals(0, first.getReferences(null));
        assertNull(copy.getParent());
    }

    @Test public void subtreeCopyKeepsExternalAlignmentReference() {
        Group external = new Group();
        Group branch = new Group();
        external.addChild(branch);
        branch.setAlignment(external, Node.ORIGIN, null, Node.NONE);
        Group copy = (Group) branch.duplicate();
        assertSame(external, copy.getAlignmentReference(Node.Z_AXIS));
        assertEquals(Node.ORIGIN, copy.getAlignmentTarget(Node.Z_AXIS));
        assertNull(copy.getParent());
        assertEquals(0, copy.getReferences(null));
    }

    @Test public void skeletonAlignmentCanReferenceCopiedOuterNodes() {
        Group root = new Group();
        Group target = new Group();
        Group skeleton = new Group();
        Group bone = new Group();
        skeleton.addChild(bone);
        SkinnedMesh mesh = new SkinnedMesh(vertices(),
                new TriangleStripArray(0, new int[]{3}), new Appearance(), skeleton);
        mesh.addTransform(bone, 1, 0, 3);
        root.addChild(mesh);
        root.addChild(target);
        bone.setAlignment(target, Node.Z_AXIS, null, Node.NONE);
        target.setAlignment(bone, Node.Z_AXIS, null, Node.NONE);
        Group copy = (Group) root.duplicate();
        SkinnedMesh copiedMesh = (SkinnedMesh) copy.getChild(0);
        Node copiedBone = copiedMesh.getSkeleton().getChild(0);
        assertSame(copy.getChild(1), copiedBone.getAlignmentReference(Node.Z_AXIS));
        assertSame(copiedBone, copy.getChild(1).getAlignmentReference(Node.Z_AXIS));
        assertSame(copiedMesh, copiedMesh.getSkeleton().getParent());
        assertEquals(3, copiedMesh.getBoneVertices(copiedBone, null, null));
        assertSame(target, bone.getAlignmentReference(Node.Z_AXIS));
    }

    @Test public void worldCameraCopyIsChildAndSeparateReference() {
        World world = new World();
        Camera camera = new Camera();
        camera.setUserID(73);
        world.addChild(camera);
        world.setActiveCamera(camera);
        assertEquals(2, references(world, camera));
        World copy = (World) world.duplicate();
        Camera copiedCamera = (Camera) copy.getChild(0);
        assertSame(copiedCamera, copy.getActiveCamera());
        assertEquals(2, references(copy, copiedCamera));
        copy.setActiveCamera(new Camera());
        assertSame(copiedCamera, copy.find(73));
        assertEquals(1, references(copy, copiedCamera));
        assertSame(camera, world.getActiveCamera());
    }

    @Test public void nullCameraIsRejectedWithoutLosingExistingReference() {
        assertNull(((World) new World().duplicate()).getActiveCamera());
        World world = new World();
        Camera camera = new Camera();
        world.setActiveCamera(camera);
        try {
            world.setActiveCamera(null);
            fail("Null active camera must be rejected");
        } catch (NullPointerException expected) { }
        assertSame(camera, world.getActiveCamera());
        assertEquals(1, references(world, camera));
    }

    @Test public void loaderAcceptsWorldWithoutAnActiveCamera() throws Exception {
        // Original minimal file: one header section and one childless World.
        byte[] magic = {(byte) 0xab, 0x4a, 0x53, 0x52, 0x31, 0x38,
                0x34, (byte) 0xbb, 13, 10, 26, 10};
        ByteBuffer world = littleEndian(39);
        world.put((byte) 22).putInt(34);
        world.putInt(74).putInt(0).putInt(0); // Object3D
        world.put((byte) 0).put((byte) 0); // Transformable
        world.put((byte) 1).put((byte) 1).put((byte) 255).putInt(-1).put((byte) 0);
        world.putInt(0).putInt(0).putInt(0); // children, camera, background
        byte[] worldSection = section(world.array());
        ByteBuffer header = littleEndian(17);
        header.put((byte) 0).putInt(12);
        header.put((byte) 1).put((byte) 0).put((byte) 0);
        header.putInt(94).putInt(1024).put((byte) 0);
        byte[] file = littleEndian(94).put(magic).put(section(header.array())).put(worldSection).array();
        Object3D[] objects = Loader.load(file, 0);
        assertEquals(1, objects.length);
        assertTrue(objects[0] instanceof World);
        assertNull(((World) objects[0]).getActiveCamera());
        assertEquals(74, objects[0].getUserID());
    }

    private static ByteBuffer littleEndian(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] section(byte[] payload) {
        ByteBuffer section = littleEndian(payload.length + 13);
        section.put((byte) 0).putInt(section.capacity()).putInt(payload.length).put(payload);
        Adler32 checksum = new Adler32();
        checksum.update(section.array(), 0, section.position());
        return section.putInt((int) checksum.getValue()).array();
    }

    private static VertexBuffer vertices() {
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(new VertexArray(3, 3, 2), 1, null);
        return buffer;
    }

    private static int references(Object3D owner, Object3D target) {
        Object3D[] references = new Object3D[owner.getReferences(null)];
        assertEquals(references.length, owner.getReferences(references));
        int count = 0;
        for (Object3D reference : references) if (reference == target) count++;
        return count;
    }
}
