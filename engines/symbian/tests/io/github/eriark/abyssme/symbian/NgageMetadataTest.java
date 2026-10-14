package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class NgageMetadataTest {
    private static byte[] xml(String children) {
        return ("<GameAchievements gameid='2000AFE3'><Achievement id='1'>" + children
                + "</Achievement></GameAchievements>").getBytes(StandardCharsets.UTF_8);
    }
    @Test public void normalizesOnlyLegacySpanishTags() throws Exception {
        byte[] input = xml("<Title>sp</Title><Title lang='sp'>Racer &amp; driver</Title>"
                + "<Description lang='sp'>Finish</Description><IconName lang='sp'>sp.png</IconName>"
                + "<Title lang='fr'>Pilote</Title><Other lang='sp'>unchanged</Other>");
        String result = new String(NgageMetadata.normalized(input), StandardCharsets.UTF_8);
        assertTrue(result.contains("lang=\"es\""));
        assertTrue(result.contains(">sp.png<"));
        assertTrue(result.contains("Racer &amp; driver"));
        assertTrue(result.contains("lang=\"fr\""));
        assertTrue(result.contains("<Other lang=\"sp\">"));
        assertNull(NgageMetadata.normalized(result.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void doesNotRewriteValidOrUnrelatedMetadata() throws Exception {
        assertNull(NgageMetadata.normalized(xml("<Title lang='es'>Piloto</Title>")));
        assertNull(NgageMetadata.normalized("<Other lang='sp'/>".getBytes(StandardCharsets.UTF_8)));
        assertNull(NgageMetadata.normalized("<GameAchievements xmlns='foreign' gameid='2000AFE3'/>".getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void rejectsAmbiguousTranslationsAndUnsafeXml() throws Exception {
        for (byte[] input : new byte[][]{
                xml("<Title lang='sp'>one</Title><Title lang='es'>two</Title>"),
                "<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///etc/passwd'>]><x>&y;</x>".getBytes(StandardCharsets.UTF_8),
                "<GameAchievements>".getBytes(StandardCharsets.UTF_8), new byte[NgageMetadata.MAX_BYTES + 1]}) {
            try { NgageMetadata.normalized(input); fail("Invalid metadata must be rejected"); }
            catch (Exception expected) { }
        }
    }
    @Test public void preservesOriginalAndIsIdempotent() throws Exception {
        File root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "ngage-metadata-").toFile();
        File file = new File(root, "achievements.xml");
        File backupDirectory = new File(root, "backups");
        byte[] original = xml("<Title lang='sp'>Piloto</Title>");
        try {
            Files.write(file.toPath(), original);
            assertTrue(NgageMetadata.repair(file, backupDirectory));
            File[] backups = backupDirectory.listFiles();
            assertNotNull(backups); assertEquals(1, backups.length);
            assertArrayEquals(original, Files.readAllBytes(backups[0].toPath()));
            assertFalse(NgageMetadata.repair(file, backupDirectory));
            assertEquals(2, root.list().length);
            Files.write(file.toPath(), original);
            assertTrue(NgageMetadata.repair(file, backupDirectory));
            assertEquals(2, root.list().length);
            Files.delete(file.toPath());
            assertArrayEquals(original, Files.readAllBytes(backups[0].toPath()));
        } finally {
            if (backupDirectory.exists()) for (File child : backupDirectory.listFiles()) Files.delete(child.toPath());
            for (File child : root.listFiles()) Files.delete(child.toPath());
            Files.delete(root.toPath());
        }
    }
    @Test public void backupFailureDoesNotChangeInstalledMetadata() throws Exception {
        File root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "ngage-backup-").toFile();
        File file = new File(root, "achievements.xml"), blocked = new File(root, "not-a-directory");
        byte[] original = xml("<Title lang='sp'>Piloto</Title>");
        try {
            Files.write(file.toPath(), original);
            Files.write(blocked.toPath(), new byte[]{1});
            try { NgageMetadata.repair(file, blocked); fail("Backup failure must stop repair"); }
            catch (Exception expected) { }
            assertArrayEquals(original, Files.readAllBytes(file.toPath()));
            assertEquals(2, root.list().length);
        } finally { for (File child : root.listFiles()) Files.delete(child.toPath()); Files.delete(root.toPath()); }
    }
    @Test public void preservesUnicodeAndAcceptsUtf8Bom() throws Exception {
        byte[] input = ("\uFEFF" + new String(xml("<Title lang='sp'>Espa\u00f1ol</Title>"), StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        String result = new String(NgageMetadata.normalized(input), StandardCharsets.UTF_8);
        assertTrue(result.contains("Espa\u00f1ol"));
        assertTrue(result.contains("lang=\"es\""));
    }
    @Test public void rejectsExcessiveTreeDepthAndElementCount() throws Exception {
        StringBuilder deep = new StringBuilder(), wide = new StringBuilder();
        for (int i = 0; i < 64; i++) deep.append("<x>");
        for (int i = 0; i < 64; i++) deep.append("</x>");
        for (int i = 0; i < 10001; i++) wide.append("<x/>");
        for (String value : new String[]{deep.toString(), wide.toString()}) {
            try { NgageMetadata.normalized(xml(value)); fail("Excessive XML must be rejected"); }
            catch (java.io.IOException expected) { }
        }
    }
}
