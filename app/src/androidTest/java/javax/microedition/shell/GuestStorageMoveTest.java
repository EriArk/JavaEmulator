/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.shell;

import android.content.Context;
import android.os.Environment;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import org.junit.Test;
import org.junit.Assume;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.storage.LibraryMove;
import static org.junit.Assert.*;

/** Run each phase in a fresh process, with a dedicated debug package and removable card. */
public class GuestStorageMoveTest {
    @Test public void legacyPrivateAndCacheFilesFollowTheLibrary() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("movePhase");
        Assume.assumeNotNull(phase);
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(app.getPackageName().endsWith(".filecheck.debug"));
        File primary = app.getExternalFilesDir(null), card = null;
        for (File volume : app.getExternalFilesDirs(null)) {
            if (volume != null && Environment.isExternalStorageRemovable(volume)) card = volume;
        }
        assertNotNull(card);
        boolean first = "outbound".equals(phase), onCard = "return".equals(phase);
        assertTrue(first || onCard || "reopened".equals(phase));
        assertTrue(StoragePaths.library(app).toPath().startsWith((onCard ? card : primary).toPath()));
        String game = "Storage probe";
        new MicroLoader(app, new File(Config.getAppDir(),game).getPath()).setFileProperties();
        String legacy = primary.toPath().resolve("data").resolve(game).toString()
                .substring(Environment.getExternalStorageDirectory().getPath().length());
        for (String type : new String[]{"private", "cache"}) {
            String expectedUri = "file:///c:" + legacy + "/" + type;
            String uri = MidletSystem.getProperty("fileconn.dir." + type);
            assertEquals(expectedUri, uri);
            if (first) assertTrue(new File(Config.getFsInternalDir(), legacy.substring(1) + "/" + type).mkdirs());
            FileConnection file = (FileConnection)Connector.open(uri + "/slot.dat");
            try {
                if (first) file.create();
                else {
                    try (InputStream in = file.openInputStream()) {
                        assertEquals(onCard ? 41 : 42, in.read());
                        assertEquals(-1, in.read());
                    }
                }
                if (first || onCard) {
                    try (OutputStream out = file.openOutputStream()) { out.write(first ? 41 : 42); }
                }
            } finally { file.close(); }
        }
        if (first || onCard) {
            LibraryMove move = new LibraryMove(app);
            move.begin(move.plan(first ? card : primary), ignored -> {});
        }
    }
}
