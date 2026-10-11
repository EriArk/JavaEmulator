package ru.playsoftware.j2meloader.applist;

import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class GameArtworkTest {
    private File dir;
    @Before public void setup() throws Exception {
        dir = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "art-test-").toFile();
    }
    private byte[] png(int w, int h) {
        Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        image.eraseColor(0xff33aabb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, out); image.recycle(); return out.toByteArray();
    }
    private File jar(Object... entries) throws Exception {
        File jar = new File(dir, "res.jar");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry((String) entries[i]));
                zip.write((byte[]) entries[i + 1]); zip.closeEntry();
            }
        }
        return jar;
    }
    @Test public void smallDeclaredIconWinsOverLargerNamedIcon() throws Exception {
        GameArtwork.Selection art = GameArtwork.select(jar("a.png", png(12, 12),
                "icon.png", png(128, 128), "wood.png", png(128, 128)), "/a.png");
        assertEquals("a.png", art.icon); assertNull(art.cover);
    }
    @Test public void invalidDeclaredIconFallsBackOnlyToUnambiguousIconName() throws Exception {
        assertEquals("icon.png", GameArtwork.select(jar("bad.png", new byte[]{0, 1},
                "icon.png", png(32, 32)), "bad.png").icon);
        assertNull(GameArtwork.select(jar("wood.png", png(128, 128), "logo.png", png(120, 120)),
                "missing.png").icon);
    }
    @Test public void declaredIconDoesNotNeedAnImageFileExtension() throws Exception {
        assertEquals("art", GameArtwork.select(jar("art", png(16, 16), "icon.png", png(64, 64)),
                "/art").icon);
    }
    @Test public void noArbitraryTextureOrTextOrPublisherArt() throws Exception {
        GameArtwork.Selection art = GameArtwork.select(jar("wood.jpg", png(128, 128),
                "congratulations.png", png(206, 67), "vendor-splash.png", png(240, 320),
                "background.png", png(640, 480), "title-sprites.png", png(320, 240)), null);
        assertNull(art.icon); assertNull(art.cover);
    }
    @Test public void coverEvidenceIsIndependentOfArea() throws Exception {
        GameArtwork.Selection art = GameArtwork.select(jar("title.png", png(176, 208),
                "background.png", png(1920, 1080), "sprite.png", png(1024, 1024)), null);
        assertEquals("title.png", art.cover);
        assertEquals("menu/background.png", GameArtwork.select(jar("menu/background.png", png(320, 180)), null).cover);
    }
    @Test public void ambiguousCandidatesFallBackRegardlessOfArchiveOrder() throws Exception {
        assertNull(GameArtwork.select(jar("a/title.png", png(240, 320), "b/title.png", png(320, 240)), null).cover);
        assertNull(GameArtwork.select(jar("b/title.png", png(320, 240), "a/title.png", png(240, 320)), null).cover);
        assertNull(GameArtwork.select(jar("icon1.png", png(32, 32), "icon2.png", png(48, 48)), null).icon);
    }
    @Test public void gameNamedIntroIsStillTooAmbiguousForACover() throws Exception {
        assertNull(GameArtwork.select(jar("example-intro.png", png(240, 320),
                "publisher-splash.png", png(288, 384)), null).cover);
    }
    @Test public void refreshPreservesUserArtAndDoesNotTouchGameData() throws Exception {
        jar("icon.png", png(16, 16), "title.png", png(240, 320));
        byte[] custom = png(64, 64);
        GameArtwork.saveUserImage(dir, true, new ByteArrayInputStream(custom));
        GameArtwork.saveUserImage(dir, false, new ByteArrayInputStream(custom));
        Files.write(new File(dir, "save.rms").toPath(), new byte[]{9});
        GameArtwork.refresh(dir, "icon.png");
        AppItem item = new AppItem("test", "Example", "", "");
        GameArtwork.applyPaths(item, dir);
        assertEquals("test/user-icon.png", item.getImagePath());
        assertEquals("test/user-cover.png", item.getCoverPath());
        assertArrayEquals(custom, Files.readAllBytes(new File(dir, GameArtwork.USER_COVER).toPath()));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(new File(dir, "save.rms").toPath()));
        jar("texture.png", png(128, 128));
        GameArtwork.refresh(dir, null);
        assertFalse(new File(dir, "icon.png").exists()); assertFalse(new File(dir, "cover.png").exists());
        assertTrue(new File(dir, GameArtwork.USER_COVER).exists());
    }
    @Test public void invalidPickedImageDoesNotReplaceExistingImage() throws Exception {
        byte[] original = png(32, 32);
        GameArtwork.saveUserImage(dir, false, new ByteArrayInputStream(original));
        try { GameArtwork.saveUserImage(dir, false, new ByteArrayInputStream(new byte[]{1})); fail(); }
        catch (IOException expected) { }
        assertArrayEquals(original, Files.readAllBytes(new File(dir, GameArtwork.USER_COVER).toPath()));
    }
    @Test public void reinstallPreservesLegacyCoverAndExplicitImages() throws Exception {
        File dest = new File(dir, "new"); assertTrue(dest.mkdir());
        byte[] cover = png(320, 240);
        Files.write(new File(dir, "cover.png").toPath(), cover);
        GameArtwork.saveUserImage(dir, true, new ByteArrayInputStream(png(24, 24)));
        GameArtwork.preserveUserArt(dir, dest);
        assertArrayEquals(cover, Files.readAllBytes(new File(dest, GameArtwork.USER_COVER).toPath()));
        assertTrue(new File(dest, GameArtwork.USER_ICON).exists());
    }
    @Test public void generatedCoverIsNotPromotedToUserArtOnReinstall() throws Exception {
        jar("title.png", png(240, 320)); GameArtwork.refresh(dir, null);
        File dest = new File(dir, "new"); assertTrue(dest.mkdir());
        GameArtwork.preserveUserArt(dir, dest);
        assertFalse(new File(dest, GameArtwork.USER_COVER).exists());
    }
}
