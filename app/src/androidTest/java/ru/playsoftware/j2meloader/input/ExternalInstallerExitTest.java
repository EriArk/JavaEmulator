package ru.playsoftware.j2meloader.input;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assume;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import ru.playsoftware.j2meloader.catalog.LauncherLinkDialog;
import ru.woesss.j2me.installer.InstallerDialog;
import static org.junit.Assert.*;

/** Cancellation must leave an external trampoline, but keep an internal library. */
public class ExternalInstallerExitTest {
    @Test public void archiveControllerCancelReturnsOnlyExternalHost() throws Exception {
        check(".zip", true, (uri, finish) -> InstallerDialog.newInstance(uri, true, finish),
                "Choose", false, KeyEvent.KEYCODE_BUTTON_B);
    }

    @Test public void javaErrorCloseReturnsOnlyExternalHost() throws Exception {
        check(".jar", false, (uri, finish) -> InstallerDialog.newInstance(uri, true, finish),
                "Installation failed", true, 0);
    }

    @Test public void linkErrorCloseReturnsOnlyExternalHost() throws Exception {
        check(".abyssme", false, LauncherLinkDialog::create, "Cannot open game", true, 0);
    }

    @Test public void linkErrorControllerBackReturnsOnlyExternalHost() throws Exception {
        check(".abyssme", false, LauncherLinkDialog::create, "Cannot open game", false,
                KeyEvent.KEYCODE_BUTTON_B);
    }

    @Test public void linkSystemCancelReturnsOnlyExternalHost() throws Exception {
        check(".abyssme", false, LauncherLinkDialog::create, "Cannot open game", false, 0);
    }

    @Test public void mophunErrorBackReturnsOnlyExternalHost() throws Exception {
        check(".mpn", false, (uri, finish) -> (DialogFragment) Class.forName(
                "ru.playsoftware.j2meloader.mophun.MophunInstallerDialog")
                .getMethod("create", Uri.class, boolean.class, boolean.class)
                .invoke(null, uri, true, finish), "Could not import game", false, KeyEvent.KEYCODE_BACK);
    }

    @Test public void mophunErrorCloseReturnsOnlyExternalHost() throws Exception {
        check(".mpn", false, (uri, finish) -> (DialogFragment) Class.forName(
                "ru.playsoftware.j2meloader.mophun.MophunInstallerDialog")
                .getMethod("create", Uri.class, boolean.class, boolean.class)
                .invoke(null, uri, true, finish), "Could not import game", true, 0);
    }

    @Test public void symbianInspectionCancelReturnsOnlyExternalHost() throws Exception {
        Assume.assumeTrue(BuildConfig.SYMBIAN_EXPERIMENT);
        check(".sis", false, (uri, finish) -> ((AdditionalGames.ImportRouter) Class.forName(
                "io.github.eriark.abyssme.symbian.SymbianImportDialog").getConstructor().newInstance())
                .installer(InstrumentationRegistry.getInstrumentation().getTargetContext(), uri, true, finish),
                null, false, KeyEvent.KEYCODE_BUTTON_B);
    }

    private interface Factory { DialogFragment create(Uri uri, boolean finish) throws Exception; }

    private static void check(String suffix, boolean archive, Factory factory, String state,
                              boolean touch, int key) throws Exception {
        Instrumentation i = InstrumentationRegistry.getInstrumentation();
        File source = File.createTempFile("external-exit-", suffix, i.getTargetContext().getCacheDir());
        if (archive) {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source.toPath()))) {
                for (String name : new String[]{"One.jar", "Two.jar"}) {
                    zip.putNextEntry(new ZipEntry(name)); zip.write(new byte[]{1, 2, 3}); zip.closeEntry();
                }
            }
        } else Files.write(source.toPath(), new byte[]{1, 2, 3});
        byte[] original = Files.readAllBytes(source.toPath());
        try {
            for (boolean finish : new boolean[]{false, true}) {
                MainActivity activity = (MainActivity) i.startActivitySync(new Intent(i.getTargetContext(),
                        MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                DialogFragment dialog = factory.create(Uri.fromFile(source), finish);
                try {
                    i.runOnMainSync(() -> dialog.showNow(activity.getSupportFragmentManager(), "external-exit"));
                    if (state != null) await(i, dialog, state);
                    i.runOnMainSync(() -> {
                        assertFalse("Error/choice should remain open", activity.isFinishing());
                        if (touch) {
                            View close = dialog.requireDialog().findViewById(R.id.button_close);
                            if (close == null) close = text(dialog.requireDialog().getWindow().getDecorView(), "Close");
                            assertNotNull(close);
                            assertTrue(close.performClick());
                        } else if (key != 0) {
                            dialog.requireDialog().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                            dialog.requireDialog().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                        } else dialog.requireDialog().cancel();
                    });
                    i.waitForIdleSync();
                    i.runOnMainSync(() -> assertEquals("finishHost=" + finish, finish, activity.isFinishing()));
                    assertFalse(dialog.isAdded());
                    assertArrayEquals(original, Files.readAllBytes(source.toPath()));
                } finally {
                    i.runOnMainSync(() -> { dialog.dismissAllowingStateLoss(); activity.finish(); });
                    i.waitForIdleSync();
                }
            }
        } finally { Files.deleteIfExists(source.toPath()); }
    }

    private static void await(Instrumentation i, DialogFragment dialog, String state) {
        AtomicBoolean found = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            i.runOnMainSync(() -> found.set(dialog.getDialog() != null
                    && text(dialog.requireDialog().getWindow().getDecorView(), state) != null));
            if (!found.get()) SystemClock.sleep(50);
        } while (!found.get() && SystemClock.uptimeMillis() < deadline);
        assertTrue("Missing installer state: " + state, found.get());
        i.waitForIdleSync();
    }

    private static View text(View view, String value) {
        if (view instanceof TextView && view.isShown()
                && ((TextView) view).getText().toString().contains(value)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) {
                View result = text(group.getChildAt(n), value);
                if (result != null) return result;
            }
        }
        return null;
    }
}
