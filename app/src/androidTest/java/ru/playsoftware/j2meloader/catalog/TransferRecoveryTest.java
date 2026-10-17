package ru.playsoftware.j2meloader.catalog;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransferRecoveryTest {
    @Test public void restoredPickerResultIsNotRacedByAutomaticScan() {
        pendingPicker(false);
    }

    @Test public void cancellingRestoredPickerRescansPreviousSourceForReview() {
        pendingPicker(true);
    }

    private void pendingPicker(boolean cancelled) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Application app = (Application) InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getApplicationContext();
            LibraryImportModel model = new LibraryImportModel(app);
            Bundle saved = new Bundle();
            Uri game = Uri.parse("content://example.documents/tree/game");
            Uri saves = Uri.parse("content://example.documents/tree/saves");
            saved.putParcelable("game", game);
            try {
                model.restoreState(saved, true);
                assertFalse("Wait for the pending DocumentsUI result", model.state.getValue().busy);
                model.pickerReturned(cancelled);
                if (!cancelled) {
                    model.scanSingle(1, saves);
                    assertEquals(saves, model.saveState().getParcelable("data"));
                }
                assertEquals(game, model.saveState().getParcelable("game"));
                assertTrue("Resume a read-only preview, not an import", model.state.getValue().busy);
                assertNull(model.state.getValue().results);
            } finally { model.onCleared(); }
        });
    }

    @Test public void pendingWholeLibraryAccessSurvivesSerializedState() {
        verify(false);
    }

    @Test public void pendingSingleGameKeepsSaveAndSettingsFolders() {
        verify(true);
    }

    private void verify(boolean single) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Application app = (Application) InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getApplicationContext();
            LibraryImportModel model = new LibraryImportModel(app);
            Bundle saved = new Bundle();
            Uri root = Uri.parse("content://example.documents/tree/library");
            Uri folder = Uri.parse("content://example.documents/tree/library/document/library%2Fconverted");
            saved.putParcelable(single ? "game" : "root", root);
            if (single) {
                saved.putParcelable("data", Uri.parse("content://example.documents/tree/saves"));
                saved.putParcelable("config", Uri.parse("content://example.documents/tree/configs"));
            }
            saved.putParcelable("access", folder);
            saved.putString("accessName", "converted");
            Bundle grants = new Bundle();
            grants.putString("example.documents:library/data", "content://example.documents/tree/library%2Fdata");
            saved.putBundle("grants", grants);
            Parcel parcel = Parcel.obtain();
            try {
                parcel.writeBundle(saved); parcel.setDataPosition(0);
                model.restoreState(parcel.readBundle(getClass().getClassLoader()));
                assertEquals(single, model.isSingleGame());
                LibraryImportModel.State state = model.state.getValue();
                assertFalse(state.busy); assertNull(state.preview); assertNull(state.results);
                assertEquals(folder, state.access.folder);
                assertTrue(state.status.contains("converted"));
                Bundle roundTrip = model.saveState();
                for (String key : new String[]{"root", "game", "data", "config", "access", "accessName"})
                    assertEquals(key, saved.get(key), roundTrip.get(key));
                assertEquals(grants.getString("example.documents:library/data"),
                        roundTrip.getBundle("grants").getString("example.documents:library/data"));
                model.restoreState(new Bundle());
                assertSame("Recreation must not replace a retained session", state, model.state.getValue());
            } finally { parcel.recycle(); model.onCleared(); }
        });
    }
}
