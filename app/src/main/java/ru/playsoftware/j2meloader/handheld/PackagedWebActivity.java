/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.handheld;

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

/** Offline packaged web runtime using the same game screen, input and saves as other handhelds. */
public abstract class PackagedWebActivity extends HandheldActivity {
    protected abstract String engine();
    protected abstract String platformTitle();
    protected abstract String openGame(String id) throws IOException, JSONException;
    protected abstract InputStream openResource(String path) throws IOException;
    protected abstract int[][] sizes();
    protected String extraScript() { return ""; }
    protected String prepareHtml(String html) { return html; }
    protected String browserProfile() { return engine(); }
    protected String originSuffix() { return ".invalid"; }
    protected String extraPolicy() { return ""; }
    protected void closeGame() {}
    protected boolean phoneKeys() { return false; }

    private static final Pattern HEAD = Pattern.compile("<head(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCUMENT_START = Pattern.compile("^\\s*(?:<!doctype[^>]*>)?", Pattern.CASE_INSENSITIVE);
    private static boolean initialized;
    private HandheldWebDisplay display;
    private String origin, failure;
    private AtomicFile storage, screenChoice;
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
            if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid package identity");
            String launch = openGame(id);
            origin = "https://" + id.substring(0,32) + "." + id.substring(32) + originSuffix();
            File data = new File(StoragePaths.library(this), engine() + "/data/" + id);
            Files.createDirectories(data.toPath());
            storage = new AtomicFile(new File(data, "storage.json"));
            screenChoice = new AtomicFile(new File(data, "screen"));
            try (InputStream saved = screenChoice.openRead()) {
                int index = saved.read();
                if (index >= 0 && index < sizes().length) size = index;
            } catch (FileNotFoundException ignored) {}
            prepareSession(new File(Config.getConfigsDir(), engine() + "-" + id));
            if (!initialized) { WebView.setDataDirectorySuffix(browserProfile()); initialized = true; }
            display = new HandheldWebDisplay(this, platformTitle(), width(), height());
            WebView web = display.web;
            WebSettings prefs = web.getSettings();
            prefs.setJavaScriptEnabled(true);
            prefs.setDomStorageEnabled(true);
            prefs.setDatabaseEnabled(true); // Legacy WebViews can provide Web SQL for webOS games.
            prefs.setAllowFileAccess(false);
            prefs.setAllowContentAccess(false);
            prefs.setBlockNetworkLoads(true);
            prefs.setMediaPlaybackRequiresUserGesture(false);
            if (phoneKeys()) {
                ServiceWorkerWebSettings workers = ServiceWorkerController.getInstance().getServiceWorkerWebSettings();
                workers.setAllowFileAccess(false);
                workers.setAllowContentAccess(false);
                workers.setBlockNetworkLoads(true);
                ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                    @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        return localResponse(request);
                    }
                });
                prefs.setGeolocationEnabled(false);
                web.setNetworkAvailable(false);
            }
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest r) {
                    return !sameOrigin(r.getUrl());
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                    return localResponse(r);
                }
                @Override public void onPageFinished(WebView v, String url) { setSpeed(speed); }
            });
            web.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onConsoleMessage(ConsoleMessage message) {
                    if (message.messageLevel() != ConsoleMessage.MessageLevel.ERROR) return false;
                    android.util.Log.e("AbyssWebGame", message.message() + " (" + message.sourceId() + ":" + message.lineNumber() + ")");
                    return true;
                }
                @Override public boolean onJsPrompt(WebView v, String url, String message, String value, JsPromptResult result) {
                    String reply = "";
                    if (sameOrigin(Uri.parse(url))) {
                        if ("abyss-webos-error".equals(message)) failure = value;
                        if ("abyss-webos-restore".equals(message)) {
                            try { reply = new String(storage.readFully(), StandardCharsets.UTF_8); }
                            catch (FileNotFoundException ignored) {}
                            catch (IOException e) { failure = "Cannot read game data"; }
                        }
                        if ("abyss-webos-save".equals(message) && save(value)) reply = "ok";
                    }
                    result.confirm(reply); return true;
                }
            });
            display.show();
            web.loadUrl(origin + launch);
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | JSONException | RuntimeException e) { stopCore(); fail(e.toString()); }
    }
    private WebResourceResponse localResponse(WebResourceRequest request) {
        try {
            if (!sameOrigin(request.getUrl()) || !"GET".equals(request.getMethod())) throw new FileNotFoundException();
            return resource(request.getUrl().getPath(), request.isForMainFrame());
        } catch (IOException | IllegalStateException unavailable) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", Collections.emptyMap(), null);
        }
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
            failure = "Cannot save game data"; return false;
        }
    }
    private WebResourceResponse resource(String path, boolean document) throws IOException {
        if (path == null || !path.startsWith("/")) throw new FileNotFoundException();
        String mime;
        InputStream in;
        if (path.equals("/.abyss/session.js")) {
            in = getAssets().open("webgames/session.js");
            mime = "application/javascript";
        } else {
            in = openResource(path);
            mime = mimeType(path);
            if ("text/html".equals(mime)) {
                String html;
                try (InputStream page = in) {
                    html = new String(ru.playsoftware.j2meloader.util.IOUtils.toByteArray(page), StandardCharsets.UTF_8);
                }
                if (document) {
                    Matcher head = HEAD.matcher(html);
                    Matcher start = DOCUMENT_START.matcher(html);
                    int end = head.find() ? head.end() : start.find() ? start.end() : 0;
                    html = html.substring(0, end) + "<script src=\"/.abyss/session.js\"></script>" + extraScript() + html.substring(end);
                }
                in = new ByteArrayInputStream(prepareHtml(html).getBytes(StandardCharsets.UTF_8));
            }
        }
        Map<String,String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Security-Policy", "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; object-src 'none'; form-action 'self'" + extraPolicy());
        return new WebResourceResponse(mime == null ? "application/octet-stream" : mime, null, 200, "OK", headers, in);
    }
    private static String mimeType(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (ext.equals("js")) return "application/javascript";
        if (ext.equals("html") || ext.equals("htm") || ext.equals("xhtml")) return "text/html";
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }
    @Override protected void more(GameMenuDialog menu) {
        String[] labels = new String[sizes().length];
        for (int i = 0; i < labels.length; i++) labels[i] = sizes()[i][0] + " × " + sizes()[i][1];
        menu.action(platformTitle() + " screen size", android.R.drawable.ic_menu_crop, () ->
                new AlertDialog.Builder(this).setTitle(platformTitle() + " screen size (restarts game)")
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
                                fail("Cannot save game screen size");
                            }
                        }).setNegativeButton(android.R.string.cancel, null).show());
    }
    @Override protected int width() { return sizes()[size][0]; }
    @Override protected int height() { return sizes()[size][1]; }
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
        if (display != null && phoneKeys()) ServiceWorkerController.getInstance().setServiceWorkerClient(null);
        if (display != null) { display.web.resumeTimers(); display.close(); display = null; }
        closeGame();
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
        if (phoneKeys()) {
            String label = null;
            switch (key) {
                case KEY_SOFT_LEFT: label = "SoftLeft"; break;
                case KEY_SOFT_RIGHT: label = "SoftRight"; break;
                case KEY_CLEAR: label = "Backspace"; break;
                case KEY_STAR: label = "*"; break;
                case KEY_POUND: label = "#"; break;
            }
            if (label != null) {
                display.web.evaluateJavascript("window.abyssPhoneKey?.(" + JSONObject.quote(label) + "," + down + ")", null);
                return;
            }
        }
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
