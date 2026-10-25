/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.util.*;

/** Local evidence, not a title/database/remote hash claim. Save data is never hashed. */
public final class InstalledNetworkGame {
    private static final String[] FILES = {"res.jar", "converted.dex", "converted.dex.conf"};
    public final File directory;
    public final LobbySession.Game game;
    private final List<byte[]> digests;

    public static InstalledNetworkGame verify(File library, File directory, LobbySession.Game game) throws IOException {
        if (!"java".equals(game.engine) || !"bluetooth".equals(game.mode)
                || !"1".equals(game.adapterVersion) || game.players != 2) throw new IOException("Unsupported game adapter");
        File root = library.getCanonicalFile(), local = directory.getCanonicalFile();
        if (!root.equals(local.getParentFile()) || !local.isDirectory()) throw new IOException("Game is outside the library");
        List<byte[]> digests = hashFiles(local);
        if (!hex(digests.get(0)).equals(game.contentHash)) throw new IOException("Installed game differs from the room");
        return new InstalledNetworkGame(local, game, digests);
    }
    private InstalledNetworkGame(File directory, LobbySession.Game game, List<byte[]> digests) {
        this.directory = directory; this.game = game; this.digests = digests;
    }
    public void recheck(String path) throws IOException {
        if (!directory.getPath().equals(path) || !directory.equals(directory.getCanonicalFile())) throw new IOException("Wrong launch path");
        List<byte[]> current = hashFiles(directory);
        for (int i = 0; i < FILES.length; i++) if (!MessageDigest.isEqual(digests.get(i), current.get(i)))
            throw new IOException("Installed game changed; prepare it again");
    }
    private static List<byte[]> hashFiles(File directory) throws IOException {
        List<byte[]> hashes = new ArrayList<>();
        for (String name : FILES) {
            File file = new File(directory, name);
            if (Files.isSymbolicLink(file.toPath()) || !file.isFile() || file.length() < 1 || file.length() > 128L * 1024 * 1024)
                throw new IOException("Invalid installed game file");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = 0;
                try (InputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if ((size += count) > 128L * 1024 * 1024) throw new IOException("Game file grew during verification");
                        digest.update(buffer, 0, count);
                    }
                }
                hashes.add(digest.digest());
            } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        }
        return hashes;
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
}
