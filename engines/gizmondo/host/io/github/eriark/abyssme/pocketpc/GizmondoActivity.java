/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pocketpc;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import static javax.microedition.lcdui.Canvas.*;

public final class GizmondoActivity extends HandheldActivity {
    private long session;
    private PocketAudio audio;
    private int speed = 100;
    private boolean paused, ended;
    private String failure;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            prepareSession(new File(Config.getConfigsDir(), "gizmondo-" + id));
            File runtime = new GizmondoLibrary().runtime(this, id);
            JSONArray games = new JSONObject(new String(Files.readAllBytes(new File(runtime, "library.json").toPath()),
                    java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("games");
            List<String> ids = new ArrayList<>(), titles = new ArrayList<>();
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.getJSONObject(i);
                ids.add(game.getString("id"));
                titles.add(game.getString("display_name"));
            }
            if (ids.isEmpty()) throw new IOException("No game on this card");
            if (ids.size() == 1) start(runtime, ids.get(0), titles.get(0));
            else new AlertDialog.Builder(this).setTitle("Choose a game on this card")
                    .setItems(titles.toArray(new String[0]), (dialog, i) -> start(runtime, ids.get(i), titles.get(i)))
                    .setOnCancelListener(dialog -> finish()).show();
        } catch (IOException | JSONException e) { fail(e.toString()); }
    }

    private void start(File runtime, String id, String title) {
        try {
            JSONObject result = new JSONObject(GizmondoBridge.start(runtime.getPath(), id));
            if (result.has("error")) throw new IOException(result.getString("error"));
            session = result.getLong("handle");
            audio = new PocketAudio(session, GizmondoBridge::nativeAudioFormat, GizmondoBridge::nativePollAudio);
            sessionStarted(title);
        } catch (IOException | JSONException | LinkageError e) { fail(e.toString()); }
    }

    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Gizmondo buttons", android.R.drawable.ic_menu_info_details, () ->
                new AlertDialog.Builder(this).setTitle("Gizmondo buttons")
                        .setMessage("Fire or 5: confirm\nSoft Left or 1: A\nSoft Right or 3: B\nStar or 7: C"
                                + "\nClear: Escape\nPound: Tab\n2, 4, 6, 8, 0: piano keys 1–5")
                        .setPositiveButton(android.R.string.ok, null).show());
    }

    @Override protected int width() { return 320; }
    @Override protected int height() { return 240; }

    @Override protected boolean frame(int[] pixels) {
        byte[] data = GizmondoBridge.nativePollFrame(session);
        if (data == null || data.length < 8) return false;
        ByteBuffer frame = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int type = frame.getInt();
        if (type == -3 && data.length == 16 + pixels.length * 2) {
            frame.position(16);
            for (int i = 0; i < pixels.length; i++) {
                int rgb = frame.getShort() & 65535;
                int r = rgb >>> 11, g = (rgb >>> 5) & 63, b = rgb & 31;
                pixels[i] = 0xff000000 | ((r << 3 | r >>> 2) << 16) | ((g << 2 | g >>> 4) << 8) | (b << 3 | b >>> 2);
            }
            return true;
        }
        int w = type, h = frame.getInt();
        if (type == -1 && data.length >= 20) {
            frame.getInt();
            w = frame.getInt();
            h = frame.getInt();
        }
        if (w <= 0 || h <= 0 || frame.remaining() != (long) w * h * 4) return false;
        int offset = frame.position();
        for (int y = 0; y < height(); y++) {
            int row = y * h / height() * w;
            for (int x = 0; x < width(); x++) {
                int pos = offset + 4 * (row + x * w / width());
                pixels[y * width() + x] = 0xff000000 | ((data[pos] & 255) << 16)
                        | ((data[pos + 1] & 255) << 8) | (data[pos + 2] & 255);
            }
        }
        return true;
    }

    private void key(int code, boolean down) {
        int vk;
        switch (code) {
            case KEY_LEFT: vk = 0x25; break;
            case KEY_UP: vk = 0x26; break;
            case KEY_RIGHT: vk = 0x27; break;
            case KEY_DOWN: vk = 0x28; break;
            case KEY_FIRE: case '5': vk = 13; break;
            case KEY_SOFT_LEFT: case '1': vk = 0x11; break;
            case KEY_SOFT_RIGHT: case '3': vk = 0x20; break;
            case KEY_STAR: case '7': vk = 0x10; break;
            case KEY_CLEAR: vk = 0x1b; break;
            case KEY_POUND: vk = 0x09; break;
            case '2': vk = 0x70; break;
            case '4': vk = 0x71; break;
            case '6': vk = 0x72; break;
            case '8': vk = 0x73; break;
            case '0': vk = 0x7a; break;
            default: return;
        }
        if (session != 0) GizmondoBridge.nativeSendInput(session, down ? 0 : 1, vk, 0);
    }

    @Override protected ControllerInput.Sink keys() {
        return new ControllerInput.Sink() {
            public void press(int code) { key(code, true); }
            public void release(int code) { key(code, false); }
            public void repeat(int code) {}
        };
    }
    @Override protected void setSpeed(int value) {
        speed = value;
        if (session != 0 && !paused) GizmondoBridge.rate(session, speed);
    }
    @Override protected void pauseCore(boolean value) {
        paused = value;
        if (session != 0) {
            GizmondoBridge.rate(session, paused ? 0 : speed);
            audio.paused = paused;
        }
    }
    @Override protected String error() {
        if (session != 0 && GizmondoBridge.nativeIsRunning(session) == 0) finishSession(false);
        return failure;
    }
    @Override protected boolean exited() { return ended; }
    @Override protected void stopCore() { if (session != 0) finishSession(true); }

    private void finishSession(boolean stop) {
        audio.close();
        if (stop) GizmondoBridge.nativeRequestStop(session);
        String summary = GizmondoBridge.nativeFinishGame(session);
        session = 0;
        ended = true;
        Log.i("AbyssGizmondo", summary);
        try {
            JSONObject result = new JSONObject(summary);
            JSONObject diagnostic = result.optJSONObject("diagnostic");
            if (!stop && diagnostic != null) failure = diagnostic.optString("reason", result.optString("summary"));
        } catch (JSONException e) { if (!stop) failure = summary; }
    }
}
