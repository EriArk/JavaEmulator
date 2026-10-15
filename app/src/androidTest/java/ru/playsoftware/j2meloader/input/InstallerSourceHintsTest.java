package ru.playsoftware.j2meloader.input;

import android.net.Uri;
import android.widget.LinearLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.SourceHints;
import ru.woesss.j2me.installer.InstallerDialog;
import static org.junit.Assert.*;

public class InstallerSourceHintsTest {
    private AppItem installed(InstallerJadTest.Fixture f) {
        f.awaitText("installation_status", "successfully installed");
        java.util.List<AppItem> games = f.repository.getAll().firstOrError().blockingGet();
        assertEquals(1, games.size()); return games.get(0);
    }

    @Test public void fileAndContentNamesSurviveInstallAndIdReinstall() throws Exception {
        for (boolean content : new boolean[]{false, true}) {
            try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(content, true)) {
                File source = new File(f.root, "Sudoku_SE_128x160.jar");
                Files.copy(f.jar.toPath(), source.toPath());
                f.open(f.uri(source));
                AppItem game = installed(f);
                assertEquals(source.getName(), SourceHints.read(new File(game.getPathExt())));
                f.closeDialog();
                f.dialog = InstallerDialog.newInstance(game.getId());
                f.i.runOnMainSync(() -> f.dialog.showNow(f.activity.getSupportFragmentManager(), "hints-reinstall"));
                f.awaitText("button_install", "Reinstall"); f.clickInstall();
                assertEquals(source.getName(), SourceHints.read(new File(installed(f).getPathExt())));
            }
        }
    }

    @Test public void archiveUsesChosenLeafNotContainerOrParent() throws Exception {
        for (boolean seven : new boolean[]{false, true}) {
            try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(true, true)) {
                File archive = new File(f.root, "Nokia_480x800." + (seven ? "7z" : "zip"));
                String entry = "Samsung_240x320/Sudoku_SE_128x160.jar";
                if (seven) {
                    try (org.apache.commons.compress.archivers.sevenz.SevenZOutputFile out =
                                 new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive)) {
                        out.putArchiveEntry(out.createArchiveEntry(f.jar, entry));
                        out.write(Files.readAllBytes(f.jar.toPath())); out.closeArchiveEntry();
                    }
                } else {
                    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
                        for (String name : new String[]{entry, "Other_176x220.jar"}) {
                            out.putNextEntry(new ZipEntry(name)); out.write(Files.readAllBytes(f.jar.toPath())); out.closeEntry();
                        }
                    }
                }
                f.open(f.uri(archive));
                if (!seven) {
                    f.awaitText("installer_message", "Choose a game");
                    f.i.runOnMainSync(() -> ((LinearLayout) f.dialog.requireDialog()
                            .findViewById(R.id.installer_archive_choices)).getChildAt(0).performClick());
                }
                assertEquals("Sudoku_SE_128x160.jar", SourceHints.read(new File(installed(f).getPathExt())));
            }
        }
    }

    @Test public void chosenJarReplacesJadFilenameEvidence() throws Exception {
        try (InstallerJadTest.Fixture f = new InstallerJadTest.Fixture(true, true)) {
            File jar = new File(f.root, "Sudoku_176x220.jar");
            Files.copy(f.jar.toPath(), jar.toPath());
            f.open(); f.awaitText("button_install", "Choose JAR"); f.pick(f.uri(jar));
            assertEquals(jar.getName(), SourceHints.read(new File(installed(f).getPathExt())));
        }
    }
}
