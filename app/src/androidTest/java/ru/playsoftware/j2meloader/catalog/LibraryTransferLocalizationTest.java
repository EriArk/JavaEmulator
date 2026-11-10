// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.LocaleList;
import androidx.documentfile.provider.DocumentFile;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import static org.junit.Assert.*;

public class LibraryTransferLocalizationTest {
    private final Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private Context locale(String tag) {
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));
        return base.createConfigurationContext(config);
    }
    @Test public void folderRecoveryUsesCurrentLanguageWithoutTranslatingTheFolderName() {
        String name="Owner 100% / converted";
        LibraryImporter.FolderAccessRequired request=new LibraryImporter.FolderAccessRequired(
                Uri.parse("content://fixture/document/owner"),name);
        String diagnostic=request.getMessage();
        String english=LibraryTransferFailure.message(locale("en"),request);
        for(String tag:new String[]{"es","pt-BR","ru","fr","de","ja","zh-CN"}) {
            String text=LibraryTransferFailure.message(locale(tag),request);
            assertNotEquals(english,text); assertTrue(text.contains(name));
            assertEquals(diagnostic,request.getMessage());
        }
    }
    @Test public void invalidLibraryGetsTranslatedRecoveryWithoutChangingSourceFiles() throws Exception {
        File root=Files.createTempDirectory(base.getCacheDir().toPath(),"library-error-locale-").toFile();
        File marker=new File(root,"owner-file"); byte[] original={1,2,3}; Files.write(marker.toPath(),original);
        LibraryImporter.Catalog unused=new LibraryImporter.Catalog() {
            public AppItem bySource(String key) { throw new AssertionError(); }
            public AppItem byPath(String key) { throw new AssertionError(); }
            public AppItem byTitle(String title,String vendor) { throw new AssertionError(); }
            public void insert(AppItem item) { throw new AssertionError(); }
        };
        try {
            LibraryImporter importer=new LibraryImporter(base,root,unused,new AtomicBoolean());
            try { importer.scan(DocumentFile.fromFile(root)); fail("Incomplete library accepted"); }
            catch(IOException error) {
                assertTrue(error instanceof LibraryTransferFailure);
                assertEquals("Select the J2ME Loader folder containing converted, data and configs",error.getMessage());
                assertEquals(locale("ru").getString(R.string.library_transfer_source_layout),
                        LibraryTransferFailure.message(locale("ru"),error));
            }
            assertArrayEquals(original,Files.readAllBytes(marker.toPath()));
        } finally { Files.deleteIfExists(marker.toPath()); Files.delete(root.toPath()); }
    }
    @Test public void causesAndUnknownProviderDiagnosticsArePreserved() {
        IOException cause=new IOException("provider offline / Owner 100%");
        LibraryTransferFailure failure=LibraryTransferFailure.caused(R.string.library_transfer_source_access,
                "Cannot read source folder",cause);
        assertSame(cause,failure.getCause());
        assertEquals("Cannot read source folder",failure.getMessage());
        assertEquals(locale("ja").getString(R.string.library_transfer_source_access),
                LibraryTransferFailure.message(locale("ja"),failure));
        assertEquals(cause.getMessage(),LibraryTransferFailure.message(locale("ja"),cause));
        LibraryImporter.Entry legacy=new LibraryImporter.Entry(); legacy.problem="unknown old diagnostic";
        assertEquals(legacy.problem,legacy.problemText(locale("ru")));
        LibraryTransferFailure nested=LibraryTransferFailure.caused(R.string.library_transfer_unsupported_entry,
                "Unsupported entry: Cannot read source folder",failure,failure);
        for(String tag:new String[]{"ru","ja"}) {
            Context context=locale(tag);
            assertEquals(context.getString(R.string.library_transfer_unsupported_entry,
                    context.getString(R.string.library_transfer_source_access)),LibraryTransferFailure.message(context,nested));
        }
    }
    @Test public void emptyBackupAndCancellationHaveLocalizedMessages() throws Exception {
        try {
            new LibraryArchive(new AtomicBoolean()).write(base.getCacheDir(),java.util.Collections.emptyList(),
                    new java.io.ByteArrayOutputStream());
            fail("Empty backup accepted");
        } catch(IOException error) {
            assertEquals("No installed games to back up",error.getMessage());
            assertEquals(locale("fr").getString(R.string.library_transfer_no_games),
                    LibraryTransferFailure.message(locale("fr"),error));
        }
        assertEquals(locale("de").getString(R.string.transfer_cancelled),LibraryTransferFailure.message(locale("de"),
                new java.util.concurrent.CancellationException("Cancelled")));
    }
}
