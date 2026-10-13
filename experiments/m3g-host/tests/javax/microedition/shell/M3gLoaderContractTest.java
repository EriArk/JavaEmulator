package javax.microedition.shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.Deflater;
import javax.microedition.m3g.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original format fixtures. Malformed records are bounded, not allocation bombs. */
public class M3gLoaderContractTest {
    @Test public void compressedAndPlainMeshesLoadEqually() throws Exception {
        for(boolean skin:new boolean[]{false,true}) {
            byte[] original=DeformationFixture.file(skin);
            byte[] packed=file(section(Arrays.copyOfRange(original,51,original.length-4),true));
            assertEquals(Loader.load(original,0)[0].getClass(),Loader.load(packed,0)[0].getClass());
        }
    }
    @Test public void offsetAndTrailingContainerBytesAreRespected() throws Exception {
        byte[] original=DeformationFixture.file(false); byte[] container=new byte[original.length+70];
        Arrays.fill(container,(byte)0x7e); System.arraycopy(original,0,container,7,original.length);
        assertEquals(Loader.load(original,0).length,Loader.load(container,7).length);
    }
    @Test public void headerOnlyFileDoesNotConsumeContainerTail() throws Exception {
        byte[] header=file(); byte[] container=Arrays.copyOf(header,header.length+100);
        Arrays.fill(container,header.length,container.length,(byte)0x7e);
        assertEquals(0,Loader.load(container,0).length);
    }
    @Test public void badSectionChecksumIsRejected() throws Exception {
        byte[] f=DeformationFixture.file(false); f[f.length-1]^=1; invalid(f);
    }
    @Test public void badHeaderChecksumIsRejected() throws Exception {
        byte[] f=DeformationFixture.file(false); f[41]^=1; invalid(f);
    }
    @Test public void truncatedFileIsRejectedAndNextLoadRecovers() throws Exception {
        byte[] f=DeformationFixture.file(false); invalid(Arrays.copyOf(f,f.length-3));
        assertTrue(Loader.load(f,0).length>0);
    }
    @Test public void unknownRecordIsRejected() throws Exception { invalid(file(section(record(99,new byte[0]),false))); }
    @Test public void recordCannotConsumeTheNextObject() throws Exception {
        byte[] body=appearance(-1); ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).putInt(1,11);
        invalid(file(section(body,false)));
    }
    @Test public void recordCannotLeaveUnparsedBytes() throws Exception {
        byte[] a=appearance(0); byte[] padded=Arrays.copyOf(a,a.length+1);
        ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN).putInt(1,a.length-4);
        invalid(file(section(padded,false)));
    }
    @Test public void signedAppearanceLayerLoads() throws Exception {
        Appearance a=(Appearance)Loader.load(file(section(appearance(-7),false)),0)[0]; assertEquals(-7,a.getLayer());
    }
    @Test public void sectionLengthMustMatchPlainPayload() throws Exception {
        byte[] s=section(appearance(0),false); ByteBuffer.wrap(s).order(ByteOrder.LITTLE_ENDIAN).putInt(5,12); checksum(s);
        invalid(file(s));
    }
    @Test public void invalidCompressionSchemeIsRejected() throws Exception {
        byte[] s=section(appearance(0),false); s[0]=2; checksum(s); invalid(file(s));
    }
    @Test public void incompleteZlibTrailerIsRejected() throws Exception {
        byte[] s=section(appearance(0),true);
        byte[] shortSection=Arrays.copyOf(s,s.length-2); ByteBuffer.wrap(shortSection).order(ByteOrder.LITTLE_ENDIAN).putInt(1,shortSection.length);
        checksum(shortSection); invalid(file(shortSection));
    }
    @Test public void unexpectedCompressedTrailingDataIsRejected() throws Exception {
        byte[] s=section(appearance(0),true); byte[] extra=Arrays.copyOf(s,s.length+3);
        ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN).putInt(1,extra.length); checksum(extra); invalid(file(extra));
    }
    @Test public void sectionCannotCrossDeclaredFileBoundary() throws Exception {
        byte[] f=file(section(appearance(0),false)); ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN).putInt(29,f.length-1);
        byte[] h=Arrays.copyOfRange(f,12,42); checksum(h); System.arraycopy(h,0,f,12,h.length); invalid(f);
    }
    @Test public void invalidVertexEncodingIsRejected() throws Exception {
        ByteBuffer body=bytes(26).putInt(0).putInt(0).putInt(0).put((byte)1).put((byte)3).put((byte)2).putShort((short)3);
        body.put(new byte[9]); invalid(file(section(record(20,body.array()),false)));
    }
    @Test public void invalidBooleanIsRejected() throws Exception {
        byte[] f=file(); f[28]=2; byte[] h=Arrays.copyOfRange(f,12,42); checksum(h); System.arraycopy(h,0,f,12,h.length); invalid(f);
    }
    @Test public void negativeOffsetHasApiException() throws Exception {
        try { Loader.load(file(),-1); fail(); } catch(IndexOutOfBoundsException expected) { }
    }
    @Test public void nestedExternalLoadDoesNotChangeSiblingDirectory() throws Exception {
        archive(new String[]{"a/root.m3g","a/sub/child.m3g","a/sub/leaf.m3g","a/sibling.m3g"},
                new byte[][]{external("sub/child.m3g","sibling.m3g"),external("leaf.m3g"),file(section(appearance(-2),false)),file(section(appearance(3),false))});
        Object3D[] roots=Loader.load("/a/root.m3g"); assertEquals(2,roots.length);
        int[] layers={((Appearance)roots[0]).getLayer(),((Appearance)roots[1]).getLayer()};
        Arrays.sort(layers); assertArrayEquals(new int[]{-2,3},layers);
    }
    @Test public void externalRelativeParentSegmentsResolve() throws Exception {
        archive(new String[]{"a/sub/root.m3g","a/shared/leaf.m3g"},
                new byte[][]{external("../shared/leaf.m3g"),file(section(appearance(4),false))});
        assertEquals(4,((Appearance)Loader.load("/a/sub/root.m3g")[0]).getLayer());
    }
    @Test public void externalCyclesFailAndNextResourceStillLoads() throws Exception {
        archive(new String[]{"a/root.m3g","a/valid.m3g"},new byte[][]{external("./root.m3g"),file(section(appearance(5),false))});
        try { Loader.load("/a/root.m3g"); fail(); } catch(IOException expected) { }
        assertEquals(5,((Appearance)Loader.load("/a/valid.m3g")[0]).getLayer());
    }
    private void archive(String[] names,byte[][] files) throws Exception {
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File dir=new java.io.File(context.getCacheDir(),"m3g-contract/converted/sample"); assertTrue(dir.isDirectory() || dir.mkdirs());
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(new java.io.File(dir,"res.jar")))) {
            for(int i=0;i<names.length;i++) { zip.putNextEntry(new java.util.zip.ZipEntry(names[i])); zip.write(files[i]); zip.closeEntry(); }
        }
        new AppClassLoader("",context.getCodeCacheDir().getPath(),getClass().getClassLoader(),dir);
    }
    private static byte[] external(String... paths) throws Exception {
        ByteArrayOutputStream records=new ByteArrayOutputStream();
        for(String path:paths) records.write(record(255,(path+"\0").getBytes("UTF-8")));
        byte[] f=file(section(records.toByteArray(),false)); f[28]=1;
        byte[] h=Arrays.copyOfRange(f,12,42); checksum(h); System.arraycopy(h,0,f,12,h.length); return f;
    }
    private static byte[] appearance(int layer) {
        ByteBuffer b=bytes(33).putInt(42).putInt(0).putInt(0).put((byte)layer);
        for(int i=0;i<5;i++) b.putInt(0); return record(3,b.array());
    }
    private static byte[] record(int type,byte[] body) { return bytes(body.length+5).put((byte)type).putInt(body.length).put(body).array(); }
    static byte[] file(byte[]... sections) {
        int size=42; for(byte[] s:sections) size+=s.length;
        byte[] header=record(0,bytes(12).put((byte)1).put((byte)0).put((byte)0).putInt(size).putInt(2048).put((byte)0).array());
        ByteBuffer out=bytes(size).put(new byte[]{(byte)0xab,74,83,82,49,56,52,(byte)0xbb,13,10,26,10}).put(section(header,false));
        for(byte[] s:sections) out.put(s); return out.array();
    }
    static byte[] section(byte[] payload,boolean compress) {
        byte[] encoded=payload;
        if(compress) {
            Deflater d=new Deflater(); d.setInput(payload); d.finish(); ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] chunk=new byte[128]; while(!d.finished()) { int n=d.deflate(chunk); out.write(chunk,0,n); } d.end(); encoded=out.toByteArray();
        }
        byte[] s=bytes(encoded.length+13).put((byte)(compress?1:0)).putInt(encoded.length+13).putInt(payload.length).put(encoded).putInt(0).array();
        checksum(s); return s;
    }
    private static void checksum(byte[] section) {
        Adler32 a=new Adler32(); a.update(section,0,section.length-4);
        ByteBuffer.wrap(section).order(ByteOrder.LITTLE_ENDIAN).putInt(section.length-4,(int)a.getValue());
    }
    private static ByteBuffer bytes(int size) { return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN); }
    private static void invalid(byte[] file) throws Exception {
        try { Loader.load(file,0); fail("Malformed file accepted"); } catch(IOException expected) { }
    }
}
