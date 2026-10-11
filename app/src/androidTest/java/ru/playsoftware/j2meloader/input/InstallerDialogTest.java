package ru.playsoftware.j2meloader.input;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.DialogInstallerBinding;
import ru.woesss.j2me.installer.InstallerDialog;
import static org.junit.Assert.*;

public class InstallerDialogTest {
    @Test public void archiveUsesVerticalChoicesAndBackPreservesSource() throws Exception {
        File source = fixture(".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(source))) {
            for (int i = 0; i < 12; i++) {
                zip.putNextEntry(new ZipEntry("Phone edition " + i + "/A long game filename.jar"));
                zip.write(new byte[]{1, 2, 3}); zip.closeEntry();
            }
        }
        byte[] before = Files.readAllBytes(source.toPath());
        try {
            withDialog(source, (i, dialog) -> {
                await(i, dialog, R.id.installer_archive_choices);
                i.runOnMainSync(() -> {
                    LinearLayout choices = dialog.requireDialog().findViewById(R.id.installer_archive_choices);
                    assertEquals(LinearLayout.VERTICAL, choices.getOrientation());
                    assertEquals(12, choices.getChildCount());
                    assertTrue(((Button) choices.getChildAt(0)).getText().toString().contains("/"));
                    assertTrue(dialog.requireDialog().findViewById(R.id.button_close).isShown());
                    key(dialog, KeyEvent.KEYCODE_BUTTON_B);
                });
                i.waitForIdleSync(); assertFalse(dialog.isAdded());
            });
            assertArrayEquals(before, Files.readAllBytes(source.toPath()));
        } finally { Files.deleteIfExists(source.toPath()); }
    }

    @Test public void invalidArchiveKeepsErrorVisibleUntilClose() throws Exception {
        File source = fixture(".zip");
        Files.write(source.toPath(), new byte[]{0, 1, 2});
        try {
            withDialog(source, (i, dialog) -> {
                await(i, dialog, R.id.button_close);
                i.runOnMainSync(() -> {
                    assertEquals("Installation failed", ((TextView) dialog.requireDialog()
                            .findViewById(R.id.installer_title)).getText().toString());
                    assertTrue(((TextView) dialog.requireDialog().findViewById(R.id.installer_message)).length() > 0);
                    assertFalse(dialog.requireDialog().findViewById(R.id.button_install).isShown());
                    key(dialog, KeyEvent.KEYCODE_BUTTON_B);
                });
                i.waitForIdleSync(); assertFalse(dialog.isAdded());
            });
            assertTrue(source.exists());
        } finally { Files.deleteIfExists(source.toPath()); }
    }

    @Test public void busyBackIsIgnoredAndJarPromptHasExplicitAction() throws Exception {
        File source = fixture(".jad");
        Files.write(source.toPath(), ("MIDlet-Name: Installer UI fixture\nMIDlet-Vendor: Test\n"
                + "MIDlet-Version: 1.0\nMIDlet-Jar-URL: https://example.invalid/game.jar\n"
                + "MIDlet-Jar-Size: 1\n").getBytes(StandardCharsets.UTF_8));
        try {
            withDialog(source, (i, dialog) -> {
                await(i, dialog, R.id.button_install);
                i.runOnMainSync(() -> {
                    invoke(dialog, "hideButtons", null, null);
                    key(dialog, KeyEvent.KEYCODE_BUTTON_B);
                    assertTrue(dialog.requireDialog().isShowing());
                    invoke(dialog, "onProgress", Integer.class, 4);
                    assertEquals("Choose JAR", ((Button) dialog.requireDialog()
                            .findViewById(R.id.button_install)).getText().toString());
                    assertFalse(dialog.requireDialog().findViewById(R.id.button_start).isShown());
                });
            });
        } finally { Files.deleteIfExists(source.toPath()); }
    }

