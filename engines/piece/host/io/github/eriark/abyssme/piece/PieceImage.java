/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Edits the staged copy only. Directory and sector offsets follow the PFFS on-disk format. */
final class PieceImage {
    private static final int FLASH = 0xc00000, SECTOR = 4096, DIR_COUNT = 96;
    private static final int FREE = 0xffff, END = 0xeeee;
    final byte[] bytes;
    private final ByteBuffer data;
    private final int master, fat, sectors;

    PieceImage(File file) throws IOException {
        if (file.length() < 40 || file.length() > 3 * 1024 * 1024)
            throw new IOException("Invalid P/ECE flash image size");
        bytes = Files.readAllBytes(file.toPath());
        data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int offset = data.getInt(4), top = data.getInt(32), end = data.getInt(36);
        long ram = (long)data.getInt(28) - data.getInt(24);
        if (data.getInt(0) != 0x50464931 || u16(8) != 32 || offset < 40 ||
                end <= FLASH || end > FLASH + 2 * 1024 * 1024 || offset != bytes.length - (end - FLASH) ||
                top < FLASH || top > end - SECTOR || (end - top) % SECTOR != 0 ||
                ram <= 0 || ram > 2 * 1024 * 1024) throw new IOException("Invalid PFI system header");
        master = offset + (top - FLASH);
        fat = master + 32 + DIR_COUNT * 32;
        sectors = Math.min(496, (end - top) / SECTOR);
    }

    static void checkExecutable(byte[] program) throws IOException {
        ByteBuffer h = ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN);
        if (program.length < 24 || program[0] != 'X' || (program[1] != 1 && program[1] != 2)
                || (h.getShort(2) & 0xffff) < 24 || (h.getShort(2) & 0xffff) >= program.length)
            throw new IOException("Invalid P/ECE application header");
    }
    private int u16(int pos) { return data.getShort(pos) & 0xffff; }

    /** First sector of each executable contains its caption and launcher icon. */
    java.util.Map<String,byte[]> executableHeaders() {
        java.util.Map<String,byte[]> result=new java.util.LinkedHashMap<>();
        for (int i=0;i<DIR_COUNT;i++) {
            int entry=master+32+i*32;
            int len=0;
            while (len<24 && bytes[entry+len]!=0) len++;
            String name=new String(bytes,entry,len,StandardCharsets.US_ASCII);
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pex")) continue;
            int sector=u16(entry+26), count=Math.min(SECTOR,data.getInt(entry+28));
            if (sector<=0 || sector>=sectors || count<24) continue;
            int start=master+sector*SECTOR;
            result.put(name,Arrays.copyOfRange(bytes,start,start+count));
        }
        return result;
    }

    void add(String name, byte[] payload) throws IOException {
        if (!name.matches("[a-z0-9_]{1,8}\\.[a-z0-9_]{1,3}")) throw new IOException("Invalid PFFS filename: " + name);
        if (!"PFFS Master Block".equals(new String(bytes, master + 8, 17, StandardCharsets.US_ASCII))
                || u16(fat) != 0xcccc) throw new IOException("Invalid P/ECE file system");
        int slot = -1;
        for (int i = 0; i < DIR_COUNT; i++) {
            int entry = master + 32 + i * 32;
            if (bytes[entry] == (byte)0xff || bytes[entry] == 0) {
                if (slot < 0) slot = entry;
                continue;
            }
            int len = 0;
            while (len < 24 && bytes[entry + len] != 0) len++;
            if (name.equals(new String(bytes, entry, len, StandardCharsets.US_ASCII)))
                throw new IOException(name + " already exists in the base image");
        }
        if (slot < 0) throw new IOException("P/ECE directory is full");
        int previous = slot + 26, copied = 0;
        Arrays.fill(bytes, slot, slot + 32, (byte)0);
        byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, bytes, slot, encoded.length);
        data.putInt(slot + 28, payload.length);
        for (int sector = 1; copied < payload.length && sector < sectors; sector++) {
            int link = fat + sector * 2;
            if (u16(link) != FREE) continue;
            int count = Math.min(SECTOR, payload.length - copied);
            System.arraycopy(payload, copied, bytes, master + sector * SECTOR, count);
            data.putShort(previous, (short)sector);
            previous = link;
            copied += count;
        }
        if (copied != payload.length) throw new IOException("Not enough free P/ECE flash space");
        data.putShort(previous, (short)END);
    }
}
