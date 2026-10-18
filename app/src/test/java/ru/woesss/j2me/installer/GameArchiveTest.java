package ru.woesss.j2me.installer;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.sevenz.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class GameArchiveTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private File archive(boolean seven, String... names) throws Exception {
        File file = files.newFile();
        byte[] data = {1, 2, 3};
        if (seven) {
            try (SevenZOutputFile out = new SevenZOutputFile(file)) {
                for (String name : names) {
                    SevenZArchiveEntry entry = new SevenZArchiveEntry();
                    entry.setName(name); entry.setSize(data.length);
                    out.putArchiveEntry(entry); out.write(data); out.closeArchiveEntry();
                }
            }
        } else {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
                for (String name : names) {
                    out.putNextEntry(new ZipEntry(name)); out.write(data); out.closeEntry();
                }
            }
        }
        return file;
    }
    @Test public void zipAndSevenListMixedGamesAndExtractOnlyChosenEntry() throws Exception {
        for (boolean seven : new boolean[]{false, true}) {
            File input = archive(seven, "readme.txt", "Java/game.JAR", "Mophun/game.MPN");
            assertEquals(Arrays.asList("Java/game.JAR", "Mophun/game.MPN"), GameArchive.list(input));
            for (String name : GameArchive.list(input)) {
                File output = files.newFile();
                GameArchive.extract(input, name, output);
                assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(output.toPath()));
            }
            assertFalse(new File(files.getRoot(), "Java").exists());
            assertFalse(new File(files.getRoot(), "Mophun").exists());
        }
    }
    @Test public void unsafeAndDuplicatePathsAreRejectedForBothFormats() throws Exception {
        for (boolean seven : new boolean[]{false, true}) {
            for (String bad : new String[]{"../game.mpn", "/game.mpn", "C:/game.mpn", "game\n.mpn"}) {
                try { GameArchive.list(archive(seven, bad)); fail(bad); }
                catch (IOException expected) { }
            }
            try { GameArchive.list(archive(seven, "GAME.mpn", "game.mpn")); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("Duplicate")); }
        }
    }
    @Test public void missingSelectionAndNonGamesAreRejected() throws Exception {
        for (boolean seven : new boolean[]{false, true}) {
            try { GameArchive.list(archive(seven, "readme.txt")); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("JAR or MPN")); }
            File output = new File(files.getRoot(), "never-created");
            try { GameArchive.extract(archive(seven, "game.mpn"), "other.mpn", output); fail(); }
            catch (IOException expected) { }
            assertFalse(output.exists());
        }
    }
    @Test public void corruptAndTruncatedInputsFail() throws Exception {
        for (byte[] data : new byte[][]{{0, 1}, {'P', 'K', 3, 4}, {0x37, 0x7a, (byte)0xbc, (byte)0xaf, 0x27, 0x1c}}) {
            File input = files.newFile(); Files.write(input.toPath(), data);
            try { GameArchive.list(input); fail(); } catch (IOException expected) { }
        }
    }
    @Test public void cancellationDoesNotCreatePayload() throws Exception {
        File input = archive(false, "game.mpn"), output = new File(files.getRoot(), "cancelled");
        Thread.currentThread().interrupt();
        try { GameArchive.extract(input, "game.mpn", output); fail(); }
        catch (InterruptedIOException expected) { }
        finally { Thread.interrupted(); }
        assertFalse(output.exists());
    }
    @Test public void oversizedSelectedPayloadIsRejectedWithoutWriting() throws Exception {
        File input = files.newFile();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(input.toPath()))) {
            out.putNextEntry(new ZipEntry("large.mpn"));
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 17; i++) out.write(chunk);
            out.closeEntry();
        }
        File output = files.newFile();
        try { GameArchive.extract(input, "large.mpn", output); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("too large")); }
        assertFalse(output.exists());
    }
}
