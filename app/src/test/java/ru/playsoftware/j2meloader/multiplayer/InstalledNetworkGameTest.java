/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class InstalledNetworkGameTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File root, game;
    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    @Before public void setup() throws Exception {
        root = temp.newFolder("converted"); game = new File(root, "game"); assertTrue(game.mkdir());
        for (String file : new String[]{"res.jar", "converted.dex", "converted.dex.conf"}) Files.write(new File(game, file).toPath(), new byte[]{97,98,99});
    }
    private LobbySession.Game selection(String hash) { return new LobbySession.Game("java", hash, "bluetooth", "1", 2); }
    private InstalledNetworkGame verify() throws Exception { return InstalledNetworkGame.verify(root, game, selection(HASH)); }
    @Test public void unchangedGameIsAcceptedWithoutDatabaseHash() throws Exception { verify().recheck(game.getCanonicalPath()); }
    @Test public void changedDexIsRejected() throws Exception {
        InstalledNetworkGame verified = verify(); Files.write(new File(game,"converted.dex").toPath(), new byte[]{1});
        assertThrows(IOException.class, () -> verified.recheck(game.getCanonicalPath()));
    }
    @Test public void changedResourcesAreRejected() throws Exception {
        InstalledNetworkGame verified = verify(); Files.write(new File(game,"res.jar").toPath(), new byte[]{1});
        assertThrows(IOException.class, () -> verified.recheck(game.getCanonicalPath()));
    }
    @Test public void changedManifestIsRejected() throws Exception {
        InstalledNetworkGame verified = verify(); Files.write(new File(game,"converted.dex.conf").toPath(), new byte[]{1});
        assertThrows(IOException.class, () -> verified.recheck(game.getCanonicalPath()));
    }
    @Test public void outsideLibraryIsRejected() throws Exception { assertThrows(IOException.class, () -> InstalledNetworkGame.verify(temp.getRoot(), game, selection(HASH))); }
    @Test public void remoteHashCannotStandInForLocalBytes() { assertThrows(IOException.class, () -> InstalledNetworkGame.verify(root, game, selection(String.join("", java.util.Collections.nCopies(64,"a"))))); }
    @Test public void savedDataDoesNotInvalidateContent() throws Exception {
        InstalledNetworkGame verified = verify(); Files.write(new File(game,"save.rms").toPath(), new byte[]{1}); verified.recheck(game.getCanonicalPath());
    }
    @Test public void differentPathCannotConsumeLaunch() throws Exception { InstalledNetworkGame verified = verify(); assertThrows(IOException.class, () -> verified.recheck(root.getPath())); }
}
