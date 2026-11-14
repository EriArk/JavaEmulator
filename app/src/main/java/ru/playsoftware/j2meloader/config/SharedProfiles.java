/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.util.AtomicFile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent linked graphics and touch-control settings. Guest compatibility stays per game. */
public final class SharedProfiles {
    public static final String FILE = "shared-profiles.json";
    public static final String GRAPHICS_FILE = "graphics-profiles.json";
    private static final Gson JSON = new Gson();
    private static final Gson WITH_NULLS = new GsonBuilder().serializeNulls().create();
    private static final String[] LCD_FIELDS = {"ScreenEffect", "ScreenEffectIntensity", "ScreenshotWithEffect"};
    public static final String[] FIELDS = {"touchLayout", "touchKeypadOverlay", "touchSize", "touchTheme",
            "touchOpacity", "touchPortraitReach", "touchLandscapeReach", "touchPlacements",
            "touchGamepadMappings", "extraTouchButtons", "TouchVisibility", "VirtualKeyboardFeedback"};
    public static final String[] LABELS = {"Touch layout", "Keypad placement", "Button size", "Button theme",
            "Button opacity", "Portrait reach", "Landscape reach", "Control positions",
            "Touch key mappings", "Extra buttons", "On-screen buttons", "Tactile feedback"};
    public static final Store CONTROLS = new Store(false, FILE, FIELDS, LABELS);
    public static final Store GRAPHICS = new Store(true, GRAPHICS_FILE,
            new String[]{"ScreenScaleType", "ScreenScaleRatio", "ScreenGravity", "ScreenRotation", "Orientation",
                    "ScreenBackgroundColor", "ScreenFilter", "RetroShader", "FsrUpscaling", "FsrSharpness",
                    "FrameInterpolation", "ScreenEffect", "ScreenEffectIntensity", "ScreenshotWithEffect", "MophunCaptureEnhanced"},
            new String[]{"Picture size", "Scale percentage", "Picture position", "Picture rotation", "Orientation",
                    "Background color", "Smoothing", "Retro shader", "FSR upscaling", "FSR sharpness",
                    "Frame interpolation", "LCD filter", "LCD intensity", "Screenshot filter", "Mophun capture image"});
    public static final class Entry {
        public String id, name, importedFrom;
        @com.google.gson.annotations.JsonAdapter(SharedValuesAdapter.class)
        public JsonObject values = new JsonObject();
    }
    public static final class Catalog {
        public int version = 2;
        /** True only for the independent graphics catalog split from Beta 6. */
        public boolean legacyCombined;
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
    public static final class Store {
        public final boolean graphics;
        public final String FILE;
        public final String[] FIELDS, LABELS;
        private Store(boolean graphics,String file,String[] fields,String[] labels) {
            this.graphics=graphics; FILE=file; FIELDS=fields; LABELS=labels;
        }
        public String title() { return graphics ? "Graphics profiles" : "Control profiles"; }
        public boolean settings(ProfileModel p) { return graphics ? p.graphicsSettings : p.sharedSettings; }
        private void setSettings(ProfileModel p, boolean value) { if(graphics) p.graphicsSettings=value; else p.sharedSettings=value; }
        public String profileId(ProfileModel p) { return graphics ? p.graphicsProfileId : p.sharedProfileId; }
        private void setProfileId(ProfileModel p, String value) { if(graphics) p.graphicsProfileId=value; else p.sharedProfileId=value; }
        public JsonObject overrides(ProfileModel p) { return graphics ? p.graphicsOverrides : p.sharedOverrides; }
        private void setOverrides(ProfileModel p, JsonObject value) { if(graphics) p.graphicsOverrides=value; else p.sharedOverrides=value; }
        public JsonObject snapshot(ProfileModel p) { return graphics ? p.graphicsSnapshot : p.sharedSnapshot; }
        private void setSnapshot(ProfileModel p, JsonObject value) { if(graphics) p.graphicsSnapshot=value; else p.sharedSnapshot=value; }
        public JsonObject portable(ProfileModel p) { return graphics ? p.graphicsPortable : p.sharedPortable; }
        private void setPortable(ProfileModel p, JsonObject value) { if(graphics) p.graphicsPortable=value; else p.sharedPortable=value; }
        public String portableName(ProfileModel p) { return graphics ? p.graphicsPortableName : p.sharedPortableName; }
        private void setPortableName(ProfileModel p, String value) { if(graphics) p.graphicsPortableName=value; else p.sharedPortableName=value; }
        public JsonObject values(ProfileModel p) {
            JsonObject all = WITH_NULLS.toJsonTree(p).getAsJsonObject(), result = new JsonObject();
            for (String key : FIELDS) result.add(key, all.has(key) ? all.get(key).deepCopy() : JsonNull.INSTANCE);
            return result;
        }
        private JsonObject builtIn() {
            ProfileModel p = new ProfileModel(); p.touchLayout=0; p.touchVisibility=0;
            p.screenScaleType=1; p.screenScaleRatio=100;
            p.screenGravity=ru.playsoftware.j2meloader.input.DeviceControls.connected(javax.microedition.util.ContextHolder.getAppContext())?2:1;
            p.orientation=1; p.screenBackgroundColor=0x101218;
            return values(p);
        }
        private JsonObject filtered(JsonObject values) {
            JsonObject result = new JsonObject();
            if (values != null) for (String key : FIELDS) if (values.has(key)) result.add(key,values.get(key).deepCopy());
            return result;
        }
        private void overlay(JsonObject into, JsonObject from) {
            for (Map.Entry<String,JsonElement> e : filtered(from).entrySet()) into.add(e.getKey(),e.getValue().deepCopy());
        }
        public synchronized Catalog read(File root) throws IOException {
            Catalog c = new Catalog();
            if (root == null) return c;
            AtomicFile file = new AtomicFile(new File(root,FILE));
            if (!file.getBaseFile().exists() && !new File(root,FILE+".bak").exists())
                return graphics ? legacyGraphics(root) : c;
            try {
                if(file.getBaseFile().length()>1024*1024) throw new IOException("Profiles file is too large");
                c=JSON.fromJson(new String(file.readFully(),StandardCharsets.UTF_8),Catalog.class);
                validate(c); return c;
            } catch(RuntimeException e){throw new IOException("Invalid shared profiles",e);}
        }
        private void validate(Catalog c) throws IOException {
            if(c==null || c.version!=1 && c.version!=2 || c.profiles==null || c.profiles.size()>200) throw new IOException("Unsupported profiles file");
            c.defaults=filtered(c.defaults); validateValues(c.defaults);
            HashSet<String> ids=new HashSet<>();
            for(Entry e:c.profiles){
                if(e==null || e.id==null || !e.id.matches("[a-zA-Z0-9-]{1,64}") || !ids.add(e.id)
                        || e.name==null || e.name.trim().isEmpty() || e.name.length()>64) throw new IOException("Invalid profile identity");
                e.values=filtered(e.values);validateValues(e.values);
            }
        }
        private void validateValues(JsonObject v) throws IOException {
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
        public synchronized void write(File root,Catalog c) throws IOException {
            if (!graphics) preserveLegacyGraphics(root);
            validate(c); c.version=2;
            if(root==null || !root.isDirectory()) throw new IOException("Library folder unavailable");
            AtomicFile f=new AtomicFile(new File(root,FILE)); FileOutputStream out=null;
            try{byte[] data=JSON.toJson(c).getBytes(StandardCharsets.UTF_8);if(data.length>1024*1024)throw new IOException("Profiles file is too large");out=f.startWrite();out.write(data);f.finishWrite(out);}
            catch(IOException e){f.failWrite(out);throw e;}
        }
        public Entry find(Catalog c,String id){for(Entry e:c.profiles)if(e.id.equals(id))return e;return null;}
        public Entry create(Catalog c,String name,JsonObject from){
            String clean=name==null?"":name.trim();
            if(clean.isEmpty() || clean.length()>64 || c.profiles.size()>=200)throw new IllegalArgumentException("Use a name of 1–64 characters (maximum 200 profiles).");
            for(Entry e:c.profiles)if(e.name.equalsIgnoreCase(clean))throw new IllegalArgumentException("A profile with this name already exists.");
            Entry e=new Entry();e.id=UUID.randomUUID().toString();e.name=clean;e.values=filtered(from);c.profiles.add(e);return e;
        }
        private JsonObject base(Catalog c,String id){JsonObject b=builtIn();overlay(b,c.defaults);Entry e=find(c,id);if(e!=null)overlay(b,e.values);return b;}
        public void applyValues(ProfileModel p,JsonObject settings){
            JsonObject all=JSON.toJsonTree(p).getAsJsonObject();overlay(all,settings);
            ProfileModel copy=JSON.fromJson(all,ProfileModel.class);
            try{for(java.lang.reflect.Field f:ProfileModel.class.getFields()){
                com.google.gson.annotations.SerializedName a=f.getAnnotation(com.google.gson.annotations.SerializedName.class);
                String key=a==null?f.getName():a.value();
                if(Arrays.asList(FIELDS).contains(key))f.set(p,f.get(copy));
            }}catch(IllegalAccessException e){throw new IllegalStateException(e);}
        }
        /** Existing game files become explicit overrides, never silently adopted into defaults. */
        public void resolve(ProfileModel p,boolean existing){
            if(!settings(p)){setSettings(p,true);setOverrides(p,existing?values(p):new JsonObject());}
            if(overrides(p)==null)setOverrides(p,new JsonObject());
            setOverrides(p,filtered(overrides(p)));
            try{
                Catalog c=read(root(p));JsonObject b=base(c,profileId(p));overlay(b,overrides(p));applyValues(p,b);
            }catch(IOException|RuntimeException e){android.util.Log.w("SharedProfiles","Retaining stored settings; shared profile unavailable",e);}
            if(!graphics && p.extraTouchButtons!=null){
                java.util.ArrayList<ru.playsoftware.j2meloader.input.ExtraTouchButton> valid=new java.util.ArrayList<>();
                HashSet<String> ids=new HashSet<>();
                for(ru.playsoftware.j2meloader.input.ExtraTouchButton b:p.extraTouchButtons)
                    if(b!=null && b.valid() && ids.add(b.id) && valid.size()<ru.playsoftware.j2meloader.input.ExtraTouchButton.LIMIT)valid.add(b);
                p.extraTouchButtons=valid;
            }
            setSnapshot(p,values(p));
        }
        public void capture(ProfileModel p){
            if(!settings(p)) {setSettings(p,true);setOverrides(p,values(p));}
            if(overrides(p)==null)setOverrides(p,new JsonObject());
            JsonObject now=values(p);
            if(snapshot(p)!=null)for(String key:FIELDS)
                if(!Objects.equals(now.get(key),snapshot(p).get(key)))overrides(p).add(key,now.get(key).deepCopy());
            setSnapshot(p,now);
        }
        public void assign(ProfileModel p,String id,Catalog c){capture(p);setProfileId(p,id);refresh(p,c);}
        public void clear(ProfileModel p,String key,Catalog c){capture(p);if(key==null)setOverrides(p,new JsonObject());else overrides(p).remove(key);refresh(p,c);}
        private void refresh(ProfileModel p,Catalog c){JsonObject b=base(c,profileId(p));overlay(b,overrides(p));applyValues(p,b);setSnapshot(p,values(p));}
        public ProfileModel editor(Catalog c,String id){ProfileModel p=new ProfileModel();applyValues(p,base(c,id));return p;}
        public String source(ProfileModel p,String key,Catalog c){
            if(overrides(p)!=null && overrides(p).has(key))return "Game override";
            Entry e=find(c,profileId(p));return e!=null && e.values.has(key)?e.name:"Global default";
        }
        /** Portable definition travels inside each config, including whole-phone backups. */
        public void prepareSave(ProfileModel p){
            capture(p);
            try { Catalog c=read(root(p));Entry e=find(c,profileId(p));
                setPortable(p,base(c,profileId(p)));setPortableName(p,e==null?"Imported defaults":e.name);
            }catch(IOException ignored){/* Retain the last known portable definition. */}
        }
        /** Never overwrite destination profiles/defaults during restore. Collisions receive a new identity. */
        public void importPortable(ProfileModel p,File root) throws IOException {
            if(!settings(p) || portable(p)==null)return;
            JsonObject portable=filtered(portable(p));validateValues(portable);Catalog c=read(root);
            Entry same=find(c,profileId(p));
            if(same!=null && base(c,same.id).equals(portable))return;
            if(profileId(p)==null && base(c,null).equals(portable))return;
            // Reuse an identical imported definition so a multi-game restore remains linked.
            String sourceId=profileId(p)==null?"global-default":profileId(p);
            for(Entry e:c.profiles)if(sourceId.equals(e.importedFrom) && base(c,e.id).equals(portable)){setProfileId(p,e.id);return;}
            String name=portableName(p)==null?"Imported profile":portableName(p);
            if(name.length()>48)name=name.substring(0,48);
            String unique=name;int n=2;boolean collision;
            do{collision=false;for(Entry e:c.profiles)if(e.name.equalsIgnoreCase(unique)){collision=true;unique=name+" ("+(n++)+")";break;}}while(collision);
            Entry e=create(c,unique,portable);e.importedFrom=sourceId;
            if(profileId(p)!=null && same==null && profileId(p).matches("[a-zA-Z0-9-]{1,64}"))e.id=profileId(p);
            write(root,c);setProfileId(p,e.id);
        }
        public void importCatalog(Catalog source,File destination) throws IOException {
            validate(source);
            for(Entry e:source.profiles){ProfileModel p=new ProfileModel();setSettings(p,true);setProfileId(p,e.id);
                setPortable(p,base(source,e.id));setPortableName(p,e.name);importPortable(p,destination);}
            if(source.defaults.size()>0){ProfileModel p=new ProfileModel();setSettings(p,true);
                setPortable(p,base(source,null));setPortableName(p,"Imported defaults");importPortable(p,destination);}
        }
    }
    // Compatibility facade: assignments and editors now refer only to controls.
    public static Catalog read(File root) throws IOException { return CONTROLS.read(root); }
    public static void write(File root,Catalog c) throws IOException { CONTROLS.write(root,c); }
    public static JsonObject values(ProfileModel p) { return CONTROLS.values(p); }
    public static Entry find(Catalog c,String id) { return CONTROLS.find(c,id); }
    public static Entry create(Catalog c,String name,JsonObject from) { return CONTROLS.create(c,name,from); }
    public static void assign(ProfileModel p,String id,Catalog c) { CONTROLS.assign(p,id,c); }
    public static void clear(ProfileModel p,String key,Catalog c) { CONTROLS.clear(p,key,c); }
    public static ProfileModel editor(Catalog c,String id) { return CONTROLS.editor(c,id); }
    public static String source(ProfileModel p,String key,Catalog c) { return CONTROLS.source(p,key,c); }
    public static void resolve(ProfileModel p,boolean existing) {
        migrateGraphics(p,existing);
        GRAPHICS.resolve(p,existing); CONTROLS.resolve(p,existing);
    }
    public static void capture(ProfileModel p) {
        migrateGraphics(p,true);
        GRAPHICS.capture(p); CONTROLS.capture(p);
    }
    public static void prepareSave(ProfileModel p) {
        migrateGraphics(p,true);
        GRAPHICS.prepareSave(p); CONTROLS.prepareSave(p);
    }
    public static void importPortable(ProfileModel p,File root) throws IOException {
        migrateGraphics(p,true);
        GRAPHICS.importPortable(p,root); CONTROLS.importPortable(p,root);
    }
    public static void importCatalog(Catalog source,File destination) throws IOException {
        if(source.version==1) GRAPHICS.importCatalog(graphicsFromLegacy(source),destination);
        CONTROLS.importCatalog(source,destination);
    }
    private static Catalog rawLegacy(File root) throws IOException {
        if(root==null)return null;
        AtomicFile f=new AtomicFile(new File(root,FILE));
        if(!f.getBaseFile().exists() && !new File(root,FILE+".bak").exists())return null;
        if(f.getBaseFile().length()>1024*1024)throw new IOException("Profiles file is too large");
        try {
            Catalog c=JSON.fromJson(new String(f.readFully(),StandardCharsets.UTF_8),Catalog.class);
            if(c==null)throw new IOException("Invalid profiles file");
            return c.version==1?c:null;
        }catch(RuntimeException e){throw new IOException("Invalid legacy profiles",e);}
    }
    private static Catalog graphicsFromLegacy(Catalog old) throws IOException {
        Catalog c=JSON.fromJson(JSON.toJson(old),Catalog.class);
        // Old catalogs contain only LCD graphics. Never adopt unexpected newer keys.
        retainLcd(c.defaults);
        if(c.profiles==null)throw new IOException("Invalid legacy profiles");
        for(Entry e:c.profiles){if(e==null)throw new IOException("Invalid legacy profile");retainLcd(e.values);}
        c.version=2; c.legacyCombined=true; GRAPHICS.validate(c); return c;
    }
    private static void retainLcd(JsonObject values) {
        if(values!=null)for(String key:new ArrayList<>(values.keySet()))
            if(!Arrays.asList(LCD_FIELDS).contains(key))values.remove(key);
    }
    private static Catalog legacyGraphics(File root) throws IOException {
        Catalog old=rawLegacy(root);return old==null?new Catalog():graphicsFromLegacy(old);
    }
    /** Persist the LCD half before a controls-only write removes it from the old file. */
    private static synchronized void preserveLegacyGraphics(File root) throws IOException {
        if(root==null || new File(root,GRAPHICS_FILE).exists() || new File(root,GRAPHICS_FILE+".bak").exists())return;
        Catalog old=rawLegacy(root);if(old!=null)GRAPHICS.write(root,graphicsFromLegacy(old));
    }
    private static void migrateGraphics(ProfileModel p,boolean existing) {
        if(p.graphicsSettings)return;
        JsonObject overrides=existing?GRAPHICS.values(p):new JsonObject();
        if(existing && p.sharedSettings) {
            try {
                Catalog c=GRAPHICS.read(root(p));
                if(c.legacyCombined || p.sharedPortable!=null) {
                    p.graphicsProfileId=p.sharedProfileId;
                    for(String key:LCD_FIELDS) {
                        overrides.remove(key);
                        if(p.sharedOverrides!=null && p.sharedOverrides.has(key))
                            overrides.add(key,p.sharedOverrides.get(key).deepCopy());
                    }
                    if(p.sharedPortable!=null) {
                        p.graphicsPortable=GRAPHICS.builtIn();
                        for(String key:LCD_FIELDS)if(p.sharedPortable.has(key))
                            p.graphicsPortable.add(key,p.sharedPortable.get(key).deepCopy());
                        p.graphicsPortableName=p.sharedPortableName;
                    }
                }
            }catch(IOException e){android.util.Log.w("SharedProfiles","Preserving stored graphics during migration",e);}
        }
        p.graphicsSettings=true; p.graphicsOverrides=overrides;
        p.graphicsSnapshot=GRAPHICS.values(p);
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
