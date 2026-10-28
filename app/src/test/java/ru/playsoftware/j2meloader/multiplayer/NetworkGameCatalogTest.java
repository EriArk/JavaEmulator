/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
import static org.junit.Assert.*;

public class NetworkGameCatalogTest {
    @Rule public TemporaryFolder root=new TemporaryFolder();
    static String manifest(String entry){return "MIDlet-Name: Fixture\nMIDlet-Version: 1.0\nMIDlet-Vendor: AbyssME\nMIDlet-1: Fixture,,"+entry+"\n";}
    File game(String jarManifest,String descriptor)throws Exception{
        File dir=root.newFolder();
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(new File(dir,"res.jar")))){
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));out.write(jarManifest.getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }
        Files.write(new File(dir,"converted.dex.conf").toPath(),descriptor.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir,"converted.dex").toPath(),new byte[]{1,2,3});return dir;
    }
    @Test public void installedSingleEntryIsVerifiedFromActualBytes()throws Exception{
        File dir=game(manifest("demo.Game"),manifest("demo.Game"));
        assertEquals(1,NetworkGameCatalog.list(root.getRoot()).size());
        InstalledNetworkGame checked=NetworkGameCatalog.verify(root.getRoot(),new NetworkGameCatalog.Entry(dir,"Not the game identity"));
        checked.recheck(dir.getCanonicalPath());assertEquals(64,checked.game.contentHash.length());
    }
    @Test public void changedDescriptorCannotSelectAnotherMidletFromSameJar()throws Exception{
        File dir=game(manifest("demo.Game"),manifest("demo.Other"));
        assertThrows(IOException.class,()->NetworkGameCatalog.verify(root.getRoot(),new NetworkGameCatalog.Entry(dir,"Fixture")));
    }
    @Test public void multiEntryArchiveCannotBeHiddenBySingleEntryDescriptor()throws Exception{
        File dir=game(manifest("demo.Game")+"MIDlet-2: Other,,demo.Other\n",manifest("demo.Game"));
        assertThrows(IOException.class,()->NetworkGameCatalog.verify(root.getRoot(),new NetworkGameCatalog.Entry(dir,"Fixture")));
    }
    @Test public void multiEntryAndOtherPlatformsAreNotOffered()throws Exception{
        game(manifest("demo.Game"),manifest("demo.Game")+"MIDlet-2: Other,,demo.Other\n");root.newFolder();
        assertTrue(NetworkGameCatalog.list(root.getRoot()).isEmpty());
    }
    @Test public void outOfLibraryEntryIsRejected()throws Exception{
        File dir=game(manifest("demo.Game"),manifest("demo.Game"));File other=root.newFolder();
        assertThrows(IOException.class,()->NetworkGameCatalog.verify(other,new NetworkGameCatalog.Entry(dir,"Fixture")));
    }
    @Test public void oversizedCompressedManifestFailsBeforeUnboundedInflation()throws Exception{
        char[] chars=new char[1024*1024+1];java.util.Arrays.fill(chars,'a');
        File dir=game(new String(chars),manifest("demo.Game"));
        assertThrows(IOException.class,()->NetworkGameCatalog.verify(root.getRoot(),new NetworkGameCatalog.Entry(dir,"Fixture")));
    }
    @Test public void inaccessibleLibraryAndCancellationHaveStableIdentities() throws Exception {
        assertEquals(SocialNotice.GAME_LIBRARY_UNAVAILABLE,assertThrows(NetworkGameException.class,
                ()->NetworkGameCatalog.list(new File(root.getRoot(),"missing"))).notice);
        game(manifest("demo.Game"),manifest("demo.Game"));
        try {
            Thread.currentThread().interrupt();
            assertEquals(SocialNotice.GAME_LOOKUP_CANCELLED,
                    assertThrows(NetworkGameException.class,()->NetworkGameCatalog.list(root.getRoot())).notice);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {Thread.interrupted();}
    }
    @Test public void descriptorMismatchKeepsActionableIdentity() throws Exception {
        File dir=game(manifest("demo.Game"),manifest("demo.Other"));
        assertEquals(SocialNotice.GAME_ENTRY_DIFFERS,assertThrows(NetworkGameException.class,
                ()->NetworkGameCatalog.verify(root.getRoot(),new NetworkGameCatalog.Entry(dir,"Fixture"))).notice);
    }
}
