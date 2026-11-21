/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gamebuino;

import static javax.microedition.lcdui.Canvas.*;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Base64;
import android.webkit.JsPromptResult;
import java.io.*;
import java.nio.file.Files;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

public final class GamebuinoGameActivity extends PackagedWebActivity {
    private static final int[][] SCREEN = {{84,48}};
    private File program, card;
    private RandomAccessFile sectors;
    private boolean ready;
    @Override protected String engine() { return "gamebuino"; }
    @Override protected String platformTitle() { return "Gamebuino Classic"; }
    @Override protected int[][] sizes() { return SCREEN; }
    @Override protected boolean usesStylus() { return false; }
    @Override protected boolean artworkReady() { return ready; }
    @Override protected String openGame(String id) throws IOException {
        program = new GamebuinoLibrary().installed(this, id);
        File dir = new File(StoragePaths.library(this), "gamebuino/data/" + id);
        Files.createDirectories(dir.toPath());
        card = new File(dir, "sd.img");
        AtomicFile stored = new AtomicFile(card);
        try (InputStream existing = stored.openRead()) { /* Recover an interrupted replacement. */ }
        catch (FileNotFoundException fresh) {
            File seed = new File(program.getParentFile(), "initial-sd.img");
            try (InputStream in = seed.isFile() ? new FileInputStream(seed) : getAssets().open("gamebuino/blank.img")) {
                replaceCard(in);
            }
        }
        sectors = new RandomAccessFile(card, "rw");
        return "/index.html";
    }
    @Override protected InputStream openResource(String path) throws IOException {
        if (path.equals("/game.hex")) return new FileInputStream(program);
        if (!path.matches("/(core/)?[A-Za-z0-9.]+") || path.contains("..")) throw new FileNotFoundException();
        return getAssets().open("gamebuino" + path);
    }
    @Override protected boolean gamePrompt(String message, String value, JsPromptResult result) {
        if (message.equals("classic-ready")) { ready = true; result.confirm(""); return true; }
        boolean write = message.equals("classic-write");
        if (!write && !message.equals("classic-read")) return false;
        try {
            int split = write ? value.indexOf(',') : value.length();
            long block = Long.parseLong(value.substring(0, split));
            if (block < 0 || block >= (1L << 23)) throw new IOException("Invalid SD sector");
            sectors.seek(block * 512);
            if (write) {
                byte[] data = Base64.decode(value.substring(split + 1), Base64.DEFAULT);
                if (data.length != 512) throw new IOException("Incomplete SD sector");
                sectors.write(data);
                sectors.getFD().sync();
                result.confirm("ok");
            } else {
                byte[] data = new byte[512];
                int available = (int) Math.min(512, Math.max(0, sectors.length() - sectors.getFilePointer()));
                sectors.readFully(data, 0, available);
                result.confirm(Base64.encodeToString(data, Base64.NO_WRAP));
            }
        } catch (IOException | RuntimeException e) { result.confirm(""); fail("SD card: " + e.getMessage()); }
        return true;
    }
    private void replaceCard(InputStream input) throws IOException {
        AtomicFile target = new AtomicFile(card);
        FileOutputStream out = target.startWrite();
        try {
            byte[] buffer = new byte[65536];
            long size = 0;
            for (int n; (n = input.read(buffer)) != -1;) { out.write(buffer, 0, n); size += n; }
            if (size < 512) throw new IOException("Choose an SD disk image");
            target.finishWrite(out);
        } catch (IOException e) { target.failWrite(out); throw e; }
    }
    @Override protected void more(GameMenuDialog menu) {
        menu.action("Mount SD image", android.R.drawable.ic_menu_upload, () ->
            new AlertDialog.Builder(this).setMessage("Replace this game's SD card and restart the game?")
                .setPositiveButton("Choose image", (d,w) -> startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 126))
                .setNegativeButton(android.R.string.cancel, null).show());
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 126 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot read image");
                replaceCard(in);
                runOnUiThread(this::recreate);
            } catch (IOException e) { runOnUiThread(() -> fail(e.getMessage())); }
        }, "Mount Gamebuino card").start();
    }
    @Override protected void key(int code, boolean down) {
        String button;
        switch (code) {
            case KEY_UP: case '2': button = "Up"; break;
            case KEY_DOWN: case '8': button = "Down"; break;
            case KEY_RIGHT: case '6': button = "Right"; break;
            case KEY_LEFT: case '4': button = "Left"; break;
            case KEY_FIRE: button = "A"; break;
            case KEY_SOFT_RIGHT: case '0': button = "B"; break;
            case KEY_SOFT_LEFT: case '1': button = "C"; break;
            default: return;
        }
        evaluate("window.classicKey?.('" + button + "'," + down + ")");
    }
    @Override protected void closeGame() {
        if (sectors != null) try { sectors.close(); } catch (IOException ignored) {}
    }
}
