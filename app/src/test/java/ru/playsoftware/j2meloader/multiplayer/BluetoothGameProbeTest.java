/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.io.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BluetoothGameProbeTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private byte[] constant(boolean reference,String name)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0xcafebabe);out.writeShort(0);out.writeShort(48);out.writeShort(3);
        out.writeByte(1);out.writeUTF(name);out.writeByte(reference?7:8);out.writeShort(1);return bytes.toByteArray();
    }
    private boolean probe(byte[] bytes)throws Exception{return BluetoothGameProbe.classReferencesBluetooth(new DataInputStream(new ByteArrayInputStream(bytes)));}
    @Test public void classReferenceEnablesButStringLiteralDoesNot()throws Exception{
        assertTrue(probe(constant(true,"javax/bluetooth/LocalDevice")));
        assertFalse(probe(constant(false,"javax/bluetooth/LocalDevice")));
        assertFalse(probe(constant(true,"javax/microedition/lcdui/Canvas")));
        assertFalse(probe(constant(true,"fake/javax/bluetooth/LocalDevice")));
    }
    @Test public void malformedAndTruncatedClassesFailClosed()throws Exception{
        assertThrows(IOException.class,()->probe(new byte[]{1,2,3,4}));
        byte[] bytes=constant(true,"javax/bluetooth/LocalDevice");
        assertThrows(IOException.class,()->probe(java.util.Arrays.copyOf(bytes,bytes.length-1)));
        bytes[bytes.length-1]=100;assertThrows(IOException.class,()->probe(bytes));
    }
    @Test public void resourcesDoNotImplySupportAndCompressedClassHasLimit()throws Exception{
        File jar=temp.newFile();
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(jar))){out.putNextEntry(new ZipEntry("help.txt"));out.write(constant(true,"javax/bluetooth/LocalDevice"));}
        assertFalse(BluetoothGameProbe.referencesBluetooth(jar));
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(jar))){out.putNextEntry(new ZipEntry("game.class"));out.write(new byte[2*1024*1024+1]);}
        assertThrows(IOException.class,()->BluetoothGameProbe.referencesBluetooth(jar));
    }
    @Test public void validClassInsideArchiveIsDetected()throws Exception{
        File jar=temp.newFile();
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(jar))){out.putNextEntry(new ZipEntry("game.class"));out.write(constant(true,"javax/bluetooth/DiscoveryAgent"));}
        assertTrue(BluetoothGameProbe.referencesBluetooth(jar));
    }
}
