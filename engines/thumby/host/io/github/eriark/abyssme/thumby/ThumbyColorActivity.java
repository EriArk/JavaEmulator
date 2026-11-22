/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.thumby;

import android.util.AtomicFile;
import android.webkit.JsPromptResult;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import ru.playsoftware.j2meloader.catalog.ThumbyPackage;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import static javax.microedition.lcdui.Canvas.*;

public final class ThumbyColorActivity extends PackagedWebActivity {
    private ZipFile zip;
    private final List<ZipEntry> files = new ArrayList<>();
    private byte[] manifest;
    private AtomicFile save;
    private FileOutputStream pending;
    private boolean hasFrame, finished;

    @Override protected String engine() { return "thumby_color"; }
    @Override protected String platformTitle() { return "Thumby Color"; }
    @Override protected int[][] sizes() { return new int[][]{{128,128}}; }
    @Override protected boolean usesStylus() { return false; }
    @Override protected void more(GameMenuDialog menu) {}
    @Override protected boolean artworkReady() { return hasFrame; }
    @Override protected boolean exited() { return finished; }

    @Override protected String openGame(String id) throws IOException, JSONException {
        File root = new File(StoragePaths.library(this), engine());
        File card = new File(root, "packages/" + id);
        Properties info = new Properties();
        try (InputStream in = new FileInputStream(new File(card, "card.properties"))) { info.load(in); }
        zip = new ZipFile(new File(card, "game.zip"));
        ThumbyPackage game = ThumbyPackage.read(zip, true, info.getProperty("directory", "main"));
        files.addAll(game.files.values());
        manifest = new JSONObject().put("title", game.title).put("main", game.main)
                .put("files", new JSONArray(game.files.keySet())).toString().getBytes(StandardCharsets.UTF_8);
        save = new AtomicFile(new File(root, "data/" + id + "/filesystem.json"));
        return "/index.html";
    }

    @Override protected InputStream openResource(String path) throws IOException {
        if (path.equals("/game.json")) return new ByteArrayInputStream(manifest);
        if (path.equals("/saved.json")) {
            try { return save.openRead(); }
            catch (FileNotFoundException e) { return new ByteArrayInputStream(new byte[]{'{','}'}); }
        }
        if (path.matches("/file/[0-9]{1,8}")) {
            int index = Integer.parseInt(path.substring(6));
            if (index < files.size()) return zip.getInputStream(files.get(index));
            throw new FileNotFoundException();
        }
        if (!path.matches("/[a-zA-Z0-9_./-]+") || path.contains("..")) throw new FileNotFoundException();
        return getAssets().open("color" + path);
    }

    @Override protected boolean gamePrompt(String message, String value, JsPromptResult result) {
        try {
            switch (message) {
                case "color-frame": hasFrame = true; break;
                case "color-exit": finished = true; break;
                case "color-save-start":
                    if (pending != null) save.failWrite(pending);
                    pending = save.startWrite(); break;
                case "color-save-append":
                    if (pending == null) throw new IOException("Missing save transaction");
                    pending.write(value.getBytes(StandardCharsets.UTF_8)); break;
                case "color-save-end":
                    if (pending == null) throw new IOException("Missing save transaction");
                    save.finishWrite(pending); pending = null; break;
                default: return false;
            }
            result.confirm("ok");
        } catch (IOException e) {
            if (pending != null) save.failWrite(pending);
            pending = null;
            result.confirm("");
        }
        return true;
    }
    @Override protected void key(int code, boolean down) {
        int mask;
        switch (code) {
            case KEY_FIRE: mask = 1; break;
            case KEY_SOFT_RIGHT: mask = 2; break;
            case KEY_UP: mask = 4; break;
            case KEY_DOWN: mask = 8; break;
            case KEY_LEFT: mask = 16; break;
            case KEY_RIGHT: mask = 32; break;
            case '*': mask = 64; break;
            case '#': mask = 128; break;
            case '9': mask = 256; break;
            default: return;
        }
        evaluate("window.colorKey?.(" + mask + "," + down + ")");
    }
    @Override protected void closeGame() {
        if (pending != null) { save.failWrite(pending); pending = null; }
        if (zip != null) try { zip.close(); } catch (IOException ignored) {}
    }
}
