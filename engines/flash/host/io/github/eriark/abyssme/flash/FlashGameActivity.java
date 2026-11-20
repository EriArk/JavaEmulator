/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.flash;

import android.app.Presentation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.view.*;
import android.webkit.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import static javax.microedition.lcdui.Canvas.*;

public final class FlashGameActivity extends HandheldActivity {
    private FlashLibrary.Installed game;
    private String origin, failure;
    private int width, height;
    private boolean done, stopped, paused;
    private int speed=100;
    private VirtualDisplay display;
    private Presentation presentation;
    private ImageReader reader;
    private WebView web;
    private AtomicFile saves;
    private static boolean webInitialized;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id=getIntent().getStringExtra("installed");
            game=new FlashLibrary().installed(this,id);
            width=Integer.parseInt(game.info.getProperty("width"));
            height=Integer.parseInt(game.info.getProperty("height"));
            origin="https://"+id.substring(0,32)+"."+id.substring(32)+".invalid";
            File data=new File(StoragePaths.library(this),"flash/data/"+id);
            Files.createDirectories(data.toPath());
            saves=new AtomicFile(new File(data,"shared-objects.json"));
            prepareSession(new File(Config.getConfigsDir(),"flash-"+id));
            startDisplay();
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | RuntimeException e) { stopCore(); fail(e.toString()); }
    }
    private void startDisplay() throws IOException {
        if (!webInitialized) { WebView.setDataDirectorySuffix("flash"); webInitialized=true; }
        reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
        DisplayManager manager=(DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        display=manager.createVirtualDisplay("Flash movie",width,height,160,reader.getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        if (display==null) throw new IOException("Cannot create Flash display");
        presentation=new Presentation(this,display.getDisplay());
        Window window=presentation.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(5894);
        web=new WebView(presentation.getContext());
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r) {
                Uri uri=r.getUrl();
                try {
                    if (!"GET".equals(r.getMethod()) || !origin.equals(uri.getScheme()+"://"+uri.getEncodedAuthority()))
                        throw new FileNotFoundException();
                    return resource(uri.getPath());
                } catch (IOException | JSONException e) {
                    return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.emptyMap(),null);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsPrompt(WebView v,String url,String message,String value,JsPromptResult result) {
                if ((origin+"/player").equals(url)) {
                    if ("flash-ready".equals(message)) web.post(()->{ setSpeed(speed); pauseCore(paused); });
                    if ("flash-save".equals(message)) store(value);
                    if ("flash-error".equals(message)) failure=value;
                    if ("flash-exit".equals(message)) done=true;
                }
                result.confirm(""); return true;
            }
        });
        presentation.setContentView(web); presentation.show();
        web.loadUrl(origin+"/player");
    }
    private WebResourceResponse resource(String path) throws IOException, JSONException {
        InputStream data; String mime;
        if ("/player".equals(path)) {
            data=getAssets().open("flash/player.html"); mime="text/html";
        } else if ("/session.json".equals(path)) {
            JSONObject config=new JSONObject(), storage=new JSONObject();
            if (saves.getBaseFile().isFile()) storage=new JSONObject(new String(saves.readFully(),StandardCharsets.UTF_8));
            config.put("saves",storage);
            config.put("url","/game/"+Uri.encode(game.info.getProperty("entry"),"/"));
            data=new ByteArrayInputStream(config.toString().getBytes(StandardCharsets.UTF_8)); mime="application/json";
        } else if (path!=null && path.startsWith("/engine/") && path.substring(8).matches("[A-Za-z0-9_.-]+")) {
            String name=path.substring(8);
            if (!name.equals("ruffle.js") && !name.endsWith(".wasm")) throw new FileNotFoundException();
            data=getAssets().open("flash/"+name); mime=name.endsWith(".wasm") ? "application/wasm" : "text/javascript";
        } else if (path!=null && path.startsWith("/game/")) {
            data=game.open(path.substring(6)); mime="application/octet-stream";
        } else throw new FileNotFoundException();
        return new WebResourceResponse(mime,null,200,"OK",Collections.singletonMap("Cache-Control","no-store"),data);
    }
    private void store(String json) {
        FileOutputStream out=null;
        try {
            new JSONObject(json);
            out=saves.startWrite(); out.write(json.getBytes(StandardCharsets.UTF_8)); saves.finishWrite(out);
        } catch (IOException | JSONException e) {
            if (out!=null) saves.failWrite(out);
            failure="Cannot save Flash shared objects";
        }
    }
    private void evaluate(String js) { if (web!=null && !stopped) web.evaluateJavascript(js,null); }
    @Override protected void setSpeed(int percent) { speed=percent; evaluate("if(typeof speed!=='undefined')speed="+percent+"/100"); }
    @Override protected void pauseCore(boolean paused) { this.paused=paused; evaluate("window.running && running("+!paused+")"); }
    @Override protected void stopCore() {
        stopped=true;
        if (presentation!=null) { presentation.dismiss(); presentation=null; }
        if (web!=null) { web.destroy(); web=null; }
        if (display!=null) { display.release(); display=null; }
        if (reader!=null) { reader.close(); reader=null; }
    }
    @Override protected boolean frame(int[] pixels) {
        if (reader==null) return false;
        try (Image img=reader.acquireLatestImage()) {
            if (img==null) return false;
            Image.Plane plane=img.getPlanes()[0];
            IntBuffer src=plane.getBuffer().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            for (int y=0;y<height;y++) {
                src.position(y*plane.getRowStride()/4);
                src.get(pixels,y*width,width);
            }
            for (int i=0;i<pixels.length;i++) {
                int c=pixels[i]; pixels[i]=0xff000000 | ((c&255)<<16) | (c&0xff00) | ((c>>16)&255);
            }
            return true;
        }
    }
    @Override protected int width() { return width; }
    @Override protected int height() { return height; }
    @Override protected String error() { return failure; }
    @Override protected boolean exited() { return done; }
    @Override protected void gameTap(float x,float y) {
        if (web==null) return;
        long now=SystemClock.uptimeMillis();
        for (int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);
            web.dispatchTouchEvent(e); e.recycle();
        }
    }
    private void key(int key,boolean down) {
        String code, text;
        switch (key) {
            case KEY_UP: code=text="ArrowUp"; break;
            case KEY_DOWN: code=text="ArrowDown"; break;
            case KEY_SOFT_LEFT: code=text="PageUp"; break;
            case KEY_SOFT_RIGHT: code=text="PageDown"; break;
            case KEY_LEFT: code=text="ArrowLeft"; break;
            case KEY_RIGHT: code=text="ArrowRight"; break;
            case KEY_FIRE: code=text="Enter"; break;
            case KEY_CLEAR: code=text="Backspace"; break;
            case '*': code="NumpadMultiply"; text="*"; break;
            case '#': code="Digit3"; text="#"; break;
            default:
                if (key<'0'||key>'9') return;
                text=Character.toString((char)key); code="Digit"+text;
        }
        evaluate("window.button && button("+JSONObject.quote(code)+","+JSONObject.quote(text)+","+down+")");
    }
    @Override protected ControllerInput.Sink keys() {
        return new ControllerInput.Sink() {
            public void press(int key) { key(key,true); }
            public void repeat(int key) { key(key,true); }
            public void release(int key) { key(key,false); }
        };
    }
}
