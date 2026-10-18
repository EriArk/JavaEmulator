package ru.playsoftware.j2meloader.input;

import android.widget.LinearLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.*;
import org.apache.commons.compress.archivers.sevenz.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;

/** UI import checks. An optional private MPN may be supplied without bundling games. */
public class InstallerArchiveTest {
    private byte[] mpn() throws Exception {
        String path = InstrumentationRegistry.getArguments().getString("archiveMpn");
        if (path != null) return Files.readAllBytes(new File(path).toPath());
        byte[] header = new byte[40];
        header[0] = 'V'; header[1] = 'M'; header[2] = 'G'; header[3] = 'P';
        return header; // Valid empty VMGP for installation checks, not gameplay.
    }
    private File archive(InstallerJadTest.Fixture f, boolean seven, boolean mixed, byte[] data) throws Exception {
        File file = new File(f.root, "collection." + (seven ? "7z" : "zip"));
        String[] names = mixed ? new String[]{"Mophun/Test_[T310].mpn", "Java/Sudoku.jar"} : new String[]{"Mophun/Test_[T310].mpn"};
        if (seven) {
            try (SevenZOutputFile out = new SevenZOutputFile(file)) {
                for (String name : names) {
                    byte[] bytes = name.endsWith(".mpn") ? data : Files.readAllBytes(f.jar.toPath());
                    SevenZArchiveEntry entry = new SevenZArchiveEntry();
                    entry.setName(name); entry.setSize(bytes.length);
                    out.putArchiveEntry(entry); out.write(bytes); out.closeArchiveEntry();
                }
            }
        } else {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
                for (String name : names) {
                    out.putNextEntry(new ZipEntry(name));
                    out.write(name.endsWith(".mpn") ? data : Files.readAllBytes(f.jar.toPath()));
                    out.closeEntry();
                }
            }
        }
        return file;
    }
    private void choose(InstallerJadTest.Fixture f, int index) {
        f.awaitText("installer_message", "Choose a game");
        f.i.runOnMainSync(() -> ((LinearLayout) f.dialog.requireDialog()
                .findViewById(R.id.installer_archive_choices)).getChildAt(index).performClick());
    }
    private void open(InstallerJadTest.Fixture f, File archive) {
        f.i.runOnMainSync(() -> ru.playsoftware.j2meloader.catalog.AdditionalGames.install(
                f.activity, f.activity.getSupportFragmentManager(), f.uri(archive), false, false));
        long deadline = android.os.SystemClock.uptimeMillis() + 30000;
        do {
            f.i.runOnMainSync(() -> {
                androidx.fragment.app.Fragment fragment = f.activity.getSupportFragmentManager().findFragmentByTag("installer");
                if (fragment instanceof ru.woesss.j2me.installer.InstallerDialog)
                    f.dialog = (ru.woesss.j2me.installer.InstallerDialog) fragment;
            });
            if (f.dialog != null) return;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("The unified importer did not hand the archive to the game chooser");
    }
    private void installMophun(InstallerJadTest.Fixture f) {
        f.awaitText("installer_title", "Mophun installer");
        f.awaitText("button_install", f.activity.getString(R.string.install)); f.clickInstall();
        f.awaitText("installation_status", "successfully installed");
    }
    @Test public void mixedZipAndSevenImportBothTypesAndReuseMophunWithSaves() throws Exception {
        for (boolean seven : new boolean[]{false, true}) {
            try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(true, true)) {
                byte[] data = mpn();
                File archive = archive(f, seven, true, data);
                open(f, archive); choose(f, 0); installMophun(f);
                List<AppItem> games = f.repository.getAll().firstOrError().blockingGet();
                assertEquals(1, games.size()); AppItem first = games.get(0);
                assertEquals(f.uri(archive).toString(), first.getSourceUri());
                assertEquals("Test [T310]", first.getTitle());
                assertArrayEquals(data, Files.readAllBytes(new File(first.getPathExt(), "game.mpn").toPath()));
                File save = new File(Config.getDataDir(), first.getPath() + "/progress");
                File settings = new File(Config.getConfigsDir(), first.getPath() + "/settings-marker");
                save.getParentFile().mkdirs(); settings.getParentFile().mkdirs();
                Files.write(save.toPath(), new byte[]{4, 5}); Files.write(settings.toPath(), new byte[]{6, 7});
                f.closeDialog(); open(f, archive); choose(f, 0); installMophun(f);
                games = f.repository.getAll().firstOrError().blockingGet();
                assertEquals(1, games.size()); assertEquals(first.getId(), games.get(0).getId());
                assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(save.toPath()));
                assertArrayEquals(new byte[]{6, 7}, Files.readAllBytes(settings.toPath()));
                assertNotNull(f.repository.getSourceAlias(SourceIdentity.key(f.uri(archive))
                        + "#Mophun/Test_[T310].mpn", "mophun:" + first.getSourceHash()));
                f.closeDialog(); open(f, archive); choose(f, 1);
                f.awaitText("installation_status", "successfully installed");
                assertEquals(2, f.repository.getAll().firstOrError().blockingGet().size());
                assertTrue(archive.isFile());
            }
        }
    }
    @Test public void singleMophunInstallsWithoutChooserAndInvalidGameDoesNotPublish() throws Exception {
        for (boolean seven : new boolean[]{false, true}) {
            try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(true, true)) {
                File archive = archive(f, seven, false, mpn());
                open(f, archive); installMophun(f);
                assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
                f.closeDialog();
                archive(f, seven, false, new byte[]{1, 2, 3});
                open(f, archive); f.awaitText("button_install", f.activity.getString(R.string.install)); f.clickInstall();
                f.awaitText("installer_title", "Installation failed");
                assertEquals(1, f.repository.getAll().firstOrError().blockingGet().size());
                assertTrue(archive.isFile());
            }
        }
    }
}
