/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.portfolio;

import static javax.microedition.lcdui.Canvas.*;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.util.AtomicFile;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.webkit.JsPromptResult;
import android.widget.EditText;
import android.widget.Toast;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

public final class PortfolioGameActivity extends PackagedWebActivity {
    private static final int[][] SCREEN = {{240,64}};
    private ZipFile zip;
    private final List<ZipEntry> files = new ArrayList<>();
    private List<String> programs;
    private String program;
    private AtomicFile state;
    private boolean exitAfterSave, running;
    private String identity() { return getIntent().getStringExtra("installed"); }
    @Override protected String engine() { return "portfolio"; }
    @Override protected String platformTitle() { return "Atari Portfolio"; }
    @Override protected int[][] sizes() { return SCREEN; }
    @Override protected boolean artworkReady() { return running; }
    @Override protected boolean usesStylus() { return false; }

    @Override protected boolean prepareGame() {
        List<String> missing = PortfolioFirmware.missing(this);
        if (missing.isEmpty()) return true;
        new AlertDialog.Builder(this).setTitle("Add Atari Portfolio firmware")
            .setMessage("Choose ROM files or a ZIP. Missing files:\n" + String.join("\n", missing))
            .setPositiveButton("Choose files", (d,w) -> pickFirmware())
            .setNegativeButton(android.R.string.cancel, (d,w) -> finish()).setOnCancelListener(d -> finish()).show();
        return false;
    }
    private void pickFirmware() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 101);
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req,result,data);
        if (req != 101) return;
        if (result != RESULT_OK || data == null) { if (zip == null) finish(); return; }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i=0;i<data.getClipData().getItemCount();i++)
            uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        new Thread(() -> {
            try {
                for (Uri uri : uris) PortfolioFirmware.install(this, uri);
                runOnUiThread(this::recreate);
            } catch (IOException e) { runOnUiThread(() -> new AlertDialog.Builder(this)
                .setMessage(e.getMessage()).setPositiveButton("Choose files",(d,w)->pickFirmware())
                .setNegativeButton(android.R.string.cancel,(d,w)->{if(zip==null)finish();}).show()); }
        }, "Portfolio firmware").start();
    }
    @Override protected String openGame(String id) throws IOException {
        File root = StoragePaths.library(this);
        zip = new ZipFile(new File(root, "portfolio/packages/" + id + "/game.zip"));
        programs = PortfolioLibrary.programs(zip);
        if (programs.isEmpty()) throw new IOException("Package has no Portfolio program");
        File dir = new File(root, "portfolio/data/" + id);
        java.nio.file.Files.createDirectories(dir.toPath());
        File choice = new File(dir,"program.txt");
        program = choice.isFile() ? new String(java.nio.file.Files.readAllBytes(choice.toPath()), StandardCharsets.UTF_8) : programs.get(0);
        if (!programs.contains(program)) program = programs.get(0);
        state = new AtomicFile(new File(dir, "slot-" + programs.indexOf(program) + ".state"));
        String folder = program.substring(0, program.lastIndexOf('/')+1);
        for (Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();) {
            ZipEntry e=it.nextElement(); String name=e.getName();
            if (!e.isDirectory() && name.startsWith(folder) && name.indexOf('/',folder.length())<0 && !name.substring(folder.length()).startsWith(".")) files.add(e);
        }
        return "/index.html";
    }
    @Override protected InputStream openResource(String path) throws IOException {
        if (path.equals("/package.json")) {
            JSONArray names=new JSONArray();
            for (ZipEntry file : files) names.put(new File(file.getName()).getName());
            try {
                String s=new JSONObject().put("program",new File(program).getName()).put("files",names).put("folder",program.substring(0,program.lastIndexOf('/')+1)).toString();
                return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
            } catch (JSONException e) { throw new IOException(e); }
        }
        if (path.matches("/file/[0-9]{1,6}")) {
            int i=Integer.parseInt(path.substring(6)); if(i<files.size())return zip.getInputStream(files.get(i));
            throw new FileNotFoundException();
        }
        if (path.startsWith("/rom/") && Arrays.asList(PortfolioFirmware.NAMES).contains(path.substring(5)))
            return new FileInputStream(new File(PortfolioFirmware.directory(this),path.substring(5)));
        if (path.equals("/snapshot")) return state.openRead();
        if (path.contains("..") || path.contains("\\")) throw new FileNotFoundException();
        return getAssets().open("portfolio" + path);
    }
    @Override protected boolean gamePrompt(String msg,String value,JsPromptResult result) {
        if (msg.equals("portfolio-ready")) { running=true; result.confirm(""); return true; }
        if (!msg.equals("portfolio-state")) return false;
        FileOutputStream out=null;
        try {
            out=state.startWrite(); out.write(value.getBytes(StandardCharsets.UTF_8)); state.finishWrite(out);
            result.confirm("ok");
            if (exitAfterSave) { exitAfterSave=false; getWindow().getDecorView().post(this::finish); }
        } catch(IOException e) {
            if(out!=null)state.failWrite(out);
            exitAfterSave=false; result.confirm(""); Toast.makeText(this,"Could not save game",Toast.LENGTH_LONG).show();
        }
        return true;
    }
    @Override protected void more(GameMenuDialog menu) {
        menu.action("Portfolio keyboard", android.R.drawable.ic_menu_edit, () -> {
            EditText input=new EditText(this); input.setSingleLine(true); input.setHint("Text or game keys");
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            new AlertDialog.Builder(this).setTitle("Type on Portfolio").setView(input)
                .setPositiveButton("Send",(d,w)->{menu.dismiss();evaluate("portfolioType("+JSONObject.quote(input.getText().toString())+")");})
                .setNeutralButton("Send + Enter",(d,w)->{menu.dismiss();evaluate("portfolioType("+JSONObject.quote(input.getText()+"\n")+")");})
                .setNegativeButton(android.R.string.cancel,null).show();
        });
        menu.action("Portfolio special keys", android.R.drawable.ic_menu_sort_alphabetically, () -> {
            String[] keys={"Enter","Space","Escape","Backspace","Tab","Delete","F1","Ctrl+C","Alt+F"};
            new AlertDialog.Builder(this).setTitle("Send key").setItems(keys,(d,i)->{
                menu.dismiss(); evaluate("portfolioChord("+JSONObject.quote(keys[i])+")");
            }).show();
        });
        menu.action("Save state",android.R.drawable.ic_menu_save,()->{menu.dismiss();evaluate("portfolioSave()");});
        if (state.getBaseFile().exists()) menu.action("Load state",android.R.drawable.ic_menu_revert,()->{menu.dismiss();evaluate("portfolioLoad()");});
        if(programs.size()>1) menu.action("Choose program",android.R.drawable.ic_media_play,()->new AlertDialog.Builder(this)
            .setItems(programs.toArray(new String[0]),(d,i)->{
                try { java.nio.file.Files.write(new File(state.getBaseFile().getParent(),"program.txt").toPath(),programs.get(i).getBytes(StandardCharsets.UTF_8)); recreate(); }
                catch(IOException e){fail(e.getMessage());}
            }).show());
        menu.action("Import firmware",android.R.drawable.ic_menu_upload,this::pickFirmware);
    }
    @Override protected void key(int code, boolean down) {
        String guest;
        switch (code) {
            case KEY_UP: guest="ArrowUp"; break;
            case KEY_DOWN: guest="ArrowDown"; break;
            case KEY_LEFT: guest="ArrowLeft"; break;
            case KEY_RIGHT: guest="ArrowRight"; break;
            case KEY_FIRE: guest="Enter"; break;
            case KEY_SOFT_LEFT: guest="Space"; break;
            case KEY_SOFT_RIGHT: guest="Escape"; break;
            case KEY_CLEAR: guest="Backspace"; break;
            case KEY_STAR: guest="Tab"; break;
            case KEY_POUND: guest="F1"; break;
            default:
                if (code<'0' || code>'9') return;
                guest="Digit"+(char)code;
        }
        evaluate("window.portfolioKey?.("+JSONObject.quote(guest)+","+down+")");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (running && hasWindowFocus() && event.getDevice()!=null &&
                event.getDevice().getKeyboardType()==InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) {
            int code=event.getKeyCode();
            String guest=null;
            if (code>=KeyEvent.KEYCODE_A && code<=KeyEvent.KEYCODE_Z) guest="Key"+(char)('A'+code-KeyEvent.KEYCODE_A);
            else switch(code) {
                case KeyEvent.KEYCODE_SHIFT_LEFT: guest="ShiftLeft"; break;
                case KeyEvent.KEYCODE_SHIFT_RIGHT: guest="ShiftRight"; break;
                case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_CTRL_RIGHT: guest="ControlLeft"; break;
                case KeyEvent.KEYCODE_ALT_LEFT: case KeyEvent.KEYCODE_ALT_RIGHT: guest="AltLeft"; break;
                case KeyEvent.KEYCODE_F1: guest="F1"; break;
                case KeyEvent.KEYCODE_SPACE: guest="Space"; break;
                case KeyEvent.KEYCODE_DEL: guest="Backspace"; break;
                case KeyEvent.KEYCODE_TAB: guest="Tab"; break;
                case KeyEvent.KEYCODE_MINUS: guest="Minus"; break;
                case KeyEvent.KEYCODE_EQUALS: guest="Equal"; break;
                case KeyEvent.KEYCODE_SLASH: guest="Slash"; break;
                case KeyEvent.KEYCODE_PERIOD: guest="Period"; break;
                case KeyEvent.KEYCODE_COMMA: guest="Comma"; break;
                case KeyEvent.KEYCODE_SEMICOLON: guest="Semicolon"; break;
            }
            if(guest!=null) {
                if(event.getRepeatCount()==0) evaluate("window.portfolioKey?.("+JSONObject.quote(guest)+","+(event.getAction()==KeyEvent.ACTION_DOWN)+")");
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected boolean canSaveOnExit() { return running; }
    @Override protected void saveAndExit() {
        exitAfterSave=true; pauseCore(false); evaluate("portfolioSave()");
    }
    @Override protected void setSpeed(int speed) { super.setSpeed(speed); evaluate("window.portfolioSpeed?.("+speed+")"); }
    @Override protected void closeGame() { if(zip!=null)try{zip.close();}catch(IOException ignored){} }
}
