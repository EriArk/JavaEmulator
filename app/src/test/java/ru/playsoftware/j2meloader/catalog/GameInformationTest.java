package ru.playsoftware.j2meloader.catalog;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;

public class GameInformationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private AppItem item(File folder, String version) {
        return new AppItem("unused", "Renamed title", "Cached vendor", version) {
            @Override public String getPathExt() { return folder.getAbsolutePath(); }
        };
    }
    private void descriptor(File folder, String text) throws Exception {
        Files.write(new File(folder, Config.MIDLET_MANIFEST_FILE).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
    @Test public void installedResolvedMetadataWinsWithoutChangingFiles() throws Exception {
        File folder = temporary.newFolder();
        String fields = "MIDlet-Name: Original title\r\nMIDlet-Version: 01.02.003\r\nMIDlet-Vendor: Real vendor\r\n";
        descriptor(folder, fields);
        assertEquals("Platform: Java ME\nVersion: 01.02.003\nVendor: Real vendor", GameInformation.describe(item(folder,"9.9")));
        assertEquals(fields, new String(Files.readAllBytes(new File(folder,Config.MIDLET_MANIFEST_FILE).toPath()), StandardCharsets.UTF_8));
    }
    @Test public void missingDescriptorUsesCachedFieldsButMissingVersionIsHonest() throws Exception {
        File folder = temporary.newFolder();
        assertTrue(GameInformation.describe(item(folder,"1.0")).contains("Version: 1.0"));
        descriptor(folder,"MIDlet-Name: Test\r\nMIDlet-Vendor: Vendor\r\n");
        assertTrue(GameInformation.describe(item(folder,"9.9")).contains("Version: Not provided"));
    }
    @Test public void brokenDescriptorIsNotReimportedOrHiddenByStaleVersion() throws Exception {
        File folder = temporary.newFolder(); descriptor(folder,"not a descriptor\r\n");
        assertTrue(GameInformation.describe(item(folder,"9.9")).contains("Version: Unavailable"));
        assertEquals(1, folder.list().length);
    }
    @Test public void jarOnlyAndMergedJadFollowTheInstallersDescriptorSemantics() throws Exception {
        File folder=temporary.newFolder();
        ru.woesss.j2me.jar.Descriptor jar=new ru.woesss.j2me.jar.Descriptor(
                "MIDlet-Name: Test\nMIDlet-Vendor: Vendor\nMIDlet-Version: 1.01\n",false);
        jar.writeTo(new File(folder,Config.MIDLET_MANIFEST_FILE));
        assertTrue(GameInformation.describe(item(folder,null)).contains("Version: 1.01"));
        ru.woesss.j2me.jar.Descriptor jad=new ru.woesss.j2me.jar.Descriptor(
                "MIDlet-Name: Test\nMIDlet-Vendor: Vendor\nMIDlet-Version: 2.00\nMIDlet-Jar-URL: test.jar\nMIDlet-Jar-Size: 123\n",true);
        jar.merge(jad); jar.writeTo(new File(folder,Config.MIDLET_MANIFEST_FILE));
        assertTrue(GameInformation.describe(item(folder,"1.01")).contains("Version: 2.00"));
    }
    @Test public void sameTitleDoesNotMixVersionsBetweenInstallations() throws Exception {
        File a=temporary.newFolder(), b=temporary.newFolder();
        descriptor(a,"MIDlet-Version: 1.0\r\n"); descriptor(b,"MIDlet-Version: 2.0\r\n");
        assertTrue(GameInformation.describe(item(a,null)).contains("Version: 1.0"));
        assertTrue(GameInformation.describe(item(b,null)).contains("Version: 2.0"));
    }
}