    @Test public void longTextScrollsWhileHeaderAndTwoActionsFit() {
        Instrumentation i = InstrumentationRegistry.getInstrumentation();
        i.runOnMainSync(() -> {
            ContextThemeWrapper context = new ContextThemeWrapper(i.getTargetContext(), R.style.AppTheme);
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.fontScale = 1.5f;
            ContextThemeWrapper large = new ContextThemeWrapper(i.getTargetContext(), R.style.AppTheme);
            large.applyOverrideConfiguration(config);
            DialogInstallerBinding b = DialogInstallerBinding.inflate(LayoutInflater.from(large));
            b.installerTitle.setText("A very long game title for a small screen");
            StringBuilder message = new StringBuilder();
            for (int n = 0; n < 20; n++) message.append("An installation warning with details.\n");
            b.installerMessage.setText(message);
            b.buttonInstall.setText("Reinstall");
            float density = large.getResources().getDisplayMetrics().density;
            for (int width : new int[]{296, 456}) {
                View root = b.getRoot();
                root.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width * density), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(Math.round(240 * density), View.MeasureSpec.EXACTLY));
                root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                b.installerScroll.scrollTo(0, 0);
                assertTrue(b.installerScroll.getHeight() > 48 * density);
                assertTrue(b.installerScroll.canScrollVertically(1));
                assertTrue(b.installerActions.getBottom() <= root.getHeight());
                assertTrue(b.buttonClose.getWidth() >= 48 * density);
                for (Button button : new Button[]{b.buttonInstall, b.buttonStart}) {
                    assertTrue(button.getHeight() >= 48 * density);
                    assertTrue(button.getLayout().getHeight() <= button.getHeight()
                            - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
                }
                int actionsTop = b.installerActions.getTop();
                b.installerScroll.scrollTo(0, 2000);
                assertEquals(actionsTop, b.installerActions.getTop());
            }
        });
    }

    @Test public void missingGamePreparationErrorExitsWithControllerBack() throws Exception {
        Instrumentation i = InstrumentationRegistry.getInstrumentation();
        ru.playsoftware.j2meloader.config.CompatibilityTestActivity activity =
                (ru.playsoftware.j2meloader.config.CompatibilityTestActivity) i.startActivitySync(new Intent(
                        i.getTargetContext(), ru.playsoftware.j2meloader.config.CompatibilityTestActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            java.lang.reflect.Field field = activity.getClass().getDeclaredField("errorDialog");
            field.setAccessible(true);
            GameMenuDialog dialog = (GameMenuDialog) field.get(activity);
            assertNotNull(dialog);
            i.runOnMainSync(() -> {
                assertTrue(dialog.isShowing());
                dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B));
                dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B));
            });
            i.waitForIdleSync();
            i.runOnMainSync(() -> assertTrue(activity.isFinishing()));
        } finally { i.runOnMainSync(activity::finish); }
    }

    private static File fixture(String suffix) throws Exception {
        return File.createTempFile("installer-ui-", suffix,
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
    }
    private interface Check { void run(Instrumentation i, InstallerDialog dialog) throws Exception; }
    private void withDialog(File source, Check check) throws Exception {
        Instrumentation i = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = (MainActivity) i.startActivitySync(new Intent(i.getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        InstallerDialog dialog = InstallerDialog.newInstance(Uri.fromFile(source));
        try {
            i.runOnMainSync(() -> dialog.showNow(activity.getSupportFragmentManager(), "installer-ui-test"));
            check.run(i, dialog);
        } finally {
            i.runOnMainSync(() -> { dialog.dismissAllowingStateLoss(); activity.finish(); });
            i.waitForIdleSync();
        }
    }
    private static void await(Instrumentation i, InstallerDialog dialog, int id) {
        AtomicBoolean shown = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            i.runOnMainSync(() -> shown.set(dialog.getDialog() != null && dialog.requireDialog().findViewById(id).isShown()));
            if (!shown.get()) SystemClock.sleep(50);
        } while (!shown.get() && SystemClock.uptimeMillis() < deadline);
        assertTrue("Installer state did not become visible", shown.get());
        i.waitForIdleSync();
    }
    private static void invoke(Object target, String name, Class<?> type, Object value) {
        try {
            Method method = type == null ? target.getClass().getDeclaredMethod(name)
                    : target.getClass().getDeclaredMethod(name, type);
            method.setAccessible(true);
            if (type == null) method.invoke(target); else method.invoke(target, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void key(InstallerDialog dialog, int code) {
        dialog.requireDialog().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        dialog.requireDialog().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }
}
