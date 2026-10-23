/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.Assert.*;

/** Device storage/runtime checks only. No Fluxer account, external server or real game. */
public class MultiplayerCoreDeviceTest {
    @Test public void journalSurvivesAtomicReplaceAndKeepsAccountsSeparate() throws Exception {
        Path root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getNoBackupFilesDir().toPath(), "contact-test-");
        try {
            FileContactJournal first = new FileContactJournal(root, "1"), second = new FileContactJournal(root, "2");
            JsonObject data = new JsonObject(); data.addProperty("v", 1); data.addProperty("self", "1"); data.add("contacts", new JsonObject());
            first.save(data); assertEquals(data, first.load()); assertNull(second.load());
            data.addProperty("revision", 2); first.save(data); assertEquals(data, new FileContactJournal(root,"1").load());
        } finally {
            Files.deleteIfExists(root.resolve("1/temporary-contacts.json"));
            Files.deleteIfExists(root.resolve("2/temporary-contacts.json"));
            Files.deleteIfExists(root.resolve("1")); Files.deleteIfExists(root.resolve("2")); Files.delete(root);
        }
    }

    @Test public void contactIntentIsPersistedBeforeAnyProviderMutation() throws Exception {
        Path root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getNoBackupFilesDir().toPath(), "contact-test-");
        try {
            FileContactJournal journal = new FileContactJournal(root,"1");
            String room = UUID.randomUUID().toString();
            TemporaryContacts actor = new TemporaryContacts("1",journal,(method,path,body,done)->{
                assertEquals("GET",method); // A peer must agree before any POST can happen.
                try { assertEquals("baseline",journal.load().getAsJsonObject("contacts").getAsJsonObject("2").get("phase").getAsString()); }
                catch(Exception e) { throw new AssertionError(e); }
                done.accept(new TemporaryContacts.Reply(200,new com.google.gson.JsonArray(),0));
            },(peer,packet)->assertEquals("offer",packet.getAsJsonObject("data").get("op").getAsString()),1792800000);
            actor.context(room,"1",new HashSet<>(Arrays.asList("1","2")),true,false); actor.tick(1792800001);
            assertEquals("offer",actor.state().getAsJsonObject("peers").getAsJsonObject("2").get("phase").getAsString()); actor.close();
        } finally {
            Files.deleteIfExists(root.resolve("1/temporary-contacts.json")); Files.deleteIfExists(root.resolve("1")); Files.delete(root);
        }
    }

    @Test public void deviceLoopbackPreservesL2capPackets() throws Exception {
        try (ServerSocket listener = new ServerSocket(0,1,InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(),listener.getLocalPort());
             Socket server = listener.accept()) {
            client.setSoTimeout(2000); server.setSoTimeout(2000);
            FramedL2capConnection a = new FramedL2capConnection(client.getInputStream(),client.getOutputStream(),client,672,672);
            FramedL2capConnection b = new FramedL2capConnection(server.getInputStream(),server.getOutputStream(),server,672,672);
            a.send(new byte[]{1,2,3}); a.send(new byte[]{4,5});
            assertEquals(1,b.receive(new byte[1])); byte[] next = new byte[2]; assertEquals(2,b.receive(next)); assertArrayEquals(new byte[]{4,5},next);
            b.send(new byte[0]); assertEquals(0,a.receive(next)); a.close(); b.close();
        }
    }

    @Test public void accountAndJournalSymlinksRemainRejected() throws Exception {
        Path root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getNoBackupFilesDir().toPath(), "contact-test-");
        try {
            Files.createDirectory(root.resolve("real"));
            Files.createSymbolicLink(root.resolve("1"),root.resolve("real"));
            assertThrows(java.io.IOException.class,()->new FileContactJournal(root,"1"));
            Files.delete(root.resolve("1"));
            new FileContactJournal(root,"1");
            Files.createSymbolicLink(root.resolve("1/temporary-contacts.json"),root.resolve("real/contacts.json"));
            assertThrows(java.io.IOException.class,()->new FileContactJournal(root,"1"));
        } finally {
            Files.deleteIfExists(root.resolve("1/temporary-contacts.json")); Files.deleteIfExists(root.resolve("1"));
            Files.deleteIfExists(root.resolve("real")); Files.delete(root);
        }
    }
}
