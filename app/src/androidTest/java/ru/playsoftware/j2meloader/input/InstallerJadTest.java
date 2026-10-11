package ru.playsoftware.j2meloader.input;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.woesss.j2me.installer.InstallerDialog;
import ru.woesss.j2me.jar.Descriptor;
import static org.junit.Assert.*;

/** Real installer/dialog transitions in an isolated library, using the MIT Sudoku fixture. */
public class InstallerJadTest {
    @Test public void localMismatchReinstallKeepsSourceSavesAndSettings() throws Exception {
        try (Fixture f = new Fixture(false, false)) {
            f.open(); f.awaitText("installer_message", "Ignore JAD");
            f.clickInstall(); f.awaitSuccess();
            f.verifyReinstall();
        }
    }

    @Test public void contentMismatchCancelPickerCancelDialogAndRetry() throws Exception {
        try (Fixture f = new Fixture(true, false)) {
            f.open(); f.awaitText("button_install", "Choose JAR");
            f.pick(null);
            f.awaitText("button_install", "Choose JAR");
            assertNull(f.find());
            f.pick(f.jarUri); f.awaitText("installer_message", "Ignore JAD");
            f.closeDialog(); assertNull(f.find());
            assertTrue(f.jar.isFile()); assertTrue(f.jad.isFile());
            f.open(); f.awaitText("button_install", "Choose JAR");
            f.pick(f.jarUri); f.awaitText("installer_message", "Ignore JAD");
            f.clickInstall(); f.awaitSuccess();
            f.verifyReinstall();
        }
    }

    @Test public void matchingContentPairRetainsJadProperties() throws Exception {
        try (Fixture f = new Fixture(true, true)) {
            f.open(); f.awaitText("button_install", "Choose JAR");
            f.pick(f.jarUri); f.awaitSuccess();
            AppItem item = f.find(); assertNotNull(item);
            Descriptor installed = new Descriptor(new File(item.getPathExt(), Config.MIDLET_MANIFEST_FILE), false);
            assertEquals("kept", installed.getAttrs().get("Fixture-Property"));
            assertEquals(f.source.toString(), item.getSourceUri());
        }
    }

