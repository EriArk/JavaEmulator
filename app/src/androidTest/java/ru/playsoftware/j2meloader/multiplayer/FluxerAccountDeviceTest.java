/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Uses an isolated vault. Optional live check creates/cancels an anonymous handoff, never signs in. */
public class FluxerAccountDeviceTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private interface VaultCheck { void run(FluxerCredentialStore vault, File file, String alias) throws Exception; }
    private void vault(VaultCheck check) throws Exception {
        File file = new File(context().getNoBackupFilesDir(), "auth-test-" + UUID.randomUUID());
        String alias = "abyssme.auth-test." + UUID.randomUUID();
        try { check.run(new FluxerCredentialStore(file, alias), file, alias); }
        finally {
            new android.util.AtomicFile(file).delete();
            KeyStore keys=KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry(alias);
        }
    }
    @Test public void vaultEncryptsAndSurvivesReopeningWithoutPlaintextFallback() throws Exception {
        vault((store,file,alias) -> {
            assertNull(store.read()); String token="fixture-only-not-a-real-session-token"; store.save(token);
            byte[] first=Files.readAllBytes(file.toPath());
            assertFalse(new String(first,StandardCharsets.ISO_8859_1).contains(token));
            assertEquals(token,new FluxerCredentialStore(file,alias).read());
            store.save(token); assertFalse(java.util.Arrays.equals(first,Files.readAllBytes(file.toPath())));
            store.clear(); assertNull(store.read());
        });
    }
    @Test public void vaultRejectsModifiedCiphertextAndLostKey() throws Exception {
        vault((store,file,alias) -> {
            store.save("fixture-only-not-a-real-session-token"); byte[] data=Files.readAllBytes(file.toPath());
            data[data.length-1]^=1; Files.write(file.toPath(),data); assertThrows(Exception.class,store::read);
            store.save("fixture-only-not-a-real-session-token");
            KeyStore keys=KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry(alias);
            assertThrows(Exception.class,store::read); store.clear(); assertNull(store.read());
        });
    }
    @Test public void authTransportRejectsForeignPathsAndHeaderInjection() {
        assertEquals(0,FluxerHttpTransport.exchange("GET","//evil.invalid",null,null).status);
        assertEquals(0,FluxerHttpTransport.exchange("GET","/v1/users/@me",null,"bad\r\nheader").status);
        assertEquals(0,FluxerHttpTransport.exchange("DELETE","/v1/channels/123/messages",null,"not-an-actual-token").status);
    }
    @Test public void signedOutScreenRecreatesAndFitsBothOrientations() throws Exception {
        Assume.assumeFalse("Never replace an existing account", new File(context().getNoBackupFilesDir(),"fluxer-session.bin").exists());
        try(ActivityScenario<FluxerAccountActivity> scenario=ActivityScenario.launch(FluxerAccountActivity.class)) {
            await(scenario,FluxerAuth.Phase.SIGNED_OUT);
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}) {
                scenario.onActivity(a -> a.setRequestedOrientation(orientation)); Thread.sleep(800);
                scenario.recreate(); await(scenario,FluxerAuth.Phase.SIGNED_OUT); Thread.sleep(250);
                scenario.onActivity(a -> {
                    Button button=a.findViewById(R.id.fluxer_primary);
                    assertTrue(button.isShown()); assertTrue(button.isEnabled());
                    assertTrue("Screen obscured by another window", button.hasWindowFocus());
                    assertEquals("Continue with Fluxer",button.getText().toString());
                    assertTrue(button.getWidth()>0); assertTrue(button.getHeight()>=48*a.getResources().getDisplayMetrics().density);
                    int[] position=new int[2]; button.getLocationOnScreen(position);
                    assertTrue(position[0]>=0); assertTrue(position[0]+button.getWidth()<=a.getResources().getDisplayMetrics().widthPixels);
                    button.requestFocusFromTouch(); assertTrue(button.hasFocus());
                });
                capture("fluxer-signed-out-"+(orientation==0?"landscape":"portrait")+".png");
            }
        }
    }
    @Test public void lightThemeKeepsTextAndActionsReadable() throws Exception {
        Assume.assumeFalse("Never replace an existing account", new File(context().getNoBackupFilesDir(),"fluxer-session.bin").exists());
        try(ActivityScenario<FluxerAccountActivity> scenario=ActivityScenario.launch(FluxerAccountActivity.class)) {
            scenario.onActivity(a -> a.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));
            await(scenario,FluxerAuth.Phase.SIGNED_OUT); Thread.sleep(500);
            scenario.onActivity(a -> {
                TextView status=a.findViewById(R.id.fluxer_status);
                int background=ru.playsoftware.j2meloader.ui.AppPalette.color(a,R.color.background);
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(status.getCurrentTextColor(),background)>=4.5);
                assertTrue(a.findViewById(R.id.fluxer_primary).hasWindowFocus());
            });
            capture("fluxer-signed-out-light.png");
        }
    }
    @Test public void liveAnonymousHandoffSurvivesRecreationAndCancels() throws Exception {
        Assume.assumeTrue("Opt-in public-service check", "true".equals(InstrumentationRegistry.getArguments().getString("fluxerLive")));
        Assume.assumeFalse("Never replace an existing account", new File(context().getNoBackupFilesDir(),"fluxer-session.bin").exists());
        try(ActivityScenario<FluxerAccountActivity> scenario=ActivityScenario.launch(FluxerAccountActivity.class)) {
            await(scenario,FluxerAuth.Phase.SIGNED_OUT);
            scenario.onActivity(a -> a.findViewById(R.id.fluxer_primary).performClick());
            await(scenario,FluxerAuth.Phase.WAITING);
            AtomicReference<String> code=new AtomicReference<>();
            scenario.onActivity(a -> code.set(((TextView)a.findViewById(R.id.fluxer_code)).getText().toString()));
            scenario.recreate(); await(scenario,FluxerAuth.Phase.WAITING);
            scenario.onActivity(a -> {
                // Compare without putting short-lived approval codes in assertion output.
                assertTrue(code.get().equals(((TextView)a.findViewById(R.id.fluxer_code)).getText().toString()));
                assertTrue((a.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0);
            });
            Thread.sleep(3000); await(scenario,FluxerAuth.Phase.WAITING);
            scenario.onActivity(a -> assertEquals("Enter this code on Fluxer and approve sign-in.",
                    ((TextView)a.findViewById(R.id.fluxer_status)).getText().toString()));
            scenario.onActivity(a -> a.findViewById(R.id.fluxer_secondary).performClick());
            await(scenario,FluxerAuth.Phase.SIGNED_OUT);
            capture("fluxer-cancelled.png");
            assertFalse(new File(context().getNoBackupFilesDir(),"fluxer-session.bin").exists());
        }
    }
    private void await(ActivityScenario<FluxerAccountActivity> scenario, FluxerAuth.Phase expected) throws Exception {
        AtomicReference<FluxerAuth.State> state=new AtomicReference<>();
        long until=System.currentTimeMillis()+35000;
        do {
            scenario.onActivity(a -> state.set(new ViewModelProvider(a).get(FluxerAccountModel.class).state.getValue()));
            if(state.get()!=null && state.get().phase==expected) return;
            Thread.sleep(100);
        } while(System.currentTimeMillis()<until);
        fail("Expected "+expected+", got "+(state.get()==null?"no state":state.get().phase+": "+state.get().message));
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(250);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        File dir=new File(context().getExternalFilesDir(null),"fluxer-account-checks"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally { bitmap.recycle(); }
    }
}
