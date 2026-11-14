/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.util.AtomicFile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Linked touch/LCD settings only. Guest compatibility and hardware bindings never enter this store. */
public final class SharedProfiles {
    public static final String FILE = "shared-profiles.json";
    private static final Gson JSON = new Gson();
    private static final Gson WITH_NULLS = new GsonBuilder().serializeNulls().create();
    public static final String[] FIELDS = {"touchLayout", "touchKeypadOverlay", "touchSize", "touchTheme",
            "touchOpacity", "touchPortraitReach", "touchLandscapeReach", "touchPlacements",
            "touchGamepadMappings", "extraTouchButtons", "TouchVisibility", "VirtualKeyboardFeedback",
            "ScreenEffect", "ScreenEffectIntensity", "ScreenshotWithEffect"};
    public static final String[] LABELS = {"Touch layout", "Keypad placement", "Button size", "Button theme",
            "Button opacity", "Portrait reach", "Landscape reach", "Control positions",
            "Touch key mappings", "Extra buttons", "On-screen buttons", "Tactile feedback",
            "LCD filter", "LCD intensity", "Screenshot filter"};
    public static final class Entry {
        public String id, name, importedFrom;
        @com.google.gson.annotations.JsonAdapter(SharedValuesAdapter.class)
        public JsonObject values = new JsonObject();
    }
    public static final class Catalog {
        public int version = 1;
        @com.google.gson.annotations.JsonAdapter(SharedValuesAdapter.class)
        public JsonObject defaults = new JsonObject();
        public ArrayList<Entry> profiles = new ArrayList<>();
    }
    public static File root(ProfileModel p) {
        if (p != null && p.sharedRoot != null) return p.sharedRoot;
        if (p != null && p.dir != null && p.dir.getParentFile() != null
                && "configs".equals(p.dir.getParentFile().getName())) return p.dir.getParentFile().getParentFile();
        String configured = Config.getEmulatorDir();
        return configured == null ? null : new File(configured);
    }
    public static JsonObject values(ProfileModel p) {
        JsonObject all = WITH_NULLS.toJsonTree(p).getAsJsonObject(), result = new JsonObject();
        for (String key : FIELDS) result.add(key, all.has(key) ? all.get(key).deepCopy() : JsonNull.INSTANCE);
        return result;
    }
    private static JsonObject builtIn() {
        ProfileModel p = new ProfileModel(); p.touchLayout=0; p.touchVisibility=0;
        return values(p);
    }
    private static JsonObject filtered(JsonObject values) {
        JsonObject result = new JsonObject();
        if (values != null) for (String key : FIELDS) if (values.has(key)) result.add(key,values.get(key).deepCopy());
        return result;
    }
    private static void overlay(JsonObject into, JsonObject from) {
        for (Map.Entry<String,JsonElement> e : filtered(from).entrySet()) into.add(e.getKey(),e.getValue().deepCopy());
    }
    public static synchronized Catalog read(File root) throws IOException {
        Catalog c = new Catalog();
        if (root == null) return c;
        AtomicFile file = new AtomicFile(new File(root,FILE));
        if (!file.getBaseFile().exists() && !new File(root,FILE+".bak").exists()) return c;
        try {
            if(file.getBaseFile().length()>1024*1024) throw new IOException("Profiles file is too large");
            c=JSON.fromJson(new String(file.readFully(),StandardCharsets.UTF_8),Catalog.class);
            validate(c); return c;
        } catch(RuntimeException e){throw new IOException("Invalid shared profiles",e);}
    }
    private static void validate(Catalog c) throws IOException {
        if(c==null || c.version!=1 || c.profiles==null || c.profiles.size()>200) throw new IOException("Unsupported profiles file");
        c.defaults=filtered(c.defaults); validateValues(c.defaults);
        HashSet<String> ids=new HashSet<>();
        for(Entry e:c.profiles){
            if(e==null || e.id==null || !e.id.matches("[a-zA-Z0-9-]{1,64}") || !ids.add(e.id)
                    || e.name==null || e.name.trim().isEmpty() || e.name.length()>64) throw new IOException("Invalid profile identity");
            e.values=filtered(e.values);validateValues(e.values);
        }
    }
    private static void validateValues(JsonObject v) throws IOException {
        try {
            ProfileModel p=JSON.fromJson(v,ProfileModel.class);
            if(p.touchLayout!=null && (p.touchLayout<0 || p.touchLayout>1))throw new IOException("Shared profiles support Phone keypad and Gamepad layouts. Legacy layouts remain per game.");
            if(p.extraTouchButtons!=null){
                if(p.extraTouchButtons.size()>ru.playsoftware.j2meloader.input.ExtraTouchButton.LIMIT)throw new IOException("Too many extra buttons");
                HashSet<String> ids=new HashSet<>();
                for(ru.playsoftware.j2meloader.input.ExtraTouchButton b:p.extraTouchButtons)
                    if(b==null || !b.valid() || !ids.add(b.id))throw new IOException("Invalid extra button");
            }
            if(p.touchPlacements!=null && p.touchPlacements.size()>32)throw new IOException("Too many placements");
        }catch(RuntimeException e){throw new IOException("Invalid profile values",e);}
    }
    public static synchronized void write(File root,Catalog c) throws IOException {
        validate(c);
        if(root==null || !root.isDirectory()) throw new IOException("Library folder unavailable");
        AtomicFile f=new AtomicFile(new File(root,FILE)); FileOutputStream out=null;
        try{byte[] data=JSON.toJson(c).getBytes(StandardCharsets.UTF_8);if(data.length>1024*1024)throw new IOException("Profiles file is too large");out=f.startWrite();out.write(data);f.finishWrite(out);}
        catch(IOException e){f.failWrite(out);throw e;}
    }
    public static Entry find(Catalog c,String id){for(Entry e:c.profiles)if(e.id.equals(id))return e;return null;}
    public static Entry create(Catalog c,String name,JsonObject from){
        String clean=name==null?"":name.trim();
        if(clean.isEmpty() || clean.length()>64 || c.profiles.size()>=200)throw new IllegalArgumentException("Use a name of 1–64 characters (maximum 200 profiles).");
        for(Entry e:c.profiles)if(e.name.equalsIgnoreCase(clean))throw new IllegalArgumentException("A profile with this name already exists.");
        Entry e=new Entry();e.id=UUID.randomUUID().toString();e.name=clean;e.values=filtered(from);c.profiles.add(e);return e;
    }
    private static JsonObject base(Catalog c,String id){JsonObject b=builtIn();overlay(b,c.defaults);Entry e=find(c,id);if(e!=null)overlay(b,e.values);return b;}
    private static void apply(ProfileModel p,JsonObject settings){
        JsonObject all=JSON.toJsonTree(p).getAsJsonObject();overlay(all,settings);
        ProfileModel copy=JSON.fromJson(all,ProfileModel.class);
        try{for(java.lang.reflect.Field f:ProfileModel.class.getFields()){
            com.google.gson.annotations.SerializedName a=f.getAnnotation(com.google.gson.annotations.SerializedName.class);
            String key=a==null?f.getName():a.value();
            if(Arrays.asList(FIELDS).contains(key))f.set(p,f.get(copy));
        }}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    /** Existing game files become explicit overrides, never silently adopted into defaults. */
    public static void resolve(ProfileModel p,boolean existing){
        if(!p.sharedSettings){p.sharedSettings=true;p.sharedOverrides=existing?values(p):new JsonObject();}
        if(p.sharedOverrides==null)p.sharedOverrides=new JsonObject();
        p.sharedOverrides=filtered(p.sharedOverrides);
        try{
            Catalog c=read(root(p));JsonObject b=base(c,p.sharedProfileId);overlay(b,p.sharedOverrides);apply(p,b);
        }catch(IOException|RuntimeException e){android.util.Log.w("SharedProfiles","Retaining stored settings; shared profile unavailable",e);}
        if(p.extraTouchButtons!=null){
            java.util.ArrayList<ru.playsoftware.j2meloader.input.ExtraTouchButton> valid=new java.util.ArrayList<>();
            HashSet<String> ids=new HashSet<>();
            for(ru.playsoftware.j2meloader.input.ExtraTouchButton b:p.extraTouchButtons)
                if(b!=null && b.valid() && ids.add(b.id) && valid.size()<ru.playsoftware.j2meloader.input.ExtraTouchButton.LIMIT)valid.add(b);
            p.extraTouchButtons=valid;
        }
        p.sharedSnapshot=values(p);
    }
    public static void capture(ProfileModel p){
        if(!p.sharedSettings) {p.sharedSettings=true;p.sharedOverrides=values(p);}
        if(p.sharedOverrides==null)p.sharedOverrides=new JsonObject();
        JsonObject now=values(p);
        if(p.sharedSnapshot!=null)for(String key:FIELDS)
            if(!Objects.equals(now.get(key),p.sharedSnapshot.get(key)))p.sharedOverrides.add(key,now.get(key).deepCopy());
        p.sharedSnapshot=now;
    }
    public static void assign(ProfileModel p,String id,Catalog c){capture(p);p.sharedProfileId=id;refresh(p,c);}
    public static void clear(ProfileModel p,String key,Catalog c){capture(p);if(key==null)p.sharedOverrides=new JsonObject();else p.sharedOverrides.remove(key);refresh(p,c);}
    private static void refresh(ProfileModel p,Catalog c){JsonObject b=base(c,p.sharedProfileId);overlay(b,p.sharedOverrides);apply(p,b);p.sharedSnapshot=values(p);}
    public static ProfileModel editor(Catalog c,String id){ProfileModel p=new ProfileModel();apply(p,base(c,id));return p;}
    public static String source(ProfileModel p,String key,Catalog c){
        if(p.sharedOverrides!=null && p.sharedOverrides.has(key))return "Game override";
        Entry e=find(c,p.sharedProfileId);return e!=null && e.values.has(key)?e.name:"Global default";
    }
    /** Portable definition travels inside each config, including whole-phone backups. */
    public static void prepareSave(ProfileModel p){
        capture(p);
        try { Catalog c=read(root(p));Entry e=find(c,p.sharedProfileId);
            p.sharedPortable=base(c,p.sharedProfileId);p.sharedPortableName=e==null?"Imported defaults":e.name;
        }catch(IOException ignored){/* Retain the last known portable definition. */}
    }
    /** Never overwrite destination profiles/defaults during restore. Collisions receive a new identity. */
    public static void importPortable(ProfileModel p,File root) throws IOException {
        if(!p.sharedSettings || p.sharedPortable==null)return;
        validateValues(p.sharedPortable);Catalog c=read(root);JsonObject portable=filtered(p.sharedPortable);
        Entry same=find(c,p.sharedProfileId);
        if(same!=null && base(c,same.id).equals(portable))return;
        if(p.sharedProfileId==null && base(c,null).equals(portable))return;
        // Reuse an identical imported definition so a multi-game restore remains linked.
        String sourceId=p.sharedProfileId==null?"global-default":p.sharedProfileId;
        for(Entry e:c.profiles)if(sourceId.equals(e.importedFrom) && base(c,e.id).equals(portable)){p.sharedProfileId=e.id;return;}
        String name=p.sharedPortableName==null?"Imported profile":p.sharedPortableName;
        if(name.length()>48)name=name.substring(0,48);
        String unique=name;int n=2;boolean collision;
        do{collision=false;for(Entry e:c.profiles)if(e.name.equalsIgnoreCase(unique)){collision=true;unique=name+" ("+(n++)+")";break;}}while(collision);
        Entry e=create(c,unique,portable);e.importedFrom=sourceId;
        if(p.sharedProfileId!=null && same==null && p.sharedProfileId.matches("[a-zA-Z0-9-]{1,64}"))e.id=p.sharedProfileId;
        write(root,c);p.sharedProfileId=e.id;
    }
    public static void importCatalog(Catalog source,File destination) throws IOException {
        validate(source);
        for(Entry e:source.profiles){ProfileModel p=new ProfileModel();p.sharedSettings=true;p.sharedProfileId=e.id;
            p.sharedPortable=base(source,e.id);p.sharedPortableName=e.name;importPortable(p,destination);}
        if(source.defaults.size()>0){ProfileModel p=new ProfileModel();p.sharedSettings=true;
            p.sharedPortable=base(source,null);p.sharedPortableName="Imported defaults";importPortable(p,destination);}
    }
    public static byte[] portableConfig(File config) throws IOException {
        if(config.length()>1024*1024)throw new IOException("Game settings too large");
        try {
            ProfileModel p=JSON.fromJson(new String(java.nio.file.Files.readAllBytes(config.toPath()),StandardCharsets.UTF_8),ProfileModel.class);
            if(p==null)throw new IOException("Invalid game settings");
            p.dir=config.getParentFile();resolve(p,true);prepareSave(p);
            return JSON.toJson(p).getBytes(StandardCharsets.UTF_8);
        }catch(RuntimeException e){throw new IOException("Invalid game settings",e);}
    }
    public static void restorePhoneProfiles(File folder) throws IOException {
        File[] dirs=folder.listFiles();if(dirs==null)return;
        for(File dir:dirs){File config=new File(dir,"config.json");if(!config.isFile())continue;
            if(config.length()>1024*1024)throw new IOException("Game settings too large");
            ProfileModel p=JSON.fromJson(new String(java.nio.file.Files.readAllBytes(config.toPath()),StandardCharsets.UTF_8),ProfileModel.class);
            if(p==null)throw new IOException("Invalid game settings");p.dir=dir;
            importPortable(p,root(p));resolve(p,true);
            if(!ProfilesManager.saveConfig(p))throw new IOException("Could not restore profile assignment");
        }
    }
    public static java.util.List<File> gameConfigs(File root){
        ArrayList<File> result=new ArrayList<>();collect(new File(root,"configs"),result,0);
        android.content.Context context=javax.microedition.util.ContextHolder.getAppContext();
        if(context!=null) {
            File files=context.getFilesDir();
            for(String parent:new String[]{"symbian-systems","symbian-profiles"}) {
                File[] phones=new File(files,parent).listFiles();
                if(phones!=null)for(File phone:phones)collect(parent.equals("symbian-systems")?new File(phone,"profiles"):phone,result,0);
            }
        }
        return result;
    }
    private static void collect(File dir,List<File> result,int depth){
        if(depth>1)return;File[] children=dir.listFiles();if(children==null)return;
        for(File f:children)if(f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())){
            if(new File(f,"config.json").isFile())result.add(f);
        }
        result.sort(Comparator.comparing(File::getName));
    }
    private SharedProfiles(){}
}
