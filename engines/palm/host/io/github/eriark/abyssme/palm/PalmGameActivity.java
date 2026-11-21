/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.palm;

import android.graphics.*;
import android.os.Bundle;
import android.net.Uri;
import android.util.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.handheld.*;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class PalmGameActivity extends HandheldActivity {
    private static final String ORIGIN = "https://palm.abyssme.invalid/";
    // libretro B/Y/Select/Start/directions/A/X to Palm hard buttons and five-way navigator.
    private static final int[] NATIVE_KEYS = {2, 4, 0, 9, 5, 6, 7, 8, 1, 3};
    private final Map<String,File> media = new HashMap<>();
    private final List<ZipEntry> entries = new ArrayList<>();
    private final Map<String,String> launches = new LinkedHashMap<>();
    private ZipFile archive;
    private WebView web;
    private NativePalm machine;
    private boolean savingNative;
    private final PalmSpeaker speaker = new PalmSpeaker();
    private String id, romId, kind, title, launchKey = "";
    private File state;
    private FileOutputStream writing;
    private AtomicFile atomic;
    private int width, height;
    private boolean fresh, closed, initialized, exiting;
    private int[] pixels;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (StorageActivity.guard(this)) return;
        try {
            id = getIntent().getStringExtra("installed");
            PalmLibrary lib = new PalmLibrary();
            File dir = lib.directory(this,id);
            Properties meta = lib.info(dir);
            kind = lib.variant(meta);
            title = meta.getProperty("title");
            if (kind.equals("NAND")) {
                PalmLibrary.select(this,"NAND",id);
                Toast.makeText(this,"Palm NAND selected",Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            romId = kind.equals("ROM") ? id : PalmLibrary.selected(this,"ROM");
            if (romId.isEmpty()) throw new IOException("Import a Palm device ROM before opening a game.");
            media.put("rom",new File(lib.directory(this,romId),"device.rom"));
            String nandId = PalmLibrary.selected(this,"NAND");
            if (!nandId.isEmpty()) media.put("nand",new File(lib.directory(this,nandId),"nand.bin"));
            File source = new File(dir,lib.filename(meta));
            if (kind.equals("Bundle")) {
                archive = new ZipFile(source);
                for (ZipEntry e : Collections.list(archive.entries())) {
                    if (e.isDirectory() || !PalmLibrary.database(e.getName())) continue;
                    String key = "db" + entries.size();
                    entries.add(e);
                    try (InputStream in = archive.getInputStream(e)) { addApp(key,PalmLibrary.appName(in)); }
                }
            } else if (!kind.equals("ROM")) {
                media.put("db0",source);
                try (InputStream in = new FileInputStream(source)) { addApp("db0",PalmLibrary.appName(in)); }
            }
            File data = new File(StoragePaths.library(this),"palm/data");
            File saves = new File(data,id);
            Files.createDirectories(saves.toPath());
            state = new File(saves,romId+".session");
            media.put("state",state);
            media.put("base",new File(new File(data,romId),romId+".session"));
            prepareSession(new File(Config.getConfigsDir(),"palm-"+id));
            if (launches.size()>1) {
                String[] keys = launches.keySet().toArray(new String[0]);
                new android.app.AlertDialog.Builder(this).setTitle("Choose a Palm application")
                        .setItems(launches.values().toArray(new String[0]),(d,i)->{launchKey=keys[i];startHost();})
                        .setOnCancelListener(d->finish()).show();
            } else {
                if (!launches.isEmpty()) launchKey=launches.keySet().iterator().next();
                startHost();
            }
        } catch (IOException | RuntimeException e) { fail(e.toString()); }
    }
    private void addApp(String key,String name) { if (!name.isEmpty()) launches.put(key,name); }
    private void startHost() {
        // This activity lives in its own process, alongside other independent WebView users.
        if (launches.size()>1) {
            state=new File(state.getParentFile(),romId+"-"+launchKey+".session");
            media.put("state",state);
        }
        setContentView(new ProgressBar(this));
        File base = media.get("base");
        boolean resume = state.isFile();
        File session = resume ? state : base;
        File runner = new File(getApplicationInfo().nativeLibraryDir, "libabysspalm_runner.so");
        List<String> args = new ArrayList<>(Arrays.asList(runner.getPath(), media.get("rom").getPath(),
                media.containsKey("nand") ? media.get("nand").getPath() : "",
                session.isFile() ? session.getPath() : "", resume ? "resume" : "install",
                launchKey.isEmpty() ? "-1" : launchKey.substring(2)));
        if (media.containsKey("db0")) args.add(media.get("db0").getPath());
        machine = new NativePalm(state, new NativePalm.Listener() {
            public void ready(int w, int h) {
                if (closed || isFinishing()) return;
                try {
                    if (kind.equals("ROM")) PalmLibrary.select(PalmGameActivity.this, "ROM", id);
                    width = w;
                    height = h;
                    initialized = true;
                    Log.i("NativePalm", "Native uARM ready " + w + "x" + h);
                    sessionStarted(title);
                } catch (IOException e) { fail(e.toString()); }
            }
            public void legacy() {
                machine.close();
                machine = null;
                if (!isFinishing()) startWebHost();
            }
            public void failed(String reason) { fail(reason); }
            public void saved(String error) {
                savingNative = false;
                if (error == null) {
                    if (exiting) finish();
                    else Toast.makeText(PalmGameActivity.this, "Palm session saved", Toast.LENGTH_SHORT).show();
                } else if (exiting) new Bridge().saveFailed(error);
                else Toast.makeText(PalmGameActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
        machine.start(args, getCacheDir(), archive, entries);
    }

    private void startWebHost() {
        if (!suffixSet) { WebView.setDataDirectorySuffix("palm"); suffixSet=true; }
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(),"PalmHost");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                return resource(request.getUrl());
            }
        });
        setContentView(web);
        web.loadUrl(ORIGIN+"index.html");
    }
    private static boolean suffixSet;
    private WebResourceResponse resource(Uri uri) {
        try {
            if (!uri.toString().startsWith(ORIGIN)) throw new FileNotFoundException();
            String key = uri.getLastPathSegment();
            InputStream stream;
            String mime = "application/octet-stream";
            if (media.containsKey(key)) stream = new FileInputStream(media.get(key));
            else if (key.matches("db[0-9]+") && archive!=null) stream=archive.getInputStream(entries.get(Integer.parseInt(key.substring(2))));
            else {
                if (!Arrays.asList("index.html","host.js","cloudpilot-emu.js","uarm-worker.js","pcm-worklet.js","cloudpilot_web.wasm","uarm_web.wasm").contains(key))
                    throw new FileNotFoundException();
                stream=getAssets().open("palm/"+key);
                mime=key.endsWith(".js")?"application/javascript":key.endsWith(".wasm")?"application/wasm":"text/html";
            }
            Map<String,String> headers = new HashMap<>();
            headers.put("Cross-Origin-Opener-Policy","same-origin");
            headers.put("Cross-Origin-Embedder-Policy","require-corp");
            headers.put("Cache-Control","no-store");
            return new WebResourceResponse(mime,null,200,"OK",headers,stream);
        } catch (IOException | RuntimeException e) {
            return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,null);
        }
    }
    private class Bridge {
        @JavascriptInterface public String config() throws JSONException {
            JSONObject settings = new JSONObject();
            settings.put("count",archive!=null ? entries.size() : media.containsKey("db0")?1:0);
            settings.put("app",launchKey);
            settings.put("checkpoint",state.isFile()?"state":media.get("base").isFile()?"base":"");
            return settings.toString();
        }
        @JavascriptInterface public void savedAndClosed() {
            runOnUiThread(()->{finish();});
        }
        @JavascriptInterface public void saveFailed(String reason) {
            runOnUiThread(()->{
                exiting=false;
                new android.app.AlertDialog.Builder(PalmGameActivity.this).setMessage(reason)
                        .setNegativeButton("Keep playing",(d,w)->js("setPaused(false)"))
                        .setPositiveButton("Exit without saving",(d,w)->{finish();}).show();
            });
        }
        @JavascriptInterface public void ready(int w,int h) {
            if(w<=0 || h<=0 || w>1024 || h>1024) { error("Invalid Palm screen size");return; }
            runOnUiThread(()->{
                if(closed || isFinishing() || initialized)return;
                try {
                    if(kind.equals("ROM")) PalmLibrary.select(PalmGameActivity.this,"ROM",id);
                    synchronized(PalmGameActivity.this) { width=w; height=h; pixels=new int[w*h]; }
                    initialized=true;
                    ((ViewGroup)web.getParent()).removeView(web);
                    sessionStarted(title);
                    web.setFocusable(false);
                    addContentView(web,new ViewGroup.LayoutParams(1,1));
                    js("paint()");
                } catch(IOException e) { fail(e.toString()); }
            });
        }
        @JavascriptInterface public void picture(String png) {
            synchronized(PalmGameActivity.this) {
                if(pixels==null || closed)return;
                byte[] bytes=android.util.Base64.decode(png,android.util.Base64.DEFAULT);
                Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(b==null)return;
                if(b.getWidth()==width && b.getHeight()==height) { b.getPixels(pixels,0,width,0,0,width,height);fresh=true; }
                b.recycle();
            }
        }
        @JavascriptInterface public void error(String message) { runOnUiThread(()->{if(!closed)fail(message);}); }
        @JavascriptInterface public void tone(double frequency,double duty) { speaker.tone(frequency,duty); }
        @JavascriptInterface public void notice(String message) { runOnUiThread(()->Toast.makeText(PalmGameActivity.this,message,Toast.LENGTH_SHORT).show()); }
        @JavascriptInterface public boolean begin() {
            synchronized(PalmGameActivity.this) {
                if(closed || writing!=null)return false;
                try { atomic=new AtomicFile(state);writing=atomic.startWrite();return true; } catch(IOException e){return false;}
            }
        }
        @JavascriptInterface public boolean chunk(String encoded) {
            synchronized(PalmGameActivity.this) {
                if(writing==null)return false;
                try { writing.write(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT));return true; } catch(IOException e){return false;}
            }
        }
        @JavascriptInterface public void end(boolean complete) {
            synchronized(PalmGameActivity.this) {
                if(writing==null)return;
                if(complete)atomic.finishWrite(writing);else atomic.failWrite(writing);
                writing=null;
            }
        }
    }
    private void js(String code) { if(web!=null && !closed)web.evaluateJavascript(code,null); }
    @Override protected void more(GameMenuDialog menu) {
        if (!kind.equals("ROM")) menu.action("Open installed game", android.R.drawable.ic_media_play, () -> {
            menu.dismiss();
            if (machine != null) machine.command('I', 0, 0);
            else js("installGame().catch(problem)");
        });
        menu.action("Save Palm session",android.R.drawable.ic_menu_save,()->{save(false);menu.dismiss();});
        if(state.isFile())menu.action("Load Palm session",android.R.drawable.ic_menu_revert,()->{menu.dismiss();recreate();});
    }
    @Override protected ControllerInput.Sink keys(){return new LibretroKeys(){
        @Override protected void button(int id,boolean down){
            if (machine == null) { js("key("+id+","+down+")"); return; }
            if (id >= 0 && id < NATIVE_KEYS.length && NATIVE_KEYS[id] != 0)
                machine.command('K', NATIVE_KEYS[id], down ? 1 : 0);
        }
    };}
    @Override protected void configureProfile(ProfileModel p){LibretroKeys.defaultMappings(p);}
    @Override protected boolean usesStylus(){return true;}
    @Override protected void stylus(float x,float y,boolean down){
        if (machine != null) { machine.command('T', down ? (int)x : -1, (int)y); return; }
        js(down ? "emu.emulationService.handlePointerMove("+x+","+y+",false)" : "emu.emulationService.handlePointerUp()");
    }
    @Override protected void setSpeed(int percent) {
        if (machine != null) machine.command('V', percent, 0);
        else js("emu.setSpeed("+percent+"/100)");
    }
    @Override protected void pauseCore(boolean paused) {
        if (machine != null) machine.command('P', paused ? 1 : 0, 0);
        else {
            speaker.pause(paused);
            js("setPaused("+paused+")");
        }
    }
    @Override protected synchronized boolean frame(int[] out) {
        if (machine != null) return machine.frame(out);
        if (!fresh) return false;
        System.arraycopy(pixels, 0, out, 0, out.length);
        fresh = false;
        return true;
    }
    @Override protected int width(){return width;}
    @Override protected int height(){return height;}
    @Override protected boolean exited(){return false;}
    @Override protected boolean canSaveOnExit(){return true;}
    @Override protected void saveAndExit(){
        save(true);
    }
    private void save(boolean exitAfter) {
        if (exiting) return;
        exiting = exitAfter;
        if (machine != null) {
            if (!savingNative) {
                savingNative = true;
                machine.command('S', 0, 0);
            }
        } else if (exitAfter) js("saveSession(true).catch(e=>PalmHost.saveFailed(String(e)))");
        else js("saveSession().catch(problem)");
    }
    @Override protected void stopCore(){closeHost();}
    private synchronized void closeHost(){
        if(closed)return;closed=true;
        if(machine!=null) { machine.close(); machine=null; }
        speaker.close();
        if(writing!=null){atomic.failWrite(writing);writing=null;}
        if(web!=null){web.removeJavascriptInterface("PalmHost");ViewParent parent=web.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(web);web.destroy();}
        if(archive!=null)try {archive.close();} catch(IOException ignored){}
    }
    @Override public void onDestroy(){super.onDestroy();closeHost();}
}