    @Test public void missingChosenJarFailsWithoutInstallingAndCanRetry() throws Exception {
        try (Fixture f = new Fixture(true, false)) {
            f.open(); f.awaitText("button_install", "Choose JAR");
            f.pick(Uri.fromFile(new File(f.root, "missing.jar")));
            f.awaitText("installer_title", "Installation failed");
            assertNull(f.find()); f.closeDialog();
            f.open(); f.awaitText("button_install", "Choose JAR");
            f.pick(f.jarUri); f.awaitText("installer_message", "Ignore JAD");
            f.clickInstall(); f.awaitSuccess();
            assertNotNull(f.find());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Instrumentation i = InstrumentationRegistry.getInstrumentation();
        final String original = Config.getEmulatorDir();
        final File root, jar, jad;
        final Uri source, jarUri;
        final boolean content;
        final Method init;
        final MainActivity activity;
        AppRepository repository;
        InstallerDialog dialog;

        Fixture(boolean content, boolean matching) throws Exception {
            this.content = content;
            root = Files.createTempDirectory(i.getTargetContext().getCacheDir().toPath(), "jad-flow-").toFile();
            jar = new File(root, "game.jar"); jad = new File(root, "game.jad");
            try (InputStream input = i.getContext().getAssets().open("SudokuJ2ME-0.1.jar")) {
                Files.copy(input, jar.toPath());
            }
            String manifest;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar);
                 InputStream input = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
                manifest = bytes.toString("UTF-8");
            }
            Descriptor descriptor = new Descriptor(manifest, false);
            String text = "MIDlet-Name: " + (matching ? descriptor.getName() : "Wrong JAD name")
                    + "\nMIDlet-Vendor: " + descriptor.getVendor()
                    + "\nMIDlet-Version: " + descriptor.getVersion()
                    + "\nMIDlet-Jar-URL: game.jar\nMIDlet-Jar-Size: " + jar.length()
                    + "\nFixture-Property: kept\n";
            Files.write(jad.toPath(), text.getBytes(StandardCharsets.UTF_8));
            source = uri(jad); jarUri = uri(jar);
            init = Config.class.getDeclaredMethod("initDirs", String.class); init.setAccessible(true);
            init.invoke(null, root.getPath());
            activity = (MainActivity) i.startActivitySync(new Intent(i.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            i.runOnMainSync(() -> repository = new ViewModelProvider(activity).get(AppListModel.class).getAppRepository());
            // Wait for the repository's initial scan before installing into the isolated root.
            repository.getAll().firstOrError().blockingGet();
            i.waitForIdleSync(); SystemClock.sleep(300);
        }

        Uri uri(File file) {
            return content ? FileProvider.getUriForFile(i.getTargetContext(),
                    i.getTargetContext().getPackageName() + ".provider", file) : Uri.fromFile(file);
        }
        void open() {
            dialog = InstallerDialog.newInstance(source);
            i.runOnMainSync(() -> dialog.showNow(activity.getSupportFragmentManager(), "jad-test"));
        }
        void pick(Uri value) throws Exception {
            Method method = InstallerDialog.class.getDeclaredMethod("onPickFileResult", Uri.class);
            method.setAccessible(true);
            i.runOnMainSync(() -> {
                try { method.invoke(dialog, value); }
                catch (Exception e) { throw new AssertionError(e); }
            });
        }
        void clickInstall() { i.runOnMainSync(() -> dialog.requireDialog().findViewById(R.id.button_install).performClick()); }
        void awaitText(String resource, String text) {
            int id = i.getTargetContext().getResources().getIdentifier(resource, "id", i.getTargetContext().getPackageName());
            AtomicBoolean ready = new AtomicBoolean();
            String[] seen = {""};
            long deadline = SystemClock.uptimeMillis() + 30000;
            do {
                i.runOnMainSync(() -> {
                    TextView view = dialog.requireDialog().findViewById(id);
                    seen[0] = view.getText().toString();
                    ready.set(view.isShown() && seen[0].contains(text));
                });
                if (!ready.get()) SystemClock.sleep(50);
            } while (!ready.get() && SystemClock.uptimeMillis() < deadline);
            assertTrue("Expected " + text + "; found " + seen[0], ready.get());
        }
        void awaitSuccess() {
            awaitText("installation_status", "successfully installed");
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (find() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertNotNull(find());
        }
        AppItem find() { return repository.getBySourceKey(SourceIdentity.key(source)); }
        void closeDialog() {
            i.runOnMainSync(() -> {
                dialog.requireDialog().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B));
                dialog.requireDialog().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B));
            });
            i.waitForIdleSync(); assertFalse(dialog.isAdded()); dialog = null;
        }
        void verifyReinstall() throws Exception {
            AppItem before = find(); assertNotNull(before);
            assertEquals(source.toString(), before.getSourceUri());
            assertEquals(SourceIdentity.sha256(jar), before.getSourceHash());
            Descriptor installed = new Descriptor(new File(before.getPathExt(), Config.MIDLET_MANIFEST_FILE), false);
            assertNotEquals("Wrong JAD name", installed.getName());
            assertNull(installed.getAttrs().get("Fixture-Property"));
            File rms = new File(Config.getDataDir(), before.getPath() + "/save-marker");
            File config = new File(Config.getConfigsDir(), before.getPath() + "/settings-marker");
            assertTrue(rms.getParentFile().mkdirs()); assertTrue(config.getParentFile().mkdirs());
            byte[] saved = {11, 22, 33};
            Files.write(rms.toPath(), saved); Files.write(config.toPath(), saved);
            closeDialog(); open();
            if (content) { awaitText("button_install", "Choose JAR"); pick(jarUri); }
            awaitText("installer_message", "Ignore JAD"); clickInstall();
            awaitText("button_install", "Reinstall");
            assertArrayEquals(saved, Files.readAllBytes(rms.toPath()));
            clickInstall(); awaitSuccess();
            AppItem after = find();
            assertEquals(before.getId(), after.getId()); assertEquals(before.getPath(), after.getPath());
            assertEquals(1, repository.getAll().firstOrError().blockingGet().size());
            assertArrayEquals(saved, Files.readAllBytes(rms.toPath()));
            assertArrayEquals(saved, Files.readAllBytes(config.toPath()));
            assertEquals(source.toString(), after.getSourceUri());
            assertTrue(jad.isFile()); assertEquals(after.getSourceHash(), SourceIdentity.sha256(jar));
        }
        @Override public void close() throws Exception {
            i.runOnMainSync(() -> { if (dialog != null) dialog.dismissAllowingStateLoss(); activity.finish(); });
            i.waitForIdleSync();
            init.invoke(null, original);
            FileUtils.deleteDirectory(root);
        }
    }
}
