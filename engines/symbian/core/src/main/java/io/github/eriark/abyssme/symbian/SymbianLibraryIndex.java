package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AtomicFile;
import com.github.eka2l1.emu.Emulator;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable snapshots for the Android launcher; reading them never loads the native engine. */
public final class SymbianLibraryIndex {
    public static final String SYSTEM_EXTRA = "symbian.system";
    public static final String UID_EXTRA = "symbian.uid";
    public static final String REMOVE_EXTRA = "symbian.remove";
    public static final class Entry {
        public final String system, title, platform, phone;
        public final long uid;
        public final File icon, cover;
        Entry(String system, long uid, String title, String platform, String phone, File icon, File cover) {
            this.system = system; this.uid = uid; this.title = title; this.platform = platform; this.phone = phone; this.icon = icon; this.cover = cover;
        }
        public String key() { return "symbian:" + system + "/" + uid; }
    }
    private final File root;
    public SymbianLibraryIndex(Context context) { this(new File(context.getFilesDir(), "symbian-library")); }
    SymbianLibraryIndex(File root) { this.root = root; }
    public static File artworkRoot(File files, String system) {
        if (system == null || !system.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid phone identity");
        File embedded = new File(files, "symbian-systems/" + system + "/artwork");
        // Restored phones own their overrides, including empty/reset artwork directories.
        return embedded.isDirectory() ? embedded : new File(files, "symbian-library/user-art/" + system);
    }
    private File icon(String system, long uid) { return new File(root, system + "-" + uid + ".png"); }
    private File cover(String system, long uid) { return new File(root, system + "-" + uid + "-cover.png"); }
    private static void identity(String system, long uid) throws IOException {
        if (system == null || !system.matches("[0-9a-f]{64}") || uid <= 0 || uid > 0xffffffffL) throw new IOException("Invalid Symbian library identity");
    }
    public List<Entry> read() throws IOException {
        List<Entry> result = new ArrayList<>();
        File[] files = root.listFiles(f -> f.getName().matches("[0-9a-f]{64}\\.json"));
        if (files == null) return result;
        if (files.length > 256) throw new IOException("Too many indexed Symbian systems");
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            try {
                JSONObject json = readJson(file);
                String system = json.getString("system");
                if (json.getInt("version") != 1 || !file.getName().equals(system + ".json")) throw new IOException("Invalid library snapshot");
                JSONArray apps = json.getJSONArray("apps");
                if (apps.length() > 2048 || result.size() + apps.length() > 10000) throw new IOException("Symbian library is too large");
                Set<Long> seen = new HashSet<>();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.getJSONObject(i); long uid = app.getLong("uid");
                    identity(system, uid);
                    if (!seen.add(uid)) throw new IOException("Duplicate Symbian app identity");
                    result.add(new Entry(system, uid, app.getString("title"), app.getString("platform"),
                            json.getString("phone"), icon(system, uid), cover(system, uid)));
                }
            } catch (JSONException error) { throw new IOException("Cannot read Symbian library", error); }
        }
        return result;
    }
    void publish(SystemFileCatalog.Entry system, String[] apps, boolean artwork) throws IOException {
        if(GameRemoval.pending(root,system.id)) throw new IOException("An uninstall is incomplete on this phone. Retry it from the game's actions or restore a phone backup. The previous library has been kept");
        publishSnapshot(system,apps,artwork);
    }
    void publishRemoval(SystemFileCatalog.Entry system, String[] apps, Set<Long> removed) throws IOException {
        publishSnapshot(system,apps,false);
        for(long uid:removed) {
            identity(system.id,uid);
            for(File image:new File[]{icon(system.id,uid),cover(system.id,uid)})
                if(image.exists() && !image.delete()) android.util.Log.w("AbyssSymbian","Unused game artwork could not be removed");
        }
    }
    private void publishSnapshot(SystemFileCatalog.Entry system, String[] apps, boolean artwork) throws IOException {
        identity(system.id, 1);
        if (apps == null || apps.length % 3 != 0 || apps.length > 6144) throw new IOException("Invalid engine library response");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create Symbian library");
        try {
            JSONArray rows = new JSONArray(); Set<Long> seen = new HashSet<>();
            for (int i = 0; i < apps.length; i += 3) {
                long uid = Long.parseLong(apps[i]); identity(system.id, uid);
                if (!seen.add(uid)) throw new IOException("Duplicate engine app identity");
                if (apps[i + 1] == null || apps[i + 1].trim().isEmpty() || apps[i + 1].length() > 1024
                        || !Arrays.asList("Symbian", "N-Gage", "N-Gage 2.0").contains(apps[i + 2]))
                    throw new IOException("Invalid engine app metadata");
                rows.put(new JSONObject().put("uid", uid).put("title", apps[i + 1]).put("platform", apps[i + 2]));
            }
            // Validate the whole response before replacing any snapshot or icon.
            if (artwork) for (int i = 0; i < apps.length; i += 3) {
                long uid = Long.parseLong(apps[i]);
                try {
                    if ("N-Gage 2.0".equals(apps[i + 2])) {
                        saveNgageArt(icon(system.id, uid), Emulator.getNgageArtwork(uid, false));
                        saveNgageArt(cover(system.id, uid), Emulator.getNgageArtwork(uid, true));
                    } else saveIcon(icon(system.id, uid), Emulator.getAppIcon(uid));
                }
                catch (IOException | RuntimeException error) { android.util.Log.w("AbyssSymbian", "App icon unavailable", error); }
            }
            AtomicFile file = new AtomicFile(new File(root, system.id + ".json"));
            FileOutputStream output = null;
            try {
                byte[] bytes = new JSONObject().put("version", 1).put("system", system.id)
                        .put("phone", system.displayName() + " / " + system.firmware).put("apps", rows).toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 2 * 1024 * 1024) throw new IOException("Library snapshot exceeds 2 MB");
                output = file.startWrite(); output.write(bytes); file.finishWrite(output);
            } catch (Exception error) { if (output != null) file.failWrite(output); throw new IOException("Cannot publish Symbian library", error); }
        } catch (JSONException | NumberFormatException error) { throw new IOException("Invalid engine library metadata", error); }
    }
    static void saveIcon(File target, Bitmap[] images) throws IOException {
        if (images == null) return;
        Bitmap result = null;
        try {
            if (images.length == 0 || images[0] == null) return;
            Bitmap source = images[0]; int w = source.getWidth(), h = source.getHeight();
            if (w <= 0 || h <= 0 || w > 1024 || h > 1024) throw new IOException("Invalid app icon dimensions");
            int[] pixels = new int[w * h]; source.getPixels(pixels, 0, w, 0, 0, w, h);
            if (images.length > 1 && images[1] != null) {
                Bitmap mask = images[1];
                if (mask.getWidth() != w || mask.getHeight() != h) throw new IOException("Mismatched app icon mask");
                int[] alpha = new int[pixels.length]; mask.getPixels(alpha, 0, w, 0, 0, w, h);
                for (int i = 0; i < pixels.length; i++) {
                    int opacity = (pixels[i] >>> 24) * (255 - ((alpha[i] >>> 16) & 255)) / 255;
                    pixels[i] = (pixels[i] & 0x00ffffff) | (opacity << 24);
                }
            }
            result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
            AtomicFile file = new AtomicFile(target); FileOutputStream output = null;
            try {
                output = file.startWrite();
                if (!result.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Cannot encode app icon");
                file.finishWrite(output);
            } catch (IOException error) { if (output != null) file.failWrite(output); throw error; }
        } finally {
            if (result != null) result.recycle();
            for (Bitmap bitmap : images) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }
    private static void saveNgageArt(File target, int[] pixels) throws IOException {
        if (pixels == null) return;
        if (pixels.length < 2 || pixels[0] <= 0 || pixels[1] <= 0 || pixels[0] > 1024 || pixels[1] > 1024
                || pixels.length != 2 + pixels[0] * pixels[1]) throw new IOException("Invalid N-Gage artwork");
        saveIcon(target, new Bitmap[]{Bitmap.createBitmap(pixels, 2, pixels[0], pixels[0], pixels[1], Bitmap.Config.ARGB_8888)});
    }
    private static JSONObject readJson(File file) throws IOException, JSONException {
        try (InputStream input = new AtomicFile(file).openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int total = 0;
            for (int n; (n = input.read(buffer)) != -1;) {
                total += n; if (total > 2 * 1024 * 1024) throw new IOException("Library snapshot exceeds 2 MB");
                output.write(buffer, 0, n);
            }
            return new JSONObject(output.toString("UTF-8"));
        }
    }
}
