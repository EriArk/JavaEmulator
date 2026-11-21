/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.arduboy;

import android.content.Context;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class ArduboyLibrary extends CartridgeLibrary {
    public ArduboyLibrary() { super("arduboy", ArduboyGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".hex") || lower.endsWith(".arduboy");
    }

    @Override protected String inspect(Context context, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                if (zip.getEntry("info.json") != null) {
                    // A renamed FX package must keep its flash data and optional save seed together.
                    inspectBundle(zip, info);
                    return SourceIdentity.sha256(file);
                }
            }
        }
        return super.inspect(context, file, name, info);
    }

    @Override protected void inspectImage(File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".arduboy")) {
            try (ZipFile zip = new ZipFile(file)) { inspectBundle(zip, info); }
        } else {
            if (!cartridge(name)) throw new IOException("Choose a .hex or .arduboy game");
            try (InputStream in = new FileInputStream(file)) { checkHex(in); }
        }
    }

    private static void inspectBundle(ZipFile zip, Properties info) throws IOException {
        ZipEntry manifest = zip.getEntry("info.json");
        if (manifest == null || manifest.getSize() > 1024 * 1024) throw new IOException("Invalid Arduboy info.json");
        try (Reader reader = new InputStreamReader(zip.getInputStream(manifest), StandardCharsets.UTF_8)) {
            JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject binary = metadata.getAsJsonArray("binaries").get(0).getAsJsonObject();
            String program = binary.get("filename").getAsString();
            try (InputStream in = zip.getInputStream(required(zip, program))) { checkHex(in); }
            for (String key : new String[]{"flashdata", "flashsave"}) {
                if (binary.has(key) && !binary.get(key).isJsonNull()) {
                    required(zip, binary.get(key).getAsString());
                    info.setProperty("fx", "true");
                }
            }
            info.setProperty("bundle", "true");
            if (metadata.has("title") && metadata.get("title").isJsonPrimitive()) {
                String title = metadata.get("title").getAsString().trim();
                if (!title.isEmpty()) info.setProperty("title", title);
            }
            JsonElement images = metadata.get("screenshots");
            if (images != null && images.isJsonArray()) {
                for (JsonElement image : images.getAsJsonArray()) {
                    if (!image.isJsonObject()) continue;
                    JsonElement path = image.getAsJsonObject().get("filename");
                    if (path != null && path.isJsonPrimitive() && zip.getEntry(path.getAsString()) != null) {
                        info.setProperty("screenshot", path.getAsString());
                        break;
                    }
                }
            }
        } catch (RuntimeException malformed) { throw new IOException("Invalid Arduboy package manifest", malformed); }
    }

    private static ZipEntry required(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) throw new IOException("Missing Arduboy package file: " + name);
        return entry;
    }

    private static void checkHex(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
        boolean data = false;
        for (String line; (line = reader.readLine()) != null;) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith(":") || line.length() < 11 || line.length() > 521 || line.length() % 2 == 0)
                throw new IOException("Invalid Intel HEX record");
            int sum = 0, length = 0, type = 0;
            for (int i = 1; i < line.length(); i += 2) {
                int hi = Character.digit(line.charAt(i), 16), lo = Character.digit(line.charAt(i + 1), 16);
                if (hi < 0 || lo < 0) throw new IOException("Invalid Intel HEX digit");
                int value = hi * 16 + lo;
                if (i == 1) length = value;
                if (i == 7) type = value;
                sum += value;
            }
            if (line.length() != length * 2 + 11 || (sum & 255) != 0) throw new IOException("Damaged Intel HEX record");
            if (type == 0 && length > 0) data = true;
            if (type == 1) break;
        }
        if (!data) throw new IOException("Intel HEX contains no program");
    }

    @Override protected void extractInstalledArt(File game, File directory) throws IOException {
        if (game.getName().endsWith(".arduboy"))
            GameArtwork.extractArchive(game, directory, load(directory).getProperty("screenshot"), false);
    }
    @Override protected String filename(Properties info) { return info.containsKey("bundle") ? "game.arduboy" : "game.hex"; }
    @Override protected String variant(Properties info) { return info.containsKey("fx") ? "Arduboy FX" : "Arduboy"; }
    @Override protected int firstCardId() { return -1140000000; }
    @Override protected int platformId(Properties info) { return 25; }
    @Override protected String platformName(Properties info) { return "Arduboy / FX"; }
}
