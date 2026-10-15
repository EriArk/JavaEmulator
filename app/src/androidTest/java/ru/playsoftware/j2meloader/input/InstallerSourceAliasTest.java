package ru.playsoftware.j2meloader.input;

import android.net.Uri;
import android.widget.LinearLayout;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;

/** Real installer choices with an isolated library; no private game fixtures. */
public class InstallerSourceAliasTest {
    private Uri content(InstallerJadTest.Fixture f, File file) {
        return FileProvider.getUriForFile(f.i.getTargetContext(),
                f.i.getTargetContext().getPackageName() + ".provider", file);
    }

    private void choose(InstallerJadTest.Fixture f, int index) {
        f.i.runOnMainSync(() -> ((LinearLayout) f.dialog.requireDialog()
                .findViewById(R.id.installer_archive_choices)).getChildAt(index).performClick());
        f.awaitText("installation_status", "Using existing game");
    }

    private void prompt(InstallerJadTest.Fixture f, Uri uri) {
        f.open(uri);
        f.awaitText("installer_title", "Already in your library");
        f.awaitText("button_install", "Install separately");
    }

    @Test public void cancelReuseReopenAndCanonicalReinstallKeepSavesAndIdentity() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(false, true)) {
            f.open(); f.awaitSuccess();
            AppItem before = f.find();
            before.setTitle("My Sudoku"); before.setFavorite(true); before.setPlayCount(9);
            f.repository.saveInstalled(before);
            File save = new File(Config.getDataDir(), before.getPath() + "/save-marker");
            File settings = new File(Config.getConfigsDir(), before.getPath() + "/settings-marker");
            save.getParentFile().mkdirs(); settings.getParentFile().mkdirs();
            byte[] marker = {9, 8, 7};
            Files.write(save.toPath(), marker); Files.write(settings.toPath(), marker);
            Uri alias = content(f, f.jar);
            f.closeDialog(); prompt(f, alias);
            f.i.waitForIdleSync(); android.os.SystemClock.sleep(500);
            File screenshot = new File(f.i.getTargetContext().getExternalFilesDir(null), "source-alias-choice.png");
            android.graphics.Bitmap bitmap = f.i.getUiAutomation().takeScreenshot();
            try (FileOutputStream output = new FileOutputStream(screenshot)) {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
            } finally { bitmap.recycle(); }
            f.closeDialog(); prompt(f, alias);
            f.i.runOnMainSync(() -> {
                ((LinearLayout) f.dialog.requireDialog().findViewById(R.id.installer_archive_choices))
                        .getChildAt(0).requestFocusFromTouch();
                f.dialog.requireDialog().dispatchKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A));
                f.dialog.requireDialog().dispatchKeyEvent(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_A));
            });
            f.awaitText("installation_status", "Using existing game");
            f.closeDialog(); f.open(alias); f.awaitText("installation_status", "Using existing game");
            f.closeDialog(); f.open(); f.awaitText("button_install", "Reinstall");
            f.clickInstall(); f.awaitSuccess(); f.closeDialog();
            f.open(alias); f.awaitText("installation_status", "Using existing game");
            AppItem after = f.find();
            assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
            assertEquals(before.getId(), after.getId()); assertEquals(before.getPath(), after.getPath());
            assertEquals(before.getSourceKey(), after.getSourceKey());
            assertEquals(before.getSourceUri(), after.getSourceUri());
            assertEquals("My Sudoku", after.getTitle()); assertTrue(after.isFavorite());
            assertEquals(9, after.getPlayCount());
            assertArrayEquals(marker, Files.readAllBytes(save.toPath()));
            assertArrayEquals(marker, Files.readAllBytes(settings.toPath()));
        }
    }

    @Test public void externalAutoStartStillRequiresChoiceForAnUnknownSource() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(false, true)) {
            f.open(); f.awaitSuccess(); f.closeDialog();
            f.dialog = ru.woesss.j2me.installer.InstallerDialog.newInstance(content(f, f.jar), true, false);
            f.i.runOnMainSync(() -> f.dialog.showNow(f.activity.getSupportFragmentManager(), "external-alias"));
            f.awaitText("installer_title", "Already in your library");
            f.closeDialog();
            assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
            prompt(f, content(f, f.jar));
        }
    }

    @Test public void twoContentJadUrisResolveAfterJarSelection() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(true, true)) {
            f.open(); f.awaitText("button_install", "Choose JAR"); f.pick(f.jarUri); f.awaitSuccess();
            AppItem original = f.find(); f.closeDialog();
            File second = new File(f.root, "second.jad"); Files.copy(f.jad.toPath(), second.toPath());
            Uri alias = content(f, second);
            f.open(alias); f.awaitText("button_install", "Choose JAR"); f.pick(f.jarUri);
            f.awaitText("installer_title", "Already in your library"); choose(f, 0); f.closeDialog();
            f.open(alias); f.awaitText("button_install", "Choose JAR"); f.pick(f.jarUri);
            f.awaitText("installation_status", "Using existing game");
            assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
            assertEquals(original.getSourceUri(), f.find().getSourceUri());
        }
    }

    @Test public void separateCopyAndMultipleMatchesRemainIndependent() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(false, true)) {
            f.open(); f.awaitSuccess(); AppItem original = f.find(); f.closeDialog();
            Uri alias = content(f, f.jar);
            prompt(f, alias); f.clickInstall(); f.awaitText("installation_status", "successfully installed");
            AppItem copy = f.repository.getBySourceKey(ru.playsoftware.j2meloader.catalog.SourceIdentity.key(alias));
            assertNotNull(copy); assertNotEquals(original.getId(), copy.getId());
            assertNotEquals(original.getPath(), copy.getPath()); f.closeDialog();
            File third = new File(f.root, "third.jar"); Files.copy(f.jar.toPath(), third.toPath());
            prompt(f, Uri.fromFile(third));
            f.i.runOnMainSync(() -> assertEquals(2, ((LinearLayout) f.dialog.requireDialog()
                    .findViewById(R.id.installer_archive_choices)).getChildCount()));
            choose(f, 1); f.awaitText("installer_title", copy.getTitle());
            assertEquals(2, f.repository.getAll().firstOrError().blockingGet().size());
            assertEquals(original.getSourceUri(), f.find().getSourceUri());
        }
    }

    @Test public void changedJadPropertiesRequireAnotherChoice() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(false, true)) {
            f.open(); f.awaitSuccess(); f.closeDialog();
            File alias = new File(f.root, "alias.jad"); Files.copy(f.jad.toPath(), alias.toPath());
            prompt(f, Uri.fromFile(alias)); choose(f, 0); f.closeDialog();
            f.open(Uri.fromFile(alias)); f.awaitText("installation_status", "Using existing game"); f.closeDialog();
            String changed = new String(Files.readAllBytes(alias.toPath()), StandardCharsets.UTF_8)
                    .replace("Fixture-Property: kept", "Fixture-Property: changed");
            Files.write(alias.toPath(), changed.getBytes(StandardCharsets.UTF_8));
            prompt(f, Uri.fromFile(alias)); f.closeDialog();
            assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
        }
    }

    @Test public void archiveEntryCanReuseButDifferentBytesDoNotMatch() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(false, true)) {
            f.open(); f.awaitSuccess(); f.closeDialog();
            File archive = new File(f.root, "games.zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("nested/game.jar")); Files.copy(f.jar.toPath(), zip); zip.closeEntry();
            }
            prompt(f, Uri.fromFile(archive)); choose(f, 0); f.closeDialog();
            f.open(Uri.fromFile(archive)); f.awaitText("installation_status", "Using existing game"); f.closeDialog();
            File other = new File(f.root, "different.jar"); Files.copy(f.jar.toPath(), other.toPath());
            try (FileOutputStream out = new FileOutputStream(other, true)) { out.write(new byte[]{1, 2, 3}); }
            f.open(Uri.fromFile(other)); f.awaitText("installation_status", "successfully installed");
            assertEquals(2, f.repository.getAll().firstOrError().blockingGet().size());
        }
    }

    @Test public void changedTargetBetweenPromptAndChoiceIsRejected() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(false, true)) {
            f.open(); f.awaitSuccess(); AppItem original = f.find(); f.closeDialog();
            prompt(f, content(f, f.jar));
            File target = new File(original.getPathExt(), Config.MIDLET_RES_FILE);
            byte[] old = Files.readAllBytes(target.toPath());
            try {
                Files.write(target.toPath(), new byte[]{0});
                f.i.runOnMainSync(() -> ((LinearLayout) f.dialog.requireDialog()
                        .findViewById(R.id.installer_archive_choices)).getChildAt(0).performClick());
                f.awaitText("installer_title", "Installation failed");
                assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
            } finally { Files.write(target.toPath(), old); }
        }
    }
}
