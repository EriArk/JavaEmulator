package javax.microedition.shell;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.Adler32;

/** Original minimal M3G records: two vertex buffers, indices, skeleton and mesh. */
final class DeformationFixture {
    static byte[] file(boolean skin) {
        ByteBuffer objects = bytes();
        positions(objects, -2); // 2
        vertices(objects, 2); // 3
        positions(objects, -4); // 4
        vertices(objects, 4); // 5
        record(objects, 11, base().put((byte) 0).putInt(0).putInt(1).putInt(3)); // 6
        record(objects, 9, node(3).putInt(0)); // 7
        ByteBuffer mesh = node(0).putInt(3).putInt(1).putInt(6).putInt(0);
        if (skin) mesh.putInt(7).putInt(1).putInt(7).putInt(0).putInt(3).putInt(1);
        else mesh.putInt(1).putInt(5).putFloat(.5f);
        record(objects, skin ? 16 : 15, mesh); // 8
        byte[] data = section(used(objects));
        ByteBuffer headerObject = bytes();
        record(headerObject, 0, bytes().put((byte) 1).put((byte) 0).put((byte) 0)
                .putInt(12 + 30 + data.length).putInt(2048).put((byte) 0));
        byte[] header = section(used(headerObject));
        byte[] magic = {(byte) 0xab, 0x4a, 0x53, 0x52, 0x31, 0x38, 0x34, (byte) 0xbb, 13, 10, 26, 10};
        return used(bytes().put(magic).put(header).put(data));
    }
    private static void positions(ByteBuffer objects, int z) {
        ByteBuffer body = base().put((byte) 2).put((byte) 3).put((byte) 0).putShort((short) 3);
        for (int value : new int[]{-1,-1,z, 1,-1,z, 0,1,z}) body.putShort((short) value);
        record(objects, 20, body);
    }
    private static void vertices(ByteBuffer objects, int positions) {
        record(objects, 21, base().putInt(-1).putInt(positions).putFloat(0).putFloat(0).putFloat(0)
                .putFloat(1).putInt(0).putInt(0).putInt(0));
    }
    private static ByteBuffer node(float z) {
        ByteBuffer body = base().put((byte) 1);
        for (float value : new float[]{0,0,z, 1,1,1, 0,0,0,1}) body.putFloat(value);
        return body.put((byte) 0).put((byte) 1).put((byte) 1).put((byte) 255).putInt(-1).put((byte) 0);
    }
    private static ByteBuffer base() { return bytes().putInt(0).putInt(0).putInt(0); }
    private static ByteBuffer bytes() { return ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN); }
    private static byte[] used(ByteBuffer buffer) { return Arrays.copyOf(buffer.array(), buffer.position()); }
    private static void record(ByteBuffer output, int type, ByteBuffer body) {
        output.put((byte) type).putInt(body.position()).put(used(body));
    }
    private static byte[] section(byte[] payload) {
        ByteBuffer buffer = bytes().put((byte) 0).putInt(payload.length + 13).putInt(payload.length).put(payload);
        Adler32 checksum = new Adler32();
        checksum.update(buffer.array(), 0, buffer.position());
        return used(buffer.putInt((int) checksum.getValue()));
    }
}
