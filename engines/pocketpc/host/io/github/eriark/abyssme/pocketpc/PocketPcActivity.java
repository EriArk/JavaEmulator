/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pocketpc;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.AtomicFile;
import com.pockethle.app.NativeBridge;
import ru.playsoftware.j2meloader.input.PlatformControls;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import static javax.microedition.lcdui.Canvas.*;

public final class PocketPcActivity extends HandheldActivity {
    private static final String[] SCREENS = {"portrait","landscape","small_portrait","hpc","wvga","vga","square"};
    private static final int[][] SIZES = {{240,320},{320,240},{176,220},{480,320},{480,800},{480,640},{240,240}};
    private File runtime;
    private String guest;
    private AtomicFile metadata;
    private JSONObject catalog, entry;
    private List<String> programs;
    private long session;
    private int screen, speed = 100, penX, penY;
    private boolean penDown, paused, drewFrame;
    private String failure;
    private PocketAudio audio;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            prepareSession(new File(Config.getConfigsDir(),"pocketpc-"+id));
            PocketPcLibrary library = new PocketPcLibrary();
            runtime = library.runtime(this,id);
            guest = library.guest(this,id);
            File dir = new File(runtime, "games/"+guest);
            metadata = new AtomicFile(new File(runtime, "library.json"));
            catalog = new JSONObject(new String(metadata.readFully(), StandardCharsets.UTF_8));
            JSONArray games = catalog.getJSONArray("games");
            for (int i=0;i<games.length();i++) {
                JSONObject candidate = games.getJSONObject(i);
                if (guest.equals(candidate.optString("id"))) { entry=candidate; break; }
            }
            if (entry==null) throw new IOException("Game missing from Pocket PC library");
            programs = PocketPcLibrary.programs(dir);
            if (programs.isEmpty()) throw new IOException("No native Windows CE ARM program found");
            screen = Math.max(0, Arrays.asList(SCREENS).indexOf(entry.getJSONObject("settings").optString("screen")));
            if (programs.size()>1 && !entry.optBoolean("abyssProgramChosen")) chooseProgram(true);
            else start(programs.contains(entry.optString("executable")) ? entry.optString("executable") : programs.get(0));
        } catch (IOException | JSONException | LinkageError e) { fail(e.toString()); }
    }
    private void chooseProgram(boolean initial) {
        String[] labels = programs.stream().map(s -> s.substring("extracted/".length())).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Choose Pocket PC program").setItems(labels,(d,i)->{
            if (initial) start(programs.get(i));
            else {
                try { entry.put("executable",programs.get(i)); writeEntry(); recreate(); }
                catch (IOException | JSONException e) { fail(e.toString()); }
            }
        }).setOnCancelListener(d->{ if (initial) finish(); }).show();
    }
    private void writeEntry() throws IOException {
        FileOutputStream out = metadata.startWrite();
        try { out.write(catalog.toString().getBytes(StandardCharsets.UTF_8)); metadata.finishWrite(out); }
        catch (IOException e) { metadata.failWrite(out); throw e; }
    }
    private void start(String executable) {
        try {
            entry.put("executable", executable).put("abyssProgramChosen", true);
            if(entry.isNull("install_dir")) entry.put("install_dir", "\\Application\\");
            writeEntry();
            session = NativeBridge.nativeStartGame(runtime.getPath(), guest);
            if (session == 0) throw new IOException("Could not start PocketHLE");
            audio = new PocketAudio(session);
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | JSONException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(GameMenuDialog menu) {
        if (programs.size()>1) menu.action("Choose Pocket PC program",android.R.drawable.ic_media_play,()->chooseProgram(false));
        menu.action("Guest screen size", android.R.drawable.ic_menu_crop,()->{
            String[] labels=new String[SIZES.length];
            for (int i=0;i<labels.length;i++) labels[i]=SIZES[i][0]+" × "+SIZES[i][1];
            new AlertDialog.Builder(this).setTitle("Guest screen size (restarts game)")
                .setSingleChoiceItems(labels,screen,(d,i)->{
                    d.dismiss();
                    if (i==screen) return;
                    try { entry.getJSONObject("settings").put("screen", SCREENS[i]); writeEntry(); recreate(); }
                    catch (IOException | JSONException e) { fail(e.toString()); }
                }).show();
        });
    }
    @Override protected int width() { return SIZES[screen][0]; }
    @Override protected int height() { return SIZES[screen][1]; }
    @Override protected boolean frame(int[] pixels) {
        byte[] data = NativeBridge.nativePollFrame(session);
        if (data==null || data.length!=8+pixels.length*4) return false;
        int width=(data[0]&255)|((data[1]&255)<<8);
        if (width!=width()) return false;
        for(int i=0,j=8;i<pixels.length;i++,j+=4)
            pixels[i]=0xff000000 | ((data[j]&255)<<16) | ((data[j+1]&255)<<8) | (data[j+2]&255);
        drewFrame=true;
        return true;
    }
    private void send(int kind,int a,int b) { if (session!=0) NativeBridge.nativeSendInput(session,kind,a,b); }
    @Override protected boolean usesStylus() { return true; }
    @Override protected void stylus(float x,float y,boolean down) {
        if (down) { penX=(int)x; penY=(int)y; }
        send(down ? (penDown ? 4 : 2) : 3,penX,penY);
        penDown=down;
    }
    private void key(int code,boolean down) {
        int vk;
        switch(code) {
            case KEY_LEFT: vk=0x25; break;
            case KEY_UP: vk=0x26; break;
            case KEY_RIGHT: vk=0x27; break;
            case KEY_DOWN: vk=0x28; break;
            case PlatformControls.APP1: vk=0xc1; break;
            case PlatformControls.APP2: vk=0xc2; break;
            case PlatformControls.APP3: vk=0xc3; break;
            case PlatformControls.APP4: vk=0xc4; break;
            case KEY_FIRE: vk=13; break;
            case KEY_SOFT_LEFT: vk=32; break;
            case KEY_SOFT_RIGHT: vk=27; break;
            case KEY_CLEAR: vk=8; break;
            case KEY_STAR: vk=9; break;
            case KEY_POUND: vk=17; break;
            default: if (code<'0' || code>'9') return; vk=code;
        }
        send(down ? 0 : 1,vk,0);
    }
    @Override protected ControllerInput.Sink keys() { return new ControllerInput.Sink() {
        public void press(int code) { key(code,true); }
        public void release(int code) { key(code,false); }
        public void repeat(int code) {}
    }; }
    @Override protected void setSpeed(int rate) {
        speed=rate;
        if (session!=0 && !paused) NativeBridge.nativeRate(session,speed);
    }
    @Override protected void pauseCore(boolean value) {
        paused=value;
        if (session==0) return;
        if (paused && penDown) stylus(penX,penY,false);
        NativeBridge.nativeRate(session,paused ? 0 : speed);
        audio.paused=paused;
    }
    @Override protected String error() {
        if (session!=0 && NativeBridge.nativeIsRunning(session)==0 && !drewFrame) {
            String summary=finishCore(false);
            if (programs.size()>1) {
                entry.remove("abyssProgramChosen");
                try { writeEntry(); }
                catch (IOException e) { android.util.Log.w("PocketHLE","Cannot reset executable selection",e); }
            }
            failure="Pocket PC program stopped before drawing a frame.\n"+summary;
        }
        return failure;
    }
    @Override protected boolean exited() { return session!=0 && NativeBridge.nativeIsRunning(session)==0; }
    @Override protected void stopCore() {
        if (session!=0) finishCore(true);
    }
    private String finishCore(boolean requestStop) {
        audio.close();
        if (requestStop) NativeBridge.nativeRequestStop(session);
        String summary=NativeBridge.nativeFinishGame(session);
        android.util.Log.w("PocketHLE",summary);
        session=0;
        return summary;
    }
}
