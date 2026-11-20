/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.cybiko;

import android.os.Bundle;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class CybikoGameActivity extends HandheldActivity {
    private File game, data, system, modelFile;
    private String model, id, requirement;
    private boolean replaceFirmware;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            id = getIntent().getStringExtra("installed");
            game = new CybikoLibrary().installed(this,id);
            String[] description;
            try { description=CybikoArtwork.description(game); } catch (IOException unavailable) { description=new String[0]; }
            if (description.length>8 && description[8].trim().matches("[0-9]+(\\.[0-9]+)+")) requirement=description[8].trim();
            data = new File(StoragePaths.library(this),"cybiko/data/" + id);
            Files.createDirectories(data.toPath());
            modelFile = new File(data,"model");
            if (modelFile.isFile()) select(new String(Files.readAllBytes(modelFile.toPath()), java.nio.charset.StandardCharsets.US_ASCII));
            else choose();
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    private void choose() {
        new android.app.AlertDialog.Builder(this).setTitle(requirement==null ? "Cybiko model" : "Cybiko model · requires CyOS " + requirement + "+")
            .setItems(new String[]{"Classic (v1)", "Xtreme"}, (dlg, which) -> select(which==0 ? "classic" : "xtreme"))
            .setOnCancelListener(dlg -> finish()).show();
    }
    private void select(String choice) {
        if (!choice.equals("classic") && !choice.equals("xtreme")) { choose(); return; }
        model=choice;
        system=new File(StoragePaths.library(this),"cybiko/system/" + model);
        try { Files.write(modelFile.toPath(),model.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
        catch (IOException e) { fail(e.toString()); return; }
        if (CybikoFirmware.ready(system)) startMachine();
        else promptFirmware();
    }
    private void promptFirmware() {
        String missing=new File(system,"boot.bin").isFile() ? "CyOS flash image" :
                new File(system,"system.bin").isFile() ? "boot ROM" : "boot ROM and CyOS flash image";
        new android.app.AlertDialog.Builder(this).setTitle("Cybiko " + model + " system files")
            .setMessage("Select the " + missing + " for this model. You can select multiple files or a ZIP.")
            .setPositiveButton("Choose files",(d,w)->pickFirmware())
            .setNegativeButton(android.R.string.cancel,(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
    private void pickFirmware() {
        android.content.Intent pick=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*");
        pick.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(pick,431);
    }
    @Override public void onActivityResult(int request,int result,android.content.Intent picked) {
        super.onActivityResult(request,result,picked);
        if (request!=431) return;
        if (result!=RESULT_OK || picked==null) { if (!replaceFirmware) finish(); replaceFirmware=false; return; }
        try {
            int count=0;
            if (picked.getClipData()!=null) {
                for (int i=0;i<picked.getClipData().getItemCount();i++)
                    count+=CybikoFirmware.install(this,picked.getClipData().getItemAt(i).getUri(),system,model.equals("classic"));
            } else if (picked.getData()!=null) count=CybikoFirmware.install(this,picked.getData(),system,model.equals("classic"));
            if (count==0) android.widget.Toast.makeText(this,"No matching system files",android.widget.Toast.LENGTH_LONG).show();
            if (replaceFirmware) { replaceFirmware=false; return; }
            if (CybikoFirmware.ready(system)) startMachine(); else promptFirmware();
        } catch (IOException e) { fail(e.toString()); }
    }
    private void startMachine() {
        try {
            File modelDir=new File(data,model);
            String systemId=ru.playsoftware.j2meloader.catalog.SourceIdentity.sha256(new File(system,"system.bin"));
            File saves=new File(modelDir,systemId);
            Files.createDirectories(saves.toPath());
            File previous=new File(modelDir,"volume.bin");
            if (previous.isFile() && !new File(saves,"volume.bin").exists())
                Files.move(previous.toPath(),new File(saves,"volume.bin").toPath());
            prepareSession(new File(Config.getConfigsDir(),"cybiko-" + id));
            if (!CybikoCore.model(model.equals("classic"),system.getPath()) || !CybikoCore.start(game.getPath(),saves.getPath()))
                throw new IOException("Could not load this firmware or install the application on the Cybiko volume.");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Cybiko keyboard", android.R.drawable.ic_menu_edit, () -> {
            android.widget.EditText input=new android.widget.EditText(this);
            input.setSingleLine(true);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setHint("Letters, numbers and spaces");
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
            new android.app.AlertDialog.Builder(this).setTitle("Type on Cybiko").setView(input)
                .setPositiveButton("Send",(d,w)->{
                    CybikoCore.type(input.getText().toString()); menu.dismiss();
                }).setNegativeButton(android.R.string.cancel,null).show();
        });
        menu.action("Change model",android.R.drawable.ic_menu_manage,()->{
            new android.app.AlertDialog.Builder(this).setTitle("Cybiko model").setItems(new String[]{"Classic (v1)","Xtreme"},(d,i)->{
                try { Files.write(modelFile.toPath(),(i==0?"classic":"xtreme").getBytes(java.nio.charset.StandardCharsets.US_ASCII)); recreate(); }
                catch (IOException e) { fail(e.toString()); }
            }).show();
        });
        menu.action("Replace system files", android.R.drawable.ic_menu_upload, ()->{ replaceFirmware=true; pickFirmware(); });
    }
    @Override protected ControllerInput.Sink keys() { return new ru.playsoftware.j2meloader.handheld.LibretroKeys() {
        @Override protected void button(int id, boolean down) { CybikoCore.button(id, down); }
    }; }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    @Override protected void setSpeed(int percent) { CybikoCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { CybikoCore.pause(paused); }
    @Override protected void stopCore() { CybikoCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return CybikoCore.frame(pixels); }
    @Override protected int width() { return 160; }
    @Override protected int height() { return 100; }
    @Override protected boolean exited() { return CybikoCore.exited(); }
}
