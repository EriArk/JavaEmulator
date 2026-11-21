/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.webos;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.handheld.HandheldWebDisplay;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import static javax.microedition.lcdui.Canvas.*;

public final class WebOsGameActivity extends HandheldActivity {
    private static final Pattern HEAD = Pattern.compile("<head(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_SCRIPT = Pattern.compile("(<script\\b[^>]*?)\\s*/>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENYO_PATH = Pattern.compile(
            "^/(?:usr/palm/frameworks|opt/PalmSDK/Current/share/framework)/enyo/(?:1\\.0|0\\.10)/framework/(.+)$");
    private static final Pattern DOCUMENT_START = Pattern.compile("^\\s*(?:<!doctype[^>]*>)?", Pattern.CASE_INSENSITIVE);
    private static boolean initialized;
    private HandheldWebDisplay display;
    private File app;
    private String origin, failure;
    private AtomicFile storage, screenChoice;
    private static final int[][] SIZES = {{320, 480}, {480, 800}, {1024, 768}};
    private int size;
    private boolean paused;
    private int speed = 100;
    private long pointerStart;
    private float lastX, lastY;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            app = new WebOsLibrary().installed(this, id);
            origin = "https://" + id.substring(0,32) + "." + id.substring(32) + ".invalid";
            File data = new File(StoragePaths.library(this), "webos/data/" + id);
            Files.createDirectories(data.toPath());
            storage = new AtomicFile(new File(data, "storage.json"));
            screenChoice = new AtomicFile(new File(data, "screen"));
            try (InputStream saved = screenChoice.openRead()) {
                int index = saved.read();
                if (index >= 0 && index < SIZES.length) size = index;
            } catch (FileNotFoundException ignored) {}
            prepareSession(new File(Config.getConfigsDir(), "webos-" + id));
            if (!initialized) { WebView.setDataDirectorySuffix("webos"); initialized = true; }
            display = new HandheldWebDisplay(this, "Palm webOS", width(), height());
            WebView web = display.web;
            WebSettings prefs = web.getSettings();
            prefs.setJavaScriptEnabled(true);
            prefs.setDomStorageEnabled(true);
            prefs.setDatabaseEnabled(true); // Legacy WebViews can provide Web SQL for webOS games.
            prefs.setAllowFileAccess(false);
            prefs.setAllowContentAccess(false);
            prefs.setBlockNetworkLoads(true);
            prefs.setMediaPlaybackRequiresUserGesture(false);
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest r) {
                    return !sameOrigin(r.getUrl());
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                    try {
                        if (!sameOrigin(r.getUrl()) || !"GET".equals(r.getMethod())) throw new FileNotFoundException();
                        return resource(r.getUrl().getPath(), r.isForMainFrame());
                    } catch (IOException e) {
                        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", Collections.emptyMap(), null);
                    }
                }
                @Override public void onPageFinished(WebView v, String url) { setSpeed(speed); }
            });
            web.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onConsoleMessage(ConsoleMessage message) {
                    if (message.messageLevel() != ConsoleMessage.MessageLevel.ERROR) return false;
                    android.util.Log.e("AbyssWebOS", message.message() + " (" + message.sourceId() + ":" + message.lineNumber() + ")");
                    return true;
                }
                @Override public boolean onJsPrompt(WebView v, String url, String message, String value, JsPromptResult result) {
                    String reply = "";
                    if (sameOrigin(Uri.parse(url))) {
                        if ("abyss-webos-error".equals(message)) failure = value;
                        if ("abyss-webos-restore".equals(message)) {
                            try { reply = new String(storage.readFully(), StandardCharsets.UTF_8); }
                            catch (FileNotFoundException ignored) {}
                            catch (IOException e) { failure = "Cannot read webOS game data"; }
                        }
                        if ("abyss-webos-save".equals(message) && save(value)) reply = "ok";
                    }
                    result.confirm(reply); return true;
                }
            });
            display.show();
            String main = Ipk.manifest(app).optString("main", "index.html");
            web.loadUrl(origin + "/" + Uri.encode(main, "/"));
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | JSONException | RuntimeException e) { stopCore(); fail(e.toString()); }
    }
    private boolean sameOrigin(Uri uri) { return origin.equals(uri.getScheme() + "://" + uri.getEncodedAuthority()); }
    private boolean save(String json) {
        FileOutputStream out = null;
        try {
            new JSONObject(json);
            out = storage.startWrite();
            out.write(json.getBytes(StandardCharsets.UTF_8)); storage.finishWrite(out); return true;
        } catch (IOException | JSONException e) {
            if (out != null) storage.failWrite(out);
            failure = "Cannot save webOS game data"; return false;
        }
    }
    private WebResourceResponse resource(String path, boolean document) throws IOException {
        if (path == null || !path.startsWith("/")) throw new FileNotFoundException();
        String mime;
        InputStream in;
        Matcher framework = ENYO_PATH.matcher(path);
        if (framework.matches()) {
            String asset = framework.group(1);
            if (Arrays.asList(asset.split("/")).contains("..") || asset.contains("\\")) throw new FileNotFoundException();
            in = getAssets().open("webos/enyo/" + asset);
            mime = mimeType(asset);
        } else if (path.equals("/usr/palm/frameworks/mojo/mojo.js") || path.equals("/.abyss/session.js")) {
            in = getAssets().open(path.endsWith("/mojo.js") ? "webos/mojo.js" : "webos/session.js");
            mime = "application/javascript";
        } else {
            File file = Ipk.resource(app, path.substring(1));
            if (!file.isFile()) throw new FileNotFoundException();
            mime = mimeType(file.getName());
            if ("text/html".equals(mime)) {
                String html = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (document) {
                    Matcher head = HEAD.matcher(html);
                    Matcher start = DOCUMENT_START.matcher(html);
                    int end = head.find() ? head.end() : start.find() ? start.end() : 0;
                    html = html.substring(0, end) + "<script src=\"/.abyss/session.js\"></script>" + html.substring(end);
                }
                html = EMPTY_SCRIPT.matcher(html).replaceAll("$1></script>");
                mime = "text/html";
                in = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
            } else in = new FileInputStream(file);
        }
        Map<String,String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Security-Policy", "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; object-src 'none'; form-action 'self'");
        return new WebResourceResponse(mime == null ? "application/octet-stream" : mime, null, 200, "OK", headers, in);
    }
    private static String mimeType(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (ext.equals("js")) return "application/javascript";
        if (ext.equals("html") || ext.equals("htm") || ext.equals("xhtml")) return "text/html";
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }
    @Override protected void more(GameMenuDialog menu) {
        String[] labels = new String[SIZES.length];
        for (int i = 0; i < labels.length; i++) labels[i] = SIZES[i][0] + " × " + SIZES[i][1];
        menu.action("webOS screen size", android.R.drawable.ic_menu_crop, () ->
                new AlertDialog.Builder(this).setTitle("webOS screen size (restarts game)")
                        .setSingleChoiceItems(labels, size, (dialog, next) -> {
                            dialog.dismiss();
                            if (next == size) return;
                            FileOutputStream out = null;
                            try {
                                out = screenChoice.startWrite();
                                out.write(next);
                                screenChoice.finishWrite(out);
                                menu.dismiss();
                                recreate();
                            } catch (IOException e) {
                                if (out != null) screenChoice.failWrite(out);
                                fail("Cannot save webOS screen size");
                            }
                        }).setNegativeButton(android.R.string.cancel, null).show());
    }
    @Override protected int width() { return SIZES[size][0]; }
    @Override protected int height() { return SIZES[size][1]; }
    @Override protected String error() { return failure; }
    @Override protected boolean frame(int[] pixels) { return display != null && display.frame(pixels); }
    @Override protected boolean exited() { return false; }
    @Override protected void setSpeed(int percent) {
        speed = percent;
        if (display != null) display.web.evaluateJavascript("window.abyssWebSpeed?.(" + percent + ")", null);
    }
    @Override protected void pauseCore(boolean value) {
        if (display == null || paused == value) return;
        paused = value;
        WebView web = display.web;
        if (!value) { web.resumeTimers(); web.onResume(); }
        web.evaluateJavascript("window.abyssWebActive?.(" + !value + ")", null);
        if (value) { web.onPause(); web.pauseTimers(); }
    }
    @Override protected void stopCore() {
        if (display != null) { display.web.resumeTimers(); display.close(); display = null; }
    }
    @Override protected boolean usesStylus() { return true; }
    @Override protected void stylus(float x, float y, boolean down) {
        if (display == null) return;
        long now = SystemClock.uptimeMillis();
        int action = down ? (pointerStart == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_MOVE) : MotionEvent.ACTION_UP;
        if (action == MotionEvent.ACTION_DOWN) pointerStart = now;
        if (down) { lastX = x; lastY = y; }
        MotionEvent event = MotionEvent.obtain(pointerStart, now, action, lastX, lastY, 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        display.web.dispatchTouchEvent(event); event.recycle();
        if (!down) pointerStart = 0;
    }
    private void key(int key, boolean down) {
        if (display == null) return;
        int code;
        switch (key) {
            case KEY_UP: code = KeyEvent.KEYCODE_DPAD_UP; break;
            case KEY_DOWN: code = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case KEY_LEFT: code = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case KEY_RIGHT: code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case KEY_FIRE: code = KeyEvent.KEYCODE_ENTER; break;
            case KEY_SOFT_LEFT: code = KeyEvent.KEYCODE_SPACE; break;
            case KEY_CLEAR: case KEY_SOFT_RIGHT: code = KeyEvent.KEYCODE_ESCAPE; break;
            default:
                if (key < '0' || key > '9') return;
                code = KeyEvent.KEYCODE_0 + key - '0';
        }
        display.web.dispatchKeyEvent(new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code));
    }
    @Override protected ControllerInput.Sink keys() {
        return new ControllerInput.Sink() {
            public void press(int code) { key(code,true); }
            public void release(int code) { key(code,false); }
            public void repeat(int code) { key(code,true); }
        };
    }
}
