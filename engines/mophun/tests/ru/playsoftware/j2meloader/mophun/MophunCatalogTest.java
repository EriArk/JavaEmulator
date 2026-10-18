package ru.playsoftware.j2meloader.mophun;

import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;

public class MophunCatalogTest {
    private AppDatabase db;
    private File root;
    private final Uri source = Uri.parse("content://fixture/tree/root/document/game.mpn");
    @Before public void setup() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = Files.createTempDirectory(context.getCacheDir().toPath(), "mophun-catalog-").toFile();
        db = AppDatabase.open(context, root.getPath());
    }
    @After public void cleanup() { db.close(); FileUtils.deleteDirectory(root); }
    private AppItem pending() {
        AppItem item = new AppItem("pending_game", "Old title", "", "");
        item.setSourceUri(source.toString()); item.setSourceKey(SourceIdentity.key(source));
        item.setPreparationState("indexed"); item.setFavorite(true);
        item.setId((int) db.appItemDao().insertImported(item));
        return item;
    }
    private AppItem installed() {
        AppItem item = new AppItem("mophun-game", "Installed title", "", "");
        item.setSourceKey("mophun:hash"); item.setSourceHash("hash");
        return item;
    }
    @Test public void singleArchivePromotesPlaceholderAndRemembersContainerAndEntry() {
        AppItem pending = pending(), ready = installed();
        MophunStore.saveCatalog(db, ready, source, "folder/game.mpn", true);
        assertEquals(pending.getId(), ready.getId());
        assertTrue(db.appItemDao().get(ready.getId()).isFavorite());
        assertEquals(0, AppRepository.indexGames(db, Collections.singletonList(pending)));
        assertEquals(ready.getId(), db.appItemDao().getSourceAlias(
                SourceIdentity.key(source) + "#folder/game.mpn", "mophun:hash").getId());
    }
    @Test public void multiArchiveKeepsChooserAndIndependentEntryAliases() {
        AppItem pending = pending(), first = installed(), second = new AppItem("mophun-second", "Second", "", "");
        second.setSourceKey("mophun:other"); second.setSourceHash("other");
        MophunStore.saveCatalog(db, first, source, "one.mpn", false);
        MophunStore.saveCatalog(db, second, source, "two.mpn", false);
        assertEquals("indexed", db.appItemDao().get(pending.getId()).getPreparationState());
        assertNotEquals(first.getId(), second.getId());
        assertEquals(first.getId(), db.appItemDao().getSourceAlias(
                SourceIdentity.key(source) + "#one.mpn", "mophun:hash").getId());
        assertEquals(second.getId(), db.appItemDao().getSourceAlias(
                SourceIdentity.key(source) + "#two.mpn", "mophun:other").getId());
    }
    @Test public void installationPromotesPlaceholderAndKeepsFavoriteAndIdentity() {
        AppItem pending = pending(), ready = installed();
        MophunStore.saveCatalog(db, ready, source);
        assertEquals(pending.getId(), ready.getId());
        assertEquals("ready", db.appItemDao().get(ready.getId()).getPreparationState());
        assertTrue(db.appItemDao().get(ready.getId()).isFavorite());
        assertNull(db.appItemDao().getByPath(pending.getPath()));
        assertEquals(0, AppRepository.indexGames(db, Collections.singletonList(pending)));
    }
    @Test public void duplicateImportKeepsInstalledIdentityAndNeverTouchesSaves() throws Exception {
        AppItem ready = installed(); ready.setPlayCount(17); ready.setCoverPath("cover.png");
        ready.setId((int) db.appItemDao().insertImported(ready));
        File save = new File(root, "saved-progress"); Files.write(save.toPath(), new byte[]{7, 4, 2});
        AppItem pending = pending();
        MophunStore.saveCatalog(db, ready, source);
        assertNull(db.appItemDao().get(pending.getId()));
        assertEquals(17, db.appItemDao().get(ready.getId()).getPlayCount());
        assertEquals("cover.png", db.appItemDao().get(ready.getId()).getCoverPath());
        assertTrue(db.appItemDao().get(ready.getId()).isFavorite());
        assertArrayEquals(new byte[]{7, 4, 2}, Files.readAllBytes(save.toPath()));
        assertEquals(0, AppRepository.indexGames(db, Collections.singletonList(pending)));
    }
}
