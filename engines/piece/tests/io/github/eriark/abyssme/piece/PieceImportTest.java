/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;

public class PieceImportTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Rule public final org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();

    @Test public void rejectsInvalidImageAndApplication() throws Exception {
        File temp = File.createTempFile("piece", ".pfi", context.getCacheDir());
        try {
            Files.write(temp.toPath(), new byte[80]);
            try { new PieceImage(temp); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("header")); }
            try { PieceImage.checkExecutable(new byte[32]); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("header")); }
        } finally { temp.delete(); }
    }

    @Test public void baseStaysIntactAcrossPackageAndLooseApplicationImports() throws Exception {
        String fixture = InstrumentationRegistry.getArguments().getString("pieceGame");
        Assume.assumeNotNull(fixture);
        File folder = context.getExternalFilesDir(null);
        File base = new File(folder, "System.pfi");
        String original = SourceIdentity.sha256(base);
        File storage = temporary.newFolder();
        Context isolated = new android.content.ContextWrapper(context) {
            @Override public File getFilesDir() { return storage; }
            @Override public File getExternalFilesDir(String type) { return storage; }
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return context.getSharedPreferences("test-piece-" + name, mode);
            }
        };
        PieceLibrary library = new PieceLibrary();
        library.install(isolated, Uri.fromFile(base));
        for (String game : new String[]{"pko.pex", fixture, fixture}) library.install(isolated, Uri.fromFile(new File(folder,game)));
        assertEquals(original, SourceIdentity.sha256(library.installed(isolated, original)));
        assertEquals(original, SourceIdentity.sha256(base));
        int packages = 0;
        for (AppItem item : library.read(isolated)) {
            if (!item.getTitle().equals("pko")) continue;
            assertEquals("PEX", item.getVersion());
            assertEquals(8, ru.playsoftware.j2meloader.applist.LibraryQuery.platform(item));
            packages++;
        }
        assertEquals(2, packages);
    }
}
