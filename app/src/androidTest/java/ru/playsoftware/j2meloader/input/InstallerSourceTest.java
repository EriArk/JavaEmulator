package ru.playsoftware.j2meloader.input;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;

public class InstallerSourceTest {
    @Test public void contentUriDistinguishesJarFromGameArchive() throws Exception {
        assertCopy(zip("META-INF/MANIFEST.MF"), ".jar");
        assertCopy(zip("folder/game.jar"), ".zip");
        assertCopy(zip("LICENSE.txt"), ".zip");
    }

    @Test public void otherSignaturesAndTextKeepTheirFormats() throws Exception {
        // Format detection only: decoding a complete 7z/KJX is the installer's job.
        assertCopy(new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0, 0}, ".7z");
        assertCopy(new byte[]{'K', 'J', 'X', 0}, ".kjx");
        assertCopy("MIDlet-Name: Example\n".getBytes(StandardCharsets.UTF_8), ".jad");
    }

    @Test public void shortInputIsCopiedAndEmptyInputFails() throws Exception {
        assertCopy(new byte[]{'M'}, ".jad");
        try {
            assertCopy(new byte[0], ".jad");
            fail("Empty input should fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Can't read data"));
        }
    }

    private byte[] zip(String entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry(entry)); out.write(new byte[]{1, 2, 3}); out.closeEntry();
        }
        return bytes.toByteArray();
    }

    private void assertCopy(byte[] bytes, String extension) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "installer-source-").toFile();
        File source = new File(root, "opaque-source"); Files.write(source.toPath(), bytes);
        ContentProvider provider = new ContentProvider() {
            @Override public boolean onCreate() { return true; }
            @Override public String getType(Uri uri) { return "application/octet-stream"; }
            @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String sort) { return null; }
            @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
            @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
            @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
            @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
                return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        };
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "fixture.install";
        provider.attachInfo(context, info);
        ContextWrapper wrapped = new ContextWrapper(context) {
            @Override public File getCacheDir() { return root; }
            @Override public ContentResolver getContentResolver() { return ContentResolver.wrap(provider); }
        };
        try {
            File copied = FileUtils.getFileForUri(wrapped, Uri.parse("content://fixture.install/document/42"));
            assertTrue(copied.getName(), copied.getName().endsWith(extension));
            assertArrayEquals(bytes, Files.readAllBytes(copied.toPath()));
            assertArrayEquals(bytes, Files.readAllBytes(source.toPath()));
            assertNotEquals(source, copied);
        } finally { FileUtils.deleteDirectory(root); }
    }
}
