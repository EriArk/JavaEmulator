/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.espboy;

import android.webkit.JsPromptResult;
import java.io.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import static javax.microedition.lcdui.Canvas.*;

public final class EspboyActivity extends PackagedWebActivity {
    private byte[] program;
    private boolean finished, ready;
    @Override protected String engine() { return "espboy"; }
    @Override protected String platformTitle() { return "ESPboy"; }
    @Override protected int[][] sizes() { return new int[][]{{128,128}}; }
    @Override protected boolean usesStylus() { return false; }
    @Override protected boolean exited() { return finished; }
    @Override protected boolean artworkReady() { return ready; }
    @Override protected void more(GameMenuDialog menu) {}
    @Override protected String openGame(String id) throws IOException {
        File card=new File(StoragePaths.library(this),"espboy/packages/"+id+"/program.bin");
        byte[] bytes=java.nio.file.Files.readAllBytes(card.toPath());
        program=new LgeProgram(bytes,LgeProgram.hasHeader(bytes)).code;
        return "/index.html";
    }
    @Override protected InputStream openResource(String path) throws IOException {
        if ("/program.bin".equals(path)) return new ByteArrayInputStream(program);
        if (!path.matches("/[A-Za-z]+\\.(js|html)")) throw new FileNotFoundException();
        return getAssets().open("espboy"+path);
    }
    @Override protected boolean gamePrompt(String name,String value,JsPromptResult result) {
        if ("esp-exit".equals(name)) finished=true;
        else if ("esp-frame".equals(name)) ready=true;
        else return false;
        result.confirm(""); return true;
    }
    @Override protected void key(int code,boolean down) {
        int mask;
        switch(code) {
            case KEY_UP: mask=1; break;
            case KEY_DOWN: mask=2; break;
            case KEY_LEFT: mask=4; break;
            case KEY_RIGHT: mask=8; break;
            case KEY_FIRE: mask=16; break;
            case KEY_SOFT_RIGHT: mask=32; break;
            case '7': mask=64; break;
            case '9': mask=128; break;
            default: return;
        }
        evaluate("window.espKey?.("+mask+","+down+")");
    }
}
