/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Opt-in real-account checks; directory access may join the shared lobby community. */
public class FluxerLiveAccountDeviceTest {
    private void optIn() {
        Assume.assumeTrue("Requires an approved test account and permission to join the lobby community",
                "true".equals(InstrumentationRegistry.getArguments().getString("fluxerApprovedLive")));
    }
    private File privateFile(String name) {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getNoBackupFilesDir(), name);
    }
    @Test public void assistedBrowserApprovalSavesNewSession() throws Exception {
        Assume.assumeTrue("Requires an operator to approve the displayed code in Fluxer",
                "true".equals(InstrumentationRegistry.getArguments().getString("fluxerApproveSignIn")));
        Assume.assumeFalse("Never replace an existing account", privateFile("fluxer-session.bin").exists());
        try (ActivityScenario<FluxerAccountActivity> s = ActivityScenario.launch(FluxerAccountActivity.class)) {
            awaitAccount(s, FluxerAuth.Phase.SIGNED_OUT, 15000);
            s.onActivity(a -> a.findViewById(R.id.fluxer_primary).performClick());
            FluxerAuth.State pending = awaitAccount(s, FluxerAuth.Phase.WAITING, 30000);
            // Same short-lived user code that the UI permits copying; never report poll secrets/tokens.
            Bundle report = new Bundle();report.putString("approval_code", pending.code);
            InstrumentationRegistry.getInstrumentation().sendStatus(2, report);
            assertTrue(awaitAccount(s, FluxerAuth.Phase.CONNECTED, 240000).remembered);
        }
    }
    @Test public void savedAccountRestoresInNewProcess() throws Exception {
        optIn();
        Assume.assumeTrue(privateFile("fluxer-session.bin").exists());
        try (ActivityScenario<FluxerAccountActivity> s = ActivityScenario.launch(FluxerAccountActivity.class)) {
            assertTrue(awaitAccount(s, FluxerAuth.Phase.CONNECTED, 35000).remembered);
            s.recreate();
            assertTrue(awaitAccount(s, FluxerAuth.Phase.CONNECTED, 35000).remembered);
            capture("account-restored.png");
        }
    }
    @Test public void directoryAutomaticallyObtainsCommunityAccess() throws Exception {
        optIn();
        Assume.assumeTrue(privateFile("fluxer-session.bin").exists());
        try (ActivityScenario<LobbyDirectoryActivity> s = ActivityScenario.launch(LobbyDirectoryActivity.class)) {
            AtomicReference<LobbyDirectory.State> state = new AtomicReference<>();
            long until = System.currentTimeMillis() + 60000;
            do {
                s.onActivity(a -> state.set(new ViewModelProvider(a).get(LobbyDirectoryModel.class).state.getValue()));
                LobbyDirectory.State current = state.get();
                if (current != null && !current.busy && current.verified) break;
                Thread.sleep(200);
            } while (System.currentTimeMillis() < until);
            assertNotNull(state.get());
            capture("directory-live.png");
            assertTrue("Account not verified", state.get().verified);
            assertFalse("Community access required: " + state.get().status, state.get().needsCommunity);
            assertFalse("Directory still loading", state.get().busy);
            assertTrue("Directory read failed: " + state.get().status,
                    state.get().status.equals("No compatible lobbies right now.") || !state.get().rooms.isEmpty());
        }
    }
    private FluxerAuth.State awaitAccount(ActivityScenario<FluxerAccountActivity> s,
                                          FluxerAuth.Phase phase, long timeout) throws Exception {
        AtomicReference<FluxerAuth.State> state = new AtomicReference<>();
        long until = System.currentTimeMillis() + timeout;
        do {
            s.onActivity(a -> state.set(new ViewModelProvider(a).get(FluxerAccountModel.class).state.getValue()));
            if (state.get() != null && state.get().phase == phase) return state.get();
            Thread.sleep(200);
        } while (System.currentTimeMillis() < until);
        fail("Expected " + phase + ", got " + (state.get() == null ? "no state" : state.get().phase + ": " + state.get().message));
        return null;
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "fluxer-live-checks");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
}
