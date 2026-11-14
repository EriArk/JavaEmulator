/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.util.*;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Shared profile management and per-game inheritance use the existing controls editor. */
public final class SharedProfileDialog {
    private final SharedProfiles.Store store;
    private final Context context;
    private final File root;
    private final GameMenuDialog menu;
    private final ProfileModel game;
    private final Runnable done;
    private SharedProfiles.Catalog catalog;
    private final Set<String> pending = new HashSet<>();
    private List<File> prepared = new ArrayList<>();
    private final Map<String,Integer> usages = new HashMap<>();
    private boolean busy;
    private SharedProfileDialog(Context context, ProfileModel game, Runnable done, SharedProfiles.Store store) {
        this.store=store;
        this.context=context;this.game=game;this.done=done;root=SharedProfiles.root(game);
        menu=new GameMenuDialog(context,store.title());menu.useEdgePanel();
        menu.setOnDismissListener(d->{if(done!=null)done.run();});
    }
    public static GameMenuDialog show(Context c,ProfileModel game,Runnable done){
        return show(c,game,done,SharedProfiles.CONTROLS);
    }
    public static GameMenuDialog show(Context c,ProfileModel game,Runnable done,SharedProfiles.Store store){
        SharedProfileDialog ui=new SharedProfileDialog(c,game,done,store);
        ui.menu.show();
        ui.run("Loading profiles…",()->{ui.catalog=store.read(ui.root);ui.refreshGames();},()->{
            if(game!=null)store.capture(game);
            if(game==null)ui.home();else ui.game();
        });
        return ui.menu;
    }
    private interface Work { void run() throws Exception; }
    private void run(String label,Work work,Runnable next){
        if(busy)return;busy=true;menu.page(label);menu.message("Please wait…");
        new Thread(()->{
            Exception failure=null;try{work.run();}catch(Exception e){failure=e;}
            Exception result=failure;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                busy=false;if(!menu.isShowing())return;
                if(result==null)next.run();else {menu.page("Profiles");error(result);menu.setBackAction(menu::dismiss);}
            });
        },"shared-profiles").start();
    }
    private void refreshGames(){
        prepared=SharedProfiles.gameConfigs(root);usages.clear();
        for(File dir:prepared){ProfileModel p=ProfilesManager.loadConfig(dir);if(p!=null)usages.put(store.profileId(p),usages.getOrDefault(store.profileId(p),0)+1);}
    }
    private void action(String label,Runnable action){menu.action(label,R.drawable.ic_baseline_tune_24,action);}
    private void error(Exception e){menu.message("Could not save profiles: "+e.getMessage());}
    private boolean save(){try{store.write(root,catalog);return true;}catch(Exception e){error(e);return false;}}
    private void home(){
        menu.page(store.title()+" / Global defaults");
        menu.message("Choose global defaults or named profiles for this category. Graphics and controls are assigned independently. Game overrides take priority. Shared changes take effect the next time you open a game.");
        action("Global default",()->edit(null));
        for(SharedProfiles.Entry e:catalog.profiles)action(e.name,()->details(e));
        action("+ New profile",()->name("New profile","",n->{store.create(catalog,n,new com.google.gson.JsonObject());if(save())home();}));
        if(game!=null)action("Create from this game's settings",()->name("New profile","",n->{store.create(catalog,n,store.values(game));if(save())home();}));
        action("Assign profiles to games",this::bulk);
        menu.setBackAction(game==null?menu::dismiss:this::game);
    }
    private void game(){
        menu.page(store.title()+" for this game");
        SharedProfiles.Entry selected=store.find(catalog,store.profileId(game));
        menu.message("Base: "+(selected==null?"Global default":selected.name)+". Changes here are a draft; use the game's Try / Keep flow to confirm.");
        action("Choose base profile",()->{
            menu.page("Choose base profile");
            menu.selectionAction("Global default",store.profileId(game)==null,()->{store.assign(game,null,catalog);game();});
            for(SharedProfiles.Entry e:catalog.profiles)menu.selectionAction(e.name,e.id.equals(store.profileId(game)),()->{store.assign(game,e.id,catalog);game();});
            menu.message("Selecting a base keeps existing game overrides. Clear an override below to inherit that setting.");menu.setBackAction(this::game);
        });
        action("Manage shared profiles",this::home);
        action("Reset all game overrides",()->confirm("Reset overrides?",store.title()+" will inherit the selected base. The other category and saves are unchanged.",()->{store.clear(game,null,catalog);game();}));
        for(int i=0;i<store.FIELDS.length;i++){
            String key=store.FIELDS[i],label=store.LABELS[i];
            action(label+" · "+store.source(game,key,catalog),()->{
                if(store.overrides(game).has(key))confirm("Use inherited setting?",label+" will use the currently assigned profile or Global default.",()->{store.clear(game,key,catalog);game();});
                else menu.message(label+" is inherited. Editing it in the game's controls/display panel creates a game override.");
            });
        }
        menu.setBackAction(menu::dismiss);
    }
    private void details(SharedProfiles.Entry e){
        menu.page(e.name);action("Edit profile",()->edit(e));
        action("Rename",()->name("Rename profile",e.name,n->{
            if(n.trim().isEmpty()||n.trim().length()>64)throw new IllegalArgumentException("Use 1–64 characters.");
            for(SharedProfiles.Entry other:catalog.profiles)if(other!=e && other.name.equalsIgnoreCase(n.trim()))throw new IllegalArgumentException("Name already used.");
            e.name=n.trim();if(save())details(e);
        }));
        action("Duplicate",()->name("Duplicate profile",e.name+" copy",n->{store.create(catalog,n,e.values);if(save())home();}));
        int count=affected(e.id);
        action("Delete profile",()->confirm("Delete "+e.name+"?",count+" games use this profile. They will fall back to Global default; game overrides and saves remain.",()->{
            // Clear references before removing the definition; an interrupted operation leaves a valid base.
            run("Deleting profile…",()->{for(File dir:configs()){
                ProfileModel p=ProfilesManager.loadConfig(dir);
                if(p!=null && e.id.equals(store.profileId(p))){store.assign(p,null,catalog);if(!ProfilesManager.saveConfig(p))throw new java.io.IOException("Could not update "+dir.getName());}
            }
            catalog.profiles.remove(e);store.write(root,catalog);refreshGames();
            },()->{if(game!=null && e.id.equals(store.profileId(game)))store.assign(game,null,catalog);home();});
        }));menu.setBackAction(this::home);
    }
    private void edit(SharedProfiles.Entry e){edit(e,store.editor(catalog,e==null?null:e.id));}
    private void edit(SharedProfiles.Entry e,ProfileModel draft){
        menu.page(e==null?"Global default":e.name);
        menu.message("Editing shared settings. "+affected(e==null?null:e.id)+" linked games; game overrides take priority. Graphics and controls remain independent.");
        if(store.graphics) graphicsEditor(e,draft);
        else {
            menu.choice("Touch layout",new String[]{"Phone keypad","Gamepad"},draft.touchLayout!=null&&draft.touchLayout==1?1:0,i->draft.touchLayout=i);
            action("Edit touch layout / extra buttons",()->menu.editTouchLayout(draft,()->edit(e,draft)));
            menu.choice("On-screen buttons",menu.labels(R.array.game_touch_visibility_modes),draft.getTouchVisibility(),i->draft.touchVisibility=i);
            menu.toggle("Tactile feedback",draft.vkFeedback,v->draft.vkFeedback=v);
        }
        action("Save shared settings",()->confirm("Save shared settings?","Applies to "+affected(e==null?null:e.id)+" linked games the next time they open. Game overrides are retained.",()->{
            if(e==null)catalog.defaults=store.values(draft);else e.values=store.values(draft);
            if(save()){if(game!=null)store.assign(game,store.profileId(game),catalog);home();}
        }));
        menu.setBackAction(this::home);
    }
    private void graphicsEditor(SharedProfiles.Entry e,ProfileModel draft) {
        menu.choice("Image preset",ImagePresets.names(true),ImagePresets.selected(draft,true),i->{
            if(i==ImagePresets.CUSTOM)draft.retroShader=0;else ImagePresets.apply(draft,i,true);
            edit(e,draft);
        });
        action("Supported settings by engine",()->{
            menu.page("Graphics support");
            menu.message("xBRZ, CRT, FSR and interpolation require the Java GPU renderer or Mophun. Scale percentage, position and background color apply to Java. Symbian uses size, rotation and LCD options; integer scaling falls back to Fit. Renderer and phone resolution remain per game.");
            menu.setBackAction(()->edit(e,draft));
        });
        menu.choice("Picture size",menu.labels(R.array.game_picture_sizes),Math.max(0,Math.min(3,draft.screenScaleType)),i->draft.screenScaleType=i);
        menu.percentage("Scale",Math.max(10,Math.min(100,draft.screenScaleRatio)),10,i->draft.screenScaleRatio=i);
        menu.choice("Picture position",menu.labels(R.array.pref_screen_gravity_entries),Math.max(0,Math.min(4,draft.screenGravity)),i->draft.screenGravity=i);
        menu.choice("Picture rotation",menu.labels(R.array.game_rotations),Math.floorMod(draft.screenRotation/90,4),i->draft.screenRotation=i*90);
        menu.choice("Orientation",menu.labels(R.array.game_orientations),Math.max(0,Math.min(2,draft.orientation-1)),i->draft.orientation=i+1);
        action("Background color · "+String.format(java.util.Locale.ROOT,"#%06X",draft.screenBackgroundColor & 0xFFFFFF),()->
                name("Background color (RRGGBB)",String.format(java.util.Locale.ROOT,"%06X",draft.screenBackgroundColor & 0xFFFFFF),value->{
                    String hex=value.trim().replaceFirst("^#","");
                    if(!hex.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Enter six hexadecimal digits.");
                    draft.screenBackgroundColor=Integer.parseInt(hex,16);edit(e,draft);
                }));
        if(draft.getRetroShader()==0) {
            menu.toggle("Smoothing",draft.screenFilter,v->draft.screenFilter=v);
            menu.toggle("FSR upscaling",draft.fsrUpscaling,v->draft.fsrUpscaling=v);
            menu.percentage("FSR sharpness",Math.max(0,Math.min(100,draft.fsrSharpness)),0,i->draft.fsrSharpness=i);
            menu.toggle("Frame interpolation",draft.frameInterpolation,v->draft.frameInterpolation=v);
            GameSettingsPages.screenEffectChoice(menu,draft);
        } else menu.message("Retro shaders use their own filtering; FSR, interpolation and LCD filters are inactive.");
        menu.choice("Mophun capture image",menu.labels(R.array.game_capture_modes),draft.mophunCaptureEnhanced?0:1,i->draft.mophunCaptureEnhanced=i==0);
    }
    private List<File> configs(){return prepared;}
    private int affected(String id){return usages.getOrDefault(id,0);}
    private String gameLabel(File dir){
        if("configs".equals(dir.getParentFile().getName()))return dir.getName();
        File parent=dir.getParentFile();String id="profiles".equals(parent.getName())?parent.getParentFile().getName():parent.getName();
        String title="App "+dir.getName(),phone=id.substring(0,Math.min(8,id.length()));
        File system=new File(context.getFilesDir(),"symbian-systems/"+id);
        try{
            File metadata=new File(system,"set.json");
            if(metadata.length()<65536){org.json.JSONObject json=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(metadata.toPath()),java.nio.charset.StandardCharsets.UTF_8));phone=json.optString("model",phone);}
            File names=new File(system,"library-titles.json");
            if(names.length()<1024*1024){org.json.JSONObject json=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(names.toPath()),java.nio.charset.StandardCharsets.UTF_8));org.json.JSONObject titles=json.optJSONObject("titles");if(titles!=null)title=titles.optString(dir.getName(),title);}
        }catch(Exception ignored){/* Keep a distinct fallback for each virtual phone. */}
        return title+" · "+phone+" ("+id.substring(0,Math.min(8,id.length()))+")";
    }
    private void bulk(){
        List<File> games=configs();menu.page("Assign profiles to games");
        menu.message("Select prepared games. Assignment keeps game overrides; resetting them is a separate explicit choice.");
        for(File dir:games)menu.toggle(gameLabel(dir),pending.contains(dir.getAbsolutePath()),yes->{if(yes)pending.add(dir.getAbsolutePath());else pending.remove(dir.getAbsolutePath());});
        action("Choose profile for selection",()->{
            menu.page("Choose profile for "+pending.size()+" games");
            action("Global default",()->bulkConfirm(null));
            for(SharedProfiles.Entry e:catalog.profiles)action(e.name,()->bulkConfirm(e.id));
            menu.setBackAction(this::bulk);
        });menu.setBackAction(this::home);
    }
    private void bulkConfirm(String id){
        boolean[] reset={false};
        menu.page("Confirm assignment");menu.message(pending.size()+" selected games. Saves, compatibility settings and physical bindings are unchanged.");
        menu.toggle("Also reset "+(store.graphics?"graphics":"control")+" game overrides",false,v->reset[0]=v);
        action("Apply to selected games",()->confirm("Apply profile?",pending.size()+" games"+(reset[0]?"; clear their "+(store.graphics?"graphics":"control")+" overrides.":"; keep their overrides."),()->{
            // Retain failed/unprocessed games for a deliberate retry.
            run("Assigning profiles…",()->{for(String path:new ArrayList<>(pending)){
                ProfileModel p=ProfilesManager.loadConfig(new File(path));if(p==null)throw new java.io.IOException("Unavailable game settings");
                store.assign(p,id,catalog);if(reset[0])store.clear(p,null,catalog);
                if(!ProfilesManager.saveConfig(p))throw new java.io.IOException("Could not update "+new File(path).getName());pending.remove(path);
            }refreshGames();},this::home);
        }));menu.setBackAction(this::bulk);
    }
    private void confirm(String title,String body,Runnable action){new AlertDialog.Builder(context).setTitle(title).setMessage(body).setPositiveButton("Confirm",(d,w)->action.run()).setNegativeButton("Cancel",null).show();}
    private interface NameAction{void run(String value)throws Exception;}
    private void name(String title,String initial,NameAction action){
        EditText input=new EditText(context);input.setSingleLine(true);input.setText(initial);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle(title).setView(input).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{action.run(input.getText().toString());dialog.dismiss();}catch(Exception ex){input.setError(ex.getMessage());}}));dialog.show();
    }
}
