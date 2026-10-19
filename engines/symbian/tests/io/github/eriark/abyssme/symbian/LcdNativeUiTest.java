package io.github.eriark.abyssme.symbian;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

public class LcdNativeUiTest {
    @Test public void nativeScreenshotExports() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdNativeExport")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SymbianLibraryIndex.Entry game=new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull("Provision the private Snakes fixture",game);
        new ru.playsoftware.j2meloader.input.TouchAppearanceUiTest().checkScreenshotExports(() -> {
            context.startActivity(new Intent(context,SymbianActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        });
    }
    @Test public void nativeEffectsApplyUndoAndPersist() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdNative")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SymbianLibraryIndex.Entry game=new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull("Provision the private Snakes fixture",game);
        new ru.playsoftware.j2meloader.input.TouchAppearanceUiTest().checkLcdEffects(() -> {
            context.startActivity(new Intent(context,SymbianActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        });
    }
}
