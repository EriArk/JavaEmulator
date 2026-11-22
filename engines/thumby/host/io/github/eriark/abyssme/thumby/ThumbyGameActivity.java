/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.thumby;

import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.nio.file.Files;
import android.util.AtomicFile;
import android.util.Base64;
import android.webkit.JsPromptResult;
import java.util.zip.*;
import org.json.*;
import ru.playsoftware.j2meloader.catalog.ThumbyPackage;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import static javax.microedition.lcdui.Canvas.*;

public final class ThumbyGameActivity extends PackagedWebActivity {
    private ZipFile zip;
    private final List<ZipEntry> files = new ArrayList<>();
    private byte[] manifest;
    private File disk;
    private volatile boolean hasFrame, finished;
    @Override protected boolean exited() { return finished; }
    private static boolean validSector(String name) {
        return name.matches("[0-9]{1,3}") && Integer.parseInt(name) < 352;
    }
    @Override protected boolean artworkReady() { return hasFrame; }
    @Override protected boolean gamePrompt(String message, String value, JsPromptResult result) {
        switch (message) {
            case "thumby-frame": hasFrame = true; break;
            case "thumby-exit": finished = true; break;
            case "thumby-write":
                int split = value.indexOf(':');
                if (split < 1 || !validSector(value.substring(0, split))) { result.confirm(""); return true; }
                AtomicFile file = new AtomicFile(new File(disk, value.substring(0, split)));
                FileOutputStream out = null;
                try {
                    byte[] sector = Base64.decode(value.substring(split + 1), Base64.DEFAULT);
                    if (sector.length != 4096) throw new IOException("Invalid sector length");
                    out = file.startWrite();
                    out.write(sector);
                    file.finishWrite(out);
                    result.confirm("ok");
                } catch (IOException | IllegalArgumentException invalid) {
                    if (out != null) file.failWrite(out);
                    result.confirm("");
                }
                return true;
            default: return false;
        }
        result.confirm("");
        return true;
    }
    @Override protected String engine() { return "thumby"; }
    @Override protected String platformTitle() { return "Thumby"; }
    @Override protected int[][] sizes() { return new int[][]{{72,40}}; }
    @Override protected boolean usesStylus() { return false; }
    @Override protected void more(GameMenuDialog menu) {}
    @Override protected String openGame(String id) throws IOException, JSONException {
        File system = ThumbyFirmware.directory(this);
        for (String name : new String[]{ThumbyFirmware.BOOT, ThumbyFirmware.FLASH}) {
            if (!new File(system, name).isFile())
                throw new IOException("Open the Thumby library menu and choose Install system files. Missing " + name);
        }
        zip = new ZipFile(new File(StoragePaths.library(this), "thumby/packages/" + id + "/game.zip"));
        ThumbyPackage game = ThumbyPackage.read(zip);
        files.addAll(game.files.values());
        disk = new File(StoragePaths.library(this), "thumby/data/" + id + "/flash-v1");
        Files.createDirectories(disk.toPath());
        String[] sectors = disk.list((dir, name) -> validSector(name));
        if (sectors == null) throw new IOException("Cannot read saved Thumby flash");
        manifest = new JSONObject().put("title", game.title).put("main", game.main)
                .put("sectors", new JSONArray(Arrays.asList(sectors))).put("files", new JSONArray(game.files.keySet())).toString().getBytes(StandardCharsets.UTF_8);
        return "/index.html";
    }
    @Override protected InputStream openResource(String path) throws IOException {
        if (path.equals("/" + ThumbyFirmware.BOOT) || path.equals("/" + ThumbyFirmware.FLASH))
            return new FileInputStream(new File(ThumbyFirmware.directory(this), path.substring(1)));
        if (path.equals("/game.json")) return new ByteArrayInputStream(manifest);
        if (path.startsWith("/disk/") && validSector(path.substring(6)))
            return new AtomicFile(new File(disk, path.substring(6))).openRead();
        if (path.matches("/file/[0-9]{1,6}")) {
            int index = Integer.parseInt(path.substring(6));
            if (index >= files.size()) throw new FileNotFoundException();
            return zip.getInputStream(files.get(index));
        }
        if (!path.matches("/[a-zA-Z0-9_./-]+") || path.contains("..")) throw new FileNotFoundException();
        return getAssets().open("thumby" + path);
    }
    @Override protected void key(int code, boolean down) {
        int pin;
        switch (code) {
            case KEY_UP: pin = 4; break;
            case KEY_DOWN: pin = 6; break;
            case KEY_LEFT: pin = 3; break;
            case KEY_RIGHT: pin = 5; break;
            case KEY_FIRE: pin = 27; break;
            case KEY_SOFT_RIGHT: pin = 24; break;
            default: return;
        }
        evaluate("window.thumbyKey?.(" + pin + "," + down + ")");
    }
    @Override protected void closeGame() {
        if (zip != null) try { zip.close(); } catch (IOException ignored) {}
    }
}
