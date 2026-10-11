package io.github.eriark.m3gprobe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.DeflaterOutputStream;
import javax.microedition.m3g.*;

/** Original tiny fixtures; field encoding follows the JSR-184 file-format specification. */
public final class SceneFixtures {
    public static final int BACKGROUND = 0xff182027;
    public static final int MESH_ID = 70;
    private static final byte[] MAGIC = {(byte)0xab, 0x4a, 0x53, 0x52, 0x31, 0x38,
            0x34, (byte)0xbb, 13, 10, 26, 10};
    public static Camera camera() {
        Camera camera = new Camera(); camera.setPerspective(60, 1, 1, 20); return camera;
    }
    public static Appearance appearance() {
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon); return appearance;
    }
    public static VertexBuffer vertices(int shift) {
        VertexArray positions = new VertexArray(3, 3, 2);
        positions.set(0, 3, new short[]{(short)(-10 + shift), -10, 0,
                (short)(10 + shift), -10, 0, (short)shift, 10, 0});
        VertexBuffer vertices = new VertexBuffer(); vertices.setPositions(positions, .1f, null);
        vertices.setDefaultColor(0xffedb84c); return vertices;
    }
    public static TriangleStripArray indices() { return new TriangleStripArray(0, new int[]{3}); }
    public static World world(Node mesh) {
        World world = new World(); Camera camera = camera();
        world.addChild(camera); world.setActiveCamera(camera);
        Background background = new Background(); background.setColor(BACKGROUND); world.setBackground(background);
        world.addChild(mesh); return world;
    }
    public static Mesh mesh() {
        Mesh mesh = new Mesh(vertices(0), indices(), appearance());
        mesh.setTranslation(0, 0, -3); mesh.setUserID(MESH_ID); return mesh;
    }

    public static byte[] scene(boolean compressed, boolean split) throws IOException {
        List<byte[]> objects = new ArrayList<>();
        // Object indices: header=1, positions=2, vertices=3, indices=4,
        // polygon=5, appearance=6, mesh=7, camera=8, background=9, world=10.
        Bytes b = objectBase(20); b.u8(2).u8(3).u8(0).u16(3);
        for (int value : new int[]{-10,-10,0, 10,-10,0, 0,10,0}) b.u16(value);
        objects.add(object(20, b));
        b = objectBase(30); b.rgba(0xffedb84c).u32(2).f(0).f(0).f(0).f(.1f).u32(0).u32(0).u32(0);
        objects.add(object(21, b));
        b = objectBase(40); b.u8(0).u32(0).u32(1).u32(3); objects.add(object(11, b));
        b = objectBase(50); b.u8(PolygonMode.CULL_NONE).u8(PolygonMode.SHADE_SMOOTH)
                .u8(PolygonMode.WINDING_CCW).u8(0).u8(0).u8(0); objects.add(object(8, b));
        b = objectBase(60); b.u8(0).u32(0).u32(0).u32(5).u32(0).u32(0); objects.add(object(3, b));
        b = node(MESH_ID, true); b.u32(3).u32(1).u32(4).u32(6); objects.add(object(14, b));
        b = node(80, false); b.u8(Camera.PERSPECTIVE).f(60).f(1).f(1).f(20); objects.add(object(5, b));
        b = objectBase(90); b.rgba(BACKGROUND).u32(0).u8(Background.BORDER).u8(Background.BORDER)
                .u32(0).u32(0).u32(0).u32(0).u8(1).u8(1); objects.add(object(4, b));
        b = node(100, false); b.u32(2).u32(7).u32(8).u32(8).u32(9); objects.add(object(22, b));
        Bytes sections = new Bytes();
        if (split) {
            sections.raw(section(join(objects.subList(0, 6)), compressed));
            sections.raw(section(join(objects.subList(6, 9)), compressed));
        } else sections.raw(section(join(objects), compressed));
        byte[] initialHeader = header(0);
        byte[] header = header(MAGIC.length + initialHeader.length + sections.size());
        return new Bytes().raw(MAGIC).raw(header).raw(sections.bytes()).bytes();
    }
    private static Bytes objectBase(int id) { return new Bytes().u32(id).u32(0).u32(0); }
    private static Bytes node(int id, boolean translated) {
        Bytes b = objectBase(id).u8(translated ? 1 : 0);
        if (translated) b.f(0).f(0).f(-3).f(1).f(1).f(1).f(0).f(0).f(0).f(1);
        return b.u8(0).u8(1).u8(1).u8(255).u32(-1).u8(0);
    }
    private static byte[] header(int total) throws IOException {
        Bytes data = new Bytes().u8(1).u8(0).u8(0).u32(total).u32(1024)
                .raw("AbyssME synthetic".getBytes(StandardCharsets.UTF_8)).u8(0);
        return section(object(0, data), false);
    }
    private static byte[] object(int type, Bytes body) {
        return new Bytes().u8(type).u32(body.size()).raw(body.bytes()).bytes();
    }
    private static byte[] join(List<byte[]> objects) {
        Bytes b = new Bytes(); for (byte[] object : objects) b.raw(object); return b.bytes();
    }
    private static byte[] section(byte[] objects, boolean compressed) throws IOException {
        byte[] payload = objects;
        if (compressed) {
            ByteArrayOutputStream zipped = new ByteArrayOutputStream();
            try (DeflaterOutputStream stream = new DeflaterOutputStream(zipped)) { stream.write(objects); }
            payload = zipped.toByteArray();
        }
        Bytes section = new Bytes().u8(compressed ? 1 : 0).u32(payload.length + 13)
                .u32(objects.length).raw(payload);
        Adler32 checksum = new Adler32(); checksum.update(section.bytes());
        return section.u32((int)checksum.getValue()).bytes();
    }
    private static final class Bytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bytes u8(int value) { out.write(value); return this; }
        Bytes u16(int value) { return u8(value).u8(value >>> 8); }
        Bytes u32(int value) { return u16(value).u16(value >>> 16); }
        Bytes f(float value) { return u32(Float.floatToIntBits(value)); }
        Bytes rgba(int value) { return u8(value >>> 16).u8(value >>> 8).u8(value).u8(value >>> 24); }
        Bytes raw(byte[] bytes) { out.write(bytes, 0, bytes.length); return this; }
        byte[] bytes() { return out.toByteArray(); }
        int size() { return out.size(); }
    }
}
