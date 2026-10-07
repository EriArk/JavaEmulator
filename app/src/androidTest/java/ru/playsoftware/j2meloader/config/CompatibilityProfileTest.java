package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class CompatibilityProfileTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private File root;

    @Before public void setUp() throws Exception {
        root = Files.createTempDirectory(context.getCacheDir().toPath(), "compatibility-").toFile();
    }

    @After public void tearDown() {
        delete(root);
    }

    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        assertTrue(file.delete());
    }

    private void manifest(String extra) throws Exception {
        String text = "Manifest-Version: 1.0\nMIDlet-Name: Fixture\nMIDlet-Version: 1.0\n"
                + "MIDlet-Vendor: Fixture\nMIDlet-1: Fixture,,Fixture\n"
                + "MicroEdition-Profile: MIDP-2.0\nMicroEdition-Configuration: CLDC-1.1\n" + extra;
        Files.write(new File(root, Config.MIDLET_MANIFEST_FILE).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private ProfileModel run() throws Exception {
        ProfileModel model = new ProfileModel(root);
        run(model);
        return model;
    }

    private void run(ProfileModel model) throws Exception {
        CompatibilityProfileTester.run(context, root, model, (progress, message) -> { });
    }

    private void images(boolean reverse, String... specs) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(root, Config.MIDLET_RES_FILE)))) {
            for (int i = 0; i < specs.length; i++) {
                String[] spec = specs[reverse ? specs.length - i - 1 : i].split(":");
                zip.putNextEntry(new ZipEntry(spec[0]));
                Bitmap bitmap = Bitmap.createBitmap(Integer.parseInt(spec[1]), Integer.parseInt(spec[2]), Bitmap.Config.ARGB_8888);
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip));
                bitmap.recycle();
                zip.closeEntry();
            }
        }
    }

    @Test public void noHintsUseNeutralDefaults() throws Exception {
        manifest("");
        ProfileModel model = run();
        assertEquals("Generic MIDP", model.compatibilityProfile);
        assertEquals("low", model.compatibilityConfidence);
        assertEquals(240, model.screenWidth);
        assertEquals(320, model.screenHeight);
        assertEquals(0, model.detectedScreenWidth);
        assertEquals(ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE ? 2 : 1, model.screenGravity);
        assertTrue(model.systemProperties.contains("microedition.platform: J2ME-Loader/Auto"));
    }

    @Test public void vendorAliasesAndSharedApisDoNotBreakTies() throws Exception {
        manifest("Target-Device: Nokia SonyEricsson Sony Ericsson K800 JP-8\n");
        assertEquals("Generic MIDP", run().compatibilityProfile);
        manifest("");
        Files.write(new File(root, Config.MIDLET_DEX_FILE).toPath(),
                "Lcom/nokia/ui/DirectGraphics; Lcom/sonyericsson/ui/Canvas;".getBytes(StandardCharsets.UTF_8));
        assertEquals("Generic MIDP", run().compatibilityProfile);
    }

    @Test public void declaredSamsungWinsOverIncidentalNokiaApi() throws Exception {
        manifest("Target-Device: Samsung GT-S8000\nScreen-Size: 480x800\nNokia-MIDlet-On-Screen-Keypad: no\n");
        Files.write(new File(root, Config.MIDLET_DEX_FILE).toPath(), "Lcom/nokia/ui/DirectGraphics;".getBytes(StandardCharsets.UTF_8));
        ProfileModel model = run();
        assertEquals("Samsung", model.compatibilityProfile);
        assertEquals("high", model.compatibilityConfidence);
        assertEquals(480, model.screenWidth);
        assertEquals(800, model.screenHeight);
    }

    @Test public void declaredSizeWinsOverLargeArtwork() throws Exception {
        manifest("Screen-Size: 176x220\n");
        images(false, "title.png:480:854", "sprite.png:640:640");
        ProfileModel model = run();
        assertEquals(176, model.screenWidth);
        assertEquals(220, model.screenHeight);
    }

    @Test public void screenArtworkBeatsAtlasWithoutAreaBias() throws Exception {
        images(false, "menu/background.png:240:320", "sprite_atlas_480x800.png:640:640", "icon.png:480:800");
        ProfileModel model = run();
        assertEquals(240, model.screenWidth);
        assertEquals(320, model.screenHeight);
        assertEquals(240, model.detectedScreenWidth);
    }

    @Test public void ambiguousArtworkIsIndependentOfZipOrder() throws Exception {
        for (boolean reverse : new boolean[]{false, true}) {
            images(reverse, "title.png:240:320", "menu.png:320:240");
            ProfileModel model = run();
            assertEquals(240, model.screenWidth);
            assertEquals(320, model.screenHeight);
            assertEquals(0, model.detectedScreenWidth);
            assertTrue(model.compatibilityReasons.contains("conflicting"));
        }
    }

    @Test public void wideAndTallTouchResolutionsAreSupported() throws Exception {
        for (String size : new String[]{"480x800", "480x854", "800x480", "854x480"}) {
            manifest("Screen-Size: " + size + "\n");
            ProfileModel model = run();
            assertEquals(size, model.screenWidth + "x" + model.screenHeight);
        }
    }

    @Test public void conflictingDeclaredSizesDoNotPickTheLargest() throws Exception {
        manifest("Screen-Size: 240x320 480x800\n");
        ProfileModel model = run();
        assertEquals(240, model.screenWidth);
        assertEquals(0, model.detectedScreenWidth);
    }

    @Test public void manualPropertiesAndDisplayArePreserved() throws Exception {
        manifest("Target-Device: Samsung\nScreen-Size: 480x800\n");
        ProfileModel model = new ProfileModel(root);
        model.screenWidth = 176;
        model.screenHeight = 220;
        model.screenScaleType = 2;
        model.screenScaleRatio = 85;
        model.screenGravity = 0;
        model.systemProperties = "microedition.platform: My phone\nmicroedition.encoding: UTF-8\n";
        String properties = model.systemProperties;
        run(model);
        assertEquals(properties, model.systemProperties);
        assertEquals(176, model.screenWidth);
        assertEquals(220, model.screenHeight);
        assertEquals(2, model.screenScaleType);
        assertEquals(85, model.screenScaleRatio);
        assertEquals(0, model.screenGravity);
        assertEquals("Samsung", model.suggestedCompatibilityProfile);
        assertEquals(480, model.detectedScreenWidth);
    }

    @Test public void reanalysisPreservesExistingProfileAndPersistsDetectedSize() throws Exception {
        manifest("Screen-Size: 480x800\n");
        ProfileModel model = run();
        model.screenWidth = 240;
        model.screenHeight = 320;
        assertTrue(ProfilesManager.saveConfig(model));
        model = ProfilesManager.loadConfig(root);
        assertEquals(480, model.detectedScreenWidth);
        run(model);
        assertEquals(240, model.screenWidth);
        assertEquals(320, model.screenHeight);
        assertEquals(800, ProfilesManager.loadConfig(root).detectedScreenHeight);
    }

    @Test public void importedOrTemplateProfileIsNotReset() throws Exception {
        manifest("Screen-Size: 480x800\n");
        ProfileModel model = new ProfileModel(root);
        assertTrue(ProfilesManager.saveConfig(model));
        model = ProfilesManager.loadConfig(root);
        String properties = model.systemProperties;
        run(model);
        assertEquals(240, model.screenWidth);
        assertEquals(properties, model.systemProperties);
    }

    @Test public void midpOneDeclarationSurvivesNeutralSelection() throws Exception {
        manifest("MicroEdition-Profile: MIDP-1.0\nMicroEdition-Configuration: CLDC-1.0\n");
        ProfileModel model = run();
        assertTrue(model.systemProperties.contains("microedition.profiles: MIDP-1.0"));
        assertTrue(model.systemProperties.contains("microedition.configuration: CLDC-1.0"));
    }

    @Test public void saveFailureIsNotReportedAsSuccessfulPreparation() throws Exception {
        ProfileModel model = new ProfileModel(new File(root, "missing/dir"));
        try { run(model); fail("Must report save failure"); }
        catch (IOException expected) { }
    }

    @Test public void cancellationDoesNotSaveAProfile() throws Exception {
        Thread.currentThread().interrupt();
        try { run(); fail("Must cancel"); }
        catch (CancellationException expected) { }
        finally { Thread.interrupted(); }
        assertFalse(new File(root, Config.MIDLET_CONFIG_FILE).exists());
    }

    @Test public void distributorBrandingDoesNotChooseADevice() throws Exception {
        manifest("MIDlet-Name: McLaren **samsungpro.ru**\n"
                + "MIDlet-Vendor: Sony Pictures Mobile /b&b by **samsungpro.ru**\n"
                + "MIDlet-Delete-Confirm: New Games For Siemens phones\n"
                + "MIDlet-Info-URL: https://nokia.example/480x800\n");
        ProfileModel model = run();
        assertEquals("Generic MIDP", model.compatibilityProfile);
        assertFalse(model.compatibilityReasons.contains("Sony Ericsson"));
        assertFalse(model.compatibilityReasons.contains("Samsung"));
        assertFalse(model.compatibilityReasons.contains("Siemens"));
        assertEquals(0, model.detectedScreenWidth);
    }

    @Test public void publisherAloneIsWeakEvidence() throws Exception {
        manifest("MIDlet-Vendor: Motorola\n");
        ProfileModel model = run();
        assertEquals("Motorola", model.compatibilityProfile);
        assertEquals("low", model.compatibilityConfidence);
        assertTrue(model.compatibilityReasons.contains("publisher name"));
    }

    @Test public void unusualBackgroundDoesNotBecomeScreenSize() throws Exception {
        images(false, "res/background.png:255:160", "title.png:512:256");
        ProfileModel model = run();
        assertEquals(240, model.screenWidth);
        assertEquals(320, model.screenHeight);
        assertEquals(0, model.detectedScreenWidth);
    }

    @Test public void targetWebsiteIsNotDeviceOrScreenEvidence() throws Exception {
        manifest("Target-Device: https://samsung.example/480x800\n"
                + "Screen-Size: https://nokia.example/240x320\n");
        ProfileModel model = run();
        assertEquals("Generic MIDP", model.compatibilityProfile);
        assertEquals(0, model.detectedScreenWidth);
    }

    @Test public void jarUrlUsesFilenameNotHostOrQuery() throws Exception {
        manifest("MIDlet-Jar-URL: https://nokia.example/480x800/Game_128x160.jar?device=Samsung\n");
        ProfileModel model = run();
        assertEquals("Generic MIDP", model.compatibilityProfile);
        assertEquals(128, model.screenWidth);
        assertEquals(160, model.screenHeight);
    }

    @Test public void commaSeparatedDeclaredScreenIsSupported() throws Exception {
        manifest("Nokia-MIDlet-Original-Display-Size: 240,320\n");
        assertEquals(320, run().detectedScreenHeight);
    }
}
