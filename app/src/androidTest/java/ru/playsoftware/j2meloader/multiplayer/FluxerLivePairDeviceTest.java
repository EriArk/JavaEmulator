/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.graphics.Bitmap;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonObject;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

/** Two authorized accounts on separate devices, real HTTP and production actors. Explicit opt-in only. */
public class FluxerLivePairDeviceTest {
    @Test public void privateInvitationAdmissionAndLeaveWithExistingFriend() throws Exception {
        String role = InstrumentationRegistry.getArguments().getString("fluxerPairRole", "");
        String peerName = InstrumentationRegistry.getArguments().getString("fluxerPairPeer", "");
        Assume.assumeTrue("Explicit host/guest role and authorized test peer required",
                (role.equals("host") || role.equals("guest")) && !peerName.isEmpty());
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Assume.assumeTrue("Sign in normally first", new FluxerCredentialStore(app).read() != null);
        // Read an existing friend; never create a relationship or select an arbitrary account.
        String peer;
        try (ActivityScenario<FluxerChatsActivity> screen = ActivityScenario.launch(FluxerChatsActivity.class)) {
            AtomicReference<FluxerChats.State> chats = new AtomicReference<>();
            long deadline = System.currentTimeMillis() + 30000;
            do {
                screen.onActivity(a -> chats.set(new ViewModelProvider(a).get(FluxerChatsModel.class).state.getValue()));
                if (chats.get() != null && chats.get().friendsReady) break;
                Thread.sleep(200);
            } while (System.currentTimeMillis() < deadline);
            assertNotNull(chats.get());assertTrue(chats.get().friendsReady);
            List<FluxerChats.Friend> matches = chats.get().friends.stream()
                    .filter(f -> f.type == 1 && (f.name.equals(peerName) || f.tag.equals(peerName)))
                    .collect(Collectors.toList());
            assertEquals("Expected exactly the designated existing friend", 1, matches.size());
            peer = matches.get(0).id;
        }
        AtomicReference<LobbyRoomModel> owner = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> owner.set(LobbyRoomModel.get(app)));
        LobbyRoomModel model = owner.get();
        try (ActivityScenario<LobbyRoomActivity> screen = ActivityScenario.launch(LobbyRoomActivity.class)) {
            LobbyRoomModel.State initial = await(model, s -> s.verified && s.friends.containsKey(peer), 30000);
            Assume.assumeTrue("Never replace an existing room", initial.room.isEmpty());
            String account = initial.account;
            String testRoom = "";
            try {
                if (role.equals("host")) {
                    model.action(account, r -> r.create("private"));
                    testRoom = await(model, s -> s.hosting, 10000).room;
                    model.action(account, r -> r.invite(peer));
                } else {
                    LobbyRoomModel.State invited = await(model,
                            s -> s.invitations.stream().anyMatch(i -> LobbyRoom.text(i, "host").equals(peer)), 120000);
                    JsonObject invitation = invited.invitations.stream()
                            .filter(i -> LobbyRoom.text(i, "host").equals(peer)).findFirst().get();
                    testRoom = LobbyRoom.text(invitation, "lobby");
                    String key = LobbyRoom.text(invitation, "key");
                    capture("guest-invitation.png");
                    model.action(account, r -> r.answer(key, true));
                }
                String expectedRoom = testRoom;
                await(model, s -> s.room.equals(expectedRoom) && s.available && members(s) == 2, 90000);
                screen.recreate();
                await(model, s -> s.room.equals(expectedRoom) && s.available && connectedPair(s), 90000);
                screen.onActivity(a -> {
                    View ready = a.getWindow().getDecorView().findViewWithTag("ready");
                    assertNotNull(ready);assertFalse("No game adapter is registered", ready.isEnabled());
                });
                capture(role + "-admitted.png");
                if (role.equals("guest")) {
                    Thread.sleep(45000);
                    await(model, s -> s.room.equals(expectedRoom) && s.available && connectedPair(s), 30000);
                    capture(role + "-heartbeat.png");
                    model.action(account, LobbyRoom::leave);
                    await(model, s -> s.room.isEmpty(), 10000);
                } else {
                    await(model, s -> s.room.equals(expectedRoom) && members(s) == 1, 120000);
                    model.action(account, LobbyRoom::leave);
                    await(model, s -> s.room.isEmpty(), 10000);
                }
                model.refresh();
                await(model, s -> s.friends.containsKey(peer) && s.room.isEmpty(), 15000);
                capture(role + "-left.png");
            } finally {
                String ownedRoom = testRoom;
                if (!ownedRoom.isEmpty()) model.action(account, r -> {
                    if (r.room().equals(ownedRoom)) r.leave();
                });
            }
        }
    }
    private static int members(LobbyRoomModel.State state) {
        return state.snapshot.has("members") ? state.snapshot.getAsJsonArray("members").size() : 0;
    }
    private static boolean connectedPair(LobbyRoomModel.State state) {
        if (members(state) != 2) return false;
        for (com.google.gson.JsonElement member : state.snapshot.getAsJsonArray("members"))
            if (!LobbyRoom.bool(member.getAsJsonObject(), "connected")) return false;
        return true;
    }
    private static LobbyRoomModel.State await(LobbyRoomModel model, Predicate<LobbyRoomModel.State> predicate, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        LobbyRoomModel.State last;
        do {
            last = model.state.getValue();
            if (last != null && predicate.test(last)) return last;
            Thread.sleep(200);
        } while (System.currentTimeMillis() < deadline);
        fail("Live room timeout: " + (last == null ? "no state" : last.status + "; members=" + members(last)));
        return null;
    }
    private static void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "fluxer-pair-checks");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(dir, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
