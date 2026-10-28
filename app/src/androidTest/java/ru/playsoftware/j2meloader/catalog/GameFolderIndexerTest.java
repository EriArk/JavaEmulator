package ru.playsoftware.j2meloader.catalog;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.appsdb.SourceAlias;
import static org.junit.Assert.*;

public class GameFolderIndexerTest {
    private static final Uri TREE = Uri.parse("content://fixture.folders/tree/root");
    private Context context;
    private File root;
    private AppDatabase db;
    private final Map<String, List<Object[]>> folders = new HashMap<>();
    private final Set<String> blocked = new HashSet<>();
    private boolean loading;

    @Before public void setup() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = Files.createTempDirectory(target.getCacheDir().toPath(), "folder-test-").toFile();
        db = AppDatabase.open(target, root.getPath());
        ContentProvider provider = new ContentProvider() {
            @Override public boolean onCreate() { return true; }
            @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sort) {
                String id = DocumentsContract.getDocumentId(uri);
                if (blocked.contains(id)) throw new SecurityException("No nested access");
                MatrixCursor cursor = new MatrixCursor(columns);
                for (Object[] row : folders.getOrDefault(id, Collections.emptyList())) cursor.addRow(row);
                if (loading) {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putBoolean(DocumentsContract.EXTRA_LOADING, true);
                    cursor.setExtras(extras);
                }
                return cursor;
            }
            @Override public String getType(Uri uri) { return DocumentsContract.Document.MIME_TYPE_DIR; }
            @Override public Uri insert(Uri uri, ContentValues values) { throw new AssertionError("Read only"); }
            @Override public int update(Uri uri, ContentValues values, String s, String[] args) { throw new AssertionError("Read only"); }
            @Override public int delete(Uri uri, String s, String[] args) { throw new AssertionError("Read only"); }
        };
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "fixture.folders";
        provider.attachInfo(target, info);
        ContentResolver resolver = ContentResolver.wrap(provider);
        context = new ContextWrapper(target) {
            @Override public ContentResolver getContentResolver() { return resolver; }
        };
    }

    @After public void cleanup() throws Exception {
        Thread.interrupted();
        db.close();
        LibraryArchive.remove(root);
    }

    private void file(String parent, String id, String name) {
        folders.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(new Object[]{id, name, "application/octet-stream"});
    }
    private void dir(String parent, String id) {
        folders.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(new Object[]{id, id, DocumentsContract.Document.MIME_TYPE_DIR});
    }

    @Test public void mixedFolderIncludesMophunKjxAndSingleJadButNotPairedDuplicates() throws Exception {
        file("root", "java", "Game.JAR"); file("root", "jad", "game.jad");
        file("root", "single", "Descriptor.JAD"); file("root", "china", "Other.KJX");
        file("root", "zip", "Collection.zip"); file("root", "seven", "Collection.7Z");
        file("root", "noise", "game.mpn.txt"); file("root", "rom", "phone.rom");
        dir("root", "nested"); file("nested", "mpn", "Deep_Abyss.MPN");
        GameFolderIndexer.Result result = GameFolderIndexer.scan(context, TREE);
        assertEquals(6, result.games.size()); assertFalse(result.limited);
        assertEquals("Deep Abyss", result.games.get(5).getTitle());
        assertEquals("indexed", result.games.get(5).getPreparationState());
        assertEquals("content://fixture.folders/tree/root/document/mpn", result.games.get(5).getSourceUri());
    }

    @Test public void blockedChildIsReportedAndAccessibleGamesRemainAvailable() throws Exception {
        dir("root", "blocked"); blocked.add("blocked"); file("root", "ok", "Game.mpn");
        GameFolderIndexer.Result result = GameFolderIndexer.scan(context, TREE);
        assertEquals(1, result.games.size()); assertEquals(1, result.skippedFolders);
        assertTrue(result.summary(context, 1).contains("could not be read"));
    }

    @Test public void inaccessibleOrLoadingRootIsNotReportedAsEmptySuccess() throws Exception {
        blocked.add("root");
        try { GameFolderIndexer.scan(context, TREE); fail(); } catch (IOException expected) { }
        blocked.clear(); loading = true;
        try { GameFolderIndexer.scan(context, TREE); fail(); } catch (IOException expected) { }
    }

    @Test public void cyclesAndDuplicateDocumentsDoNotDuplicateGames() throws Exception {
        dir("root", "nested"); dir("nested", "root");
        file("nested", "game", "One.mpn"); file("nested", "game", "One.mpn");
        assertEquals(1, GameFolderIndexer.scan(context, TREE).games.size());
    }

    @Test public void scanBudgetCountsUnsupportedDocumentsAndDepthIsReported() throws Exception {
        for (int i = 0; i < 10001; i++) file("root", "noise" + i, "file.txt");
        assertTrue(GameFolderIndexer.scan(context, TREE).limited);
        folders.clear();
        String parent = "root";
        for (int i = 0; i < 10; i++) { dir(parent, "level" + i); parent = "level" + i; }
        assertTrue(GameFolderIndexer.scan(context, TREE).limited);
    }

    @Test public void cancelledScanDoesNotReturnAPartialSuccess() throws Exception {
        Thread.currentThread().interrupt();
        try { GameFolderIndexer.scan(context, TREE); fail(); }
        catch (java.io.InterruptedIOException expected) { }
        finally { Thread.interrupted(); }
    }

    @Test public void rescanPreservesReadyEntriesFavoritesArtworkAndHistory() throws Exception {
        file("root", "one", "One.mpn"); file("root", "two", "Two.jar");
        List<AppItem> games = GameFolderIndexer.scan(context, TREE).games;
        AppItem ready = new AppItem("mophun-hash", "Custom title", "Publisher", "1");
        ready.setSourceKey("mophun:hash"); ready.setSourceUri(games.get(0).getSourceUri());
        ready.setFavorite(true); ready.setCoverPath("custom.png"); ready.setPlayCount(42);
        ready.setId((int) db.appItemDao().insertImported(ready));
        assertEquals(1, AppRepository.indexGames(db, games));
        assertEquals(0, AppRepository.indexGames(db, games));
        AppItem preserved = db.appItemDao().get(ready.getId());
        assertTrue(preserved.isFavorite()); assertEquals(42, preserved.getPlayCount());
        assertEquals("custom.png", preserved.getCoverPath()); assertEquals("Custom title", preserved.getTitle());
    }

    @Test public void validatedSourceAliasesAreNotAddedAsNewGames() throws Exception {
        file("root", "copy", "Copy.jar");
        List<AppItem> games = GameFolderIndexer.scan(context, TREE).games;
        AppItem ready = new AppItem("installed", "Title", "", "");
        ready.setId((int) db.appItemDao().insertImported(ready));
        db.appItemDao().rememberSourceAlias(new SourceAlias(games.get(0).getSourceKey(), "fingerprint", ready.getId()));
        assertEquals(0, AppRepository.indexGames(db, games));
        db.appItemDao().delete(ready);
        assertEquals(1, AppRepository.indexGames(db, games));
    }

    @Test public void insertionFailureRollsBackEntireDiscoveryBatch() throws Exception {
        file("root", "one", "One.jar"); file("root", "two", "Two.jar");
        List<AppItem> games = GameFolderIndexer.scan(context, TREE).games;
        games.get(1).setPreparationState("ready");
        try { AppRepository.indexGames(db, games); fail(); } catch (IllegalArgumentException expected) { }
        assertNull(db.appItemDao().getBySourceKey(games.get(0).getSourceKey()));
    }
}
