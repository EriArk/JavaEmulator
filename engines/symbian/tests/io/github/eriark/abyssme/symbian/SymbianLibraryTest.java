package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

public final class SymbianLibraryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File root() throws Exception { return Files.createTempDirectory(context.getCacheDir().toPath(), "symbian-index-").toFile(); }
    private SystemFileCatalog.Entry system(int number) {
        return new SystemFileCatalog.Entry(String.format(Locale.ROOT, "%064x", number), "Test phone", "RM-320", "epoc93fp1");
    }
    @Test public void identityIncludesSystemAndReplacementRemovesOnlyItsStaleApps() throws Exception {
        File root = root(); SymbianLibraryIndex index = new SymbianLibraryIndex(root);
        index.publish(system(1), new String[]{"123", "One", "Symbian", "124", "Two", "N-Gage"}, false);
        index.publish(system(2), new String[]{"123", "One", "Symbian"}, false);
        assertEquals(3, index.read().size());
        assertNotEquals(index.read().get(0).key(), index.read().get(2).key());
        index.publish(system(1), new String[]{"124", "Renamed", "N-Gage"}, false);
        List<SymbianLibraryIndex.Entry> entries = new SymbianLibraryIndex(root).read();
        assertEquals(2, entries.size()); assertEquals("Renamed", entries.get(0).title);
        assertEquals("One", entries.get(1).title);
        index.publish(system(1), new String[0], false);
        assertEquals(system(2).id, index.read().get(0).system);
    }
    @Test public void badEngineResponseDoesNotReplaceLastGoodSnapshot() throws Exception {
        File root = root(); SymbianLibraryIndex index = new SymbianLibraryIndex(root);
        index.publish(system(1), new String[]{"123", "One", "Symbian"}, false);
        byte[] before = Files.readAllBytes(new File(root, system(1).id + ".json").toPath());
        for (String[] response : new String[][]{{"0", "Bad", "Symbian"}, {"4294967296", "Bad", "Symbian"},
                {"1", "One", "Symbian", "1", "Duplicate", "Symbian"}, {"123", "Missing platform"},
                {"123", null, "Symbian"}, {"123", " ", "Symbian"}, {"123", "One", null}, null}) {
            try { index.publish(system(1), response, false); fail(); } catch (IOException expected) { }
            assertArrayEquals(before, Files.readAllBytes(new File(root, system(1).id + ".json").toPath()));
        }
        SystemFileCatalog.Entry escape = new SystemFileCatalog.Entry("../bad", "Phone", "RM-320", "epoc93fp1");
        try { index.publish(escape, new String[]{"1", "Bad", "Symbian"}, false); fail(); } catch (IOException expected) { }
    }
    @Test public void bannerArtworkFitsWithoutCroppingAndNormalCoverStillFills() {
        assertEquals(android.widget.ImageView.ScaleType.FIT_CENTER,
                ru.playsoftware.j2meloader.applist.IconArtUtils.coverScale(240, 56));
        assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP,
                ru.playsoftware.j2meloader.applist.IconArtUtils.coverScale(320, 240));
        assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP,
                ru.playsoftware.j2meloader.applist.IconArtUtils.coverScale(0, 0));
    }
    @Test public void snapshotIdentityAndVersionAreValidated() throws Exception {
        File root = root(); SymbianLibraryIndex index = new SymbianLibraryIndex(root);
        for (String invalid : new String[]{"{\"version\":2}", "{\"version\":1,\"system\":\"../escape\",\"apps\":[]}"}) {
            Files.write(new File(root, system(1).id + ".json").toPath(), invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try { index.read(); fail(); } catch (IOException expected) { }
        }
    }
    @Test public void symbianMaskUsesInverseRedAndReleasesBitmaps() throws Exception {
        File target = new File(root(), "icon.png");
        Bitmap image = Bitmap.createBitmap(new int[]{0xffff0000, 0xff00ff00}, 2, 1, Bitmap.Config.ARGB_8888);
        Bitmap mask = Bitmap.createBitmap(new int[]{0xff000000, 0xffffffff}, 2, 1, Bitmap.Config.ARGB_8888);
        SymbianLibraryIndex.saveIcon(target, new Bitmap[]{image, mask});
        assertTrue(image.isRecycled()); assertTrue(mask.isRecycled());
        Bitmap decoded = BitmapFactory.decodeFile(target.getAbsolutePath());
        assertEquals(0xffff0000, decoded.getPixel(0, 0)); assertEquals(0, decoded.getPixel(1, 0) >>> 24);
        decoded.recycle();
    }
    @Test public void mismatchedIconMaskPreservesExistingImage() throws Exception {
        File target = new File(root(), "icon.png"); Files.write(target.toPath(), new byte[]{1, 2, 3});
        Bitmap image = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888);
        Bitmap mask = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try { SymbianLibraryIndex.saveIcon(target, new Bitmap[]{image, mask}); fail(); } catch (IOException expected) { }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target.toPath()));
        assertTrue(image.isRecycled()); assertTrue(mask.isRecycled());
    }
    @Test public void indexExistingGamesWithoutInstallingOrChangingSystemSelection() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("library")));
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        File selected = catalog.activeRoot(SymbianFiles.root(context));
        SymbianLibraryIndex index = new SymbianLibraryIndex(context);
        try {
            for (SystemFileCatalog.Entry entry : catalog.boundSystems()) {
                Emulator.stopSession();
                Emulator.prepare(catalog.runtime(entry).getAbsolutePath());
                String[] apps = Emulator.getLibraryApps();
                android.util.Log.i("AbyssSymbianLibrary", entry.firmware + " " + Arrays.toString(apps) + "\n" + Emulator.environmentReport());
                index.publish(entry, apps, true);
            }
            List<SymbianLibraryIndex.Entry> entries = index.read();
            assertTrue(entries.toString(), entries.stream().anyMatch(e -> e.title.equals("Snakes")));
            assertTrue("Asphalt must be indexed by its own UID", entries.stream().anyMatch(e -> e.uid == 536915939));
            assertFalse(entries.stream().anyMatch(e -> e.uid == GameInstallFlow.PLATFORM_UID));
            for (SymbianLibraryIndex.Entry entry : entries) {
                Bitmap icon = BitmapFactory.decodeFile(entry.icon.getAbsolutePath()); assertNotNull(icon);
                if (entry.uid == 536915939) {
                    assertEquals("Use the square game icon, not its large banner", icon.getWidth(), icon.getHeight());
                    Bitmap cover = BitmapFactory.decodeFile(entry.cover.getAbsolutePath()); assertNotNull(cover); cover.recycle();
                }
                icon.recycle();
            }
            assertEquals(selected, catalog.activeRoot(SymbianFiles.root(context)));
            List<ru.playsoftware.j2meloader.applist.AppItem> items = new SymbianLauncherLibrary().read(context);
            Set<Integer> ids = new HashSet<>();
            for (ru.playsoftware.j2meloader.applist.AppItem item : items) { assertTrue(item.getId() < -1); assertTrue(ids.add(item.getId())); }
            assertEquals(items.size(), entries.size());
        } finally { Emulator.stopSession(); }
    }
}
