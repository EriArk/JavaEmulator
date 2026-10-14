package io.github.eriark.abyssme.symbian;

import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** One durable batch, containing only flat app-owned copies, never paths from the archive. */
final class ArchiveBatch {
    static final long LIMIT=1024L*1024*1024;
    enum Role { GAME, COMPONENT, PLATFORM }
    enum State { READY, ACTIVE, WAITING, DONE, FAILED, SKIPPED }
    static final class Item {
        int id;
        String name, title, hash="", problem="", detail="", system="";
        GameImport.Kind kind;
        Role role=Role.GAME;
        State state=State.READY;
        boolean selected=true, canSave;
        String label() { return title==null ? name : title; }
    }
    static final class Batch {
        String id, name;
        boolean started;
        final List<Item> items=new ArrayList<>();
        final Map<GameImport.Kind,String> phones=new EnumMap<>(GameImport.Kind.class);
        String owner(Item item) { return id+":"+item.id; }
        Item owned(String owner) {
            for(Item item:items) if(owner(item).equals(owner)) return item;
            return null;
        }
    }
    private final File root;
    ArchiveBatch(File root) { this.root=root; }
    private File current() { return new File(root,"current"); }
    boolean exists() { return current().exists(); }
    File payload(Item item) { return new File(current(),"item-"+item.id); }
    static boolean candidate(String path) {
        String name=path.toLowerCase(Locale.ROOT);
        return name.endsWith(".sis") || name.endsWith(".sisx") || name.endsWith(".n-gage") || name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z");
    }
    static boolean multiple(File source) throws IOException {
        if(!GameImport.isZip(source)) return false;
        int packages=0, count=0; boolean card=false, system=false;
        try(ZipFile zip=new ZipFile(source)) {
            for(Enumeration<? extends ZipEntry> entries=zip.entries();entries.hasMoreElements();) {
                ZipEntry e=entries.nextElement(); if(++count>20000) throw new IOException("Too many archive entries");
                String path=safe(e.getName()).toLowerCase(Locale.ROOT);
                if(e.isDirectory()) continue;
                card|=path.matches("(?:.*/)?system/apps/[^/]+/[^/]+\\.app");
                system|=path.endsWith(".rom") || path.endsWith(".rpkg");
                if(candidate(path)) packages++;
            }
        }
        return packages>1 && !card && !system;
    }
    void beginZip(File source,String name,PreparedSystemArchive.Progress progress) throws Exception {
        if(source.length()>LIMIT) throw new IOException("Archive exceeds 1 GB");
        File staging=start();
        try(ZipFile zip=new ZipFile(source)) {
            List<ZipEntry> packages=new ArrayList<>(); Set<String> names=new HashSet<>(); long expanded=0; int count=0;
            for(Enumeration<? extends ZipEntry> entries=zip.entries();entries.hasMoreElements();) {
                ZipEntry e=entries.nextElement(); if(++count>20000) throw new IOException("Too many archive entries");
                String path=safe(e.getName()).toLowerCase(Locale.ROOT);
                if(!names.add(path)) throw new IOException("Duplicate path in archive");
                if(e.isDirectory()) continue;
                if(path.endsWith(".rom") || path.endsWith(".rpkg") || path.matches("(?:.*/)?system/apps/[^/]+/[^/]+\\.app"))
                    throw new IOException("Import phone files or original N-Gage card archives separately; they must stay together.");
                if(e.getSize()<0 || e.getSize()>LIMIT-expanded) throw new IOException("Expanded archive exceeds 1 GB");
                expanded+=e.getSize();
                if(candidate(path)) packages.add(e);
            }
            if(packages.isEmpty() || packages.size()>256) throw new IOException("Select an archive with 1 to 256 Symbian installers or game ZIPs");
            space(expanded);
            Batch batch=newBatch(name); Map<Integer,SisMetadata> metadata=new HashMap<>();
            for(ZipEntry e:packages) {
                progress.update("Inspecting "+(batch.items.size()+1)+" / "+packages.size()+": "+clean(e.getName(),160));
                Item item=item(batch,e.getName()); File target=new File(staging,"item-"+item.id);
                try(InputStream in=zip.getInputStream(e)) { copy(in,target,e.getSize(),e.getCrc()); }
                catch(IOException error) {
                    if(error instanceof InterruptedIOException) throw error;
                    item.problem=clean(error.getMessage(),400); item.selected=false;
                    Files.deleteIfExists(target.toPath()); continue;
                }
                inspect(item,target,metadata);
            }
            suggest(batch,metadata); write(staging,batch); Files.move(staging.toPath(),current().toPath(),StandardCopyOption.ATOMIC_MOVE);
        } finally { remove(staging); }
    }
    void beginFiles(List<File> inputs,List<String> names,PreparedSystemArchive.Progress progress) throws Exception {
        if(inputs.isEmpty() || inputs.size()>256 || inputs.size()!=names.size()) throw new IOException("Select 1 to 256 files");
        long total=0; for(File f:inputs) { if(f.length()>LIMIT-total) throw new IOException("Selected files exceed 1 GB"); total+=f.length(); }
        File staging=start();
        try {
            space(total); Batch batch=newBatch("Selected files"); Map<Integer,SisMetadata> metadata=new HashMap<>();
            for(int i=0;i<inputs.size();i++) {
                Item item=item(batch,names.get(i)); progress.update("Inspecting "+(i+1)+" / "+inputs.size()+": "+item.name);
                File target=new File(staging,"item-"+item.id);
                try(InputStream in=new FileInputStream(inputs.get(i))) { copy(in,target,inputs.get(i).length(),-1); }
                inspect(item,target,metadata);
            }
            suggest(batch,metadata); write(staging,batch); Files.move(staging.toPath(),current().toPath(),StandardCopyOption.ATOMIC_MOVE);
        } finally { remove(staging); }
    }
    private File start() throws IOException {
        if(exists()) throw new IOException("Finish or remove the current archive queue first");
        Files.createDirectories(root.toPath()); recover(); return Files.createTempDirectory(root.toPath(),"staging-").toFile();
    }
    private void space(long size) throws IOException { if(root.getUsableSpace()<size+64L*1024*1024) throw new IOException("Not enough space to keep this import queue"); }
    private static Batch newBatch(String name) {
        Batch batch=new Batch(); batch.id=UUID.randomUUID().toString(); batch.name=clean(name,160); return batch;
    }
    private static Item item(Batch batch,String name) {
        Item item=new Item(); item.id=batch.items.size(); item.name=clean(name,512); batch.items.add(item); return item;
    }
    private static void inspect(Item item,File file,Map<Integer,SisMetadata> metadata) throws IOException {
        try(GameImport game=GameImport.inspect(file)) {
            item.hash=SystemFileCatalog.hash(file); item.kind=game.kind;
            if(game.kind==GameImport.Kind.SIS_MODERN) {
                SisMetadata info=SisMetadata.read(game.file); item.title=info.name; item.canSave=info.installType==0; metadata.put(item.id,info);
                if(game.isPlatform()) item.role=Role.PLATFORM;
            }
        } catch(IOException e) {
            if(e instanceof InterruptedIOException) throw e;
            item.problem=clean(e.getMessage(),400); item.selected=false;
        }
    }
    private static void suggest(Batch batch,Map<Integer,SisMetadata> metadata) {
        Set<String> hashes=new HashSet<>();
        for(Item item:batch.items) {
            if(!item.problem.isEmpty()) continue;
            if(!hashes.add(item.hash)) { item.selected=false; item.detail="Duplicate file"; }
            SisMetadata provided=metadata.get(item.id);
            if(provided==null || !item.canSave || item.role==Role.PLATFORM) continue;
            for(Map.Entry<Integer,SisMetadata> other:metadata.entrySet()) if(other.getKey()!=item.id)
                for(SisMetadata.Need need:other.getValue().needs)
                    if(need.uid==provided.uid && need.accepts(provided.version)) item.role=Role.COMPONENT;
        }
        Map<Long,Item> games=new HashMap<>();
        for(Item item:batch.items) {
            SisMetadata info=metadata.get(item.id);
            if(info==null || item.role!=Role.GAME || !item.problem.isEmpty()) continue;
            Item other=games.putIfAbsent(info.uid,item);
            if(other!=null) { item.selected=false; item.detail="Alternative version of "+other.label(); }
        }
    }
    static String clean(String text,int max) {
        String value=text==null ? "Import" : text.replaceAll("[\\p{Cntrl}\\p{Cf}]"," ").trim();
        return value.length()>max ? value.substring(0,max) : value;
    }
    static String safe(String name) throws IOException {
        String path=name.replace('\\','/');
        if(path.length()>2048 || path.startsWith("/") || path.contains(":") || !path.equals(clean(path,2048))) throw new IOException("Unsafe archive path");
        for(String part:path.split("/")) if(part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe archive path");
        return path;
    }
    private static void copy(InputStream in,File target,long expected,long expectedCrc) throws IOException {
        try(FileOutputStream out=new FileOutputStream(target)) {
            byte[] buffer=new byte[65536]; long size=0; CRC32 crc=new CRC32();
            for(int n;(n=in.read(buffer))!=-1;) {
                if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import paused");
                if((size+=n)>LIMIT || size>expected) throw new IOException("Expanded file exceeds its declared size");
                out.write(buffer,0,n); crc.update(buffer,0,n);
            }
            if(size!=expected || (expectedCrc>=0 && expectedCrc!=crc.getValue())) throw new IOException("Damaged archive entry");
            out.getFD().sync();
        }
    }
    void verify(Item item) throws IOException {
        File file=payload(item);
        if(!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS) || !item.hash.equals(SystemFileCatalog.hash(file)))
            throw new IOException("Retained file is missing or changed. Import its original file again.");
    }
    Batch read() throws IOException {
        if(!exists()) return null;
        try(InputStream in=new AtomicFile(new File(current(),"batch.json")).openRead()) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192];
            for(int n;(n=in.read(buffer))!=-1;) { if(out.size()+n>1024*1024) throw new IOException("Import queue record is too large"); out.write(buffer,0,n); }
            JSONObject json=new JSONObject(out.toString("UTF-8")); Batch batch=new Batch();
            if(json.getInt("version")!=1) throw new IOException("Unsupported import queue");
            batch.id=UUID.fromString(json.getString("id")).toString(); batch.name=json.getString("name"); batch.started=json.getBoolean("started");
            JSONObject phones=json.optJSONObject("phones");
            if(phones!=null)for(Iterator<String> keys=phones.keys();keys.hasNext();) {
                String key=keys.next(),value=phones.getString(key);
                if(!value.matches("[0-9a-f]{64}"))throw new IOException("Invalid archive phone preference");
                batch.phones.put(GameImport.Kind.valueOf(key),value);
            }
            JSONArray items=json.getJSONArray("items"); if(items.length()>256) throw new IOException("Too many queued files");
            for(int i=0;i<items.length();i++) {
                JSONObject row=items.getJSONObject(i); Item item=item(batch,row.getString("name"));
                item.title=row.optString("title",null); item.hash=row.getString("hash"); item.problem=row.getString("problem"); item.detail=row.getString("detail");
                item.role=Role.valueOf(row.getString("role")); item.state=State.valueOf(row.getString("state"));
                item.selected=row.getBoolean("selected"); item.canSave=row.getBoolean("canSave");
                item.system=row.optString("system","");
                if(!item.system.isEmpty() && !item.system.matches("[0-9a-f]{64}")) throw new IOException("Invalid queued phone");
                item.kind=row.has("kind") ? GameImport.Kind.valueOf(row.getString("kind")) : null;
                if(item.problem.isEmpty() && (!item.hash.matches("[0-9a-f]{64}") || item.kind==null)) throw new IOException("Invalid queued file identity");
            }
            return batch;
        } catch(JSONException | IllegalArgumentException e) { throw new IOException("Cannot read the import queue",e); }
    }
    void save(Batch batch) throws IOException {
        Batch actual=read(); if(actual==null || !actual.id.equals(batch.id)) throw new IOException("Import queue changed");
        write(current(),batch);
    }
    private static void write(File folder,Batch batch) throws IOException {
        AtomicFile file=new AtomicFile(new File(folder,"batch.json")); FileOutputStream out=null;
        try {
            JSONArray rows=new JSONArray();
            for(Item item:batch.items) rows.put(new JSONObject().put("name",item.name).put("title",item.title).put("hash",item.hash)
                    .put("problem",item.problem).put("detail",item.detail).put("kind",item.kind==null ? null : item.kind.name())
                    .put("role",item.role.name()).put("state",item.state.name()).put("selected",item.selected).put("canSave",item.canSave).put("system",item.system));
            JSONObject phones=new JSONObject(); for(Map.Entry<GameImport.Kind,String> entry:batch.phones.entrySet())phones.put(entry.getKey().name(),entry.getValue());
            JSONObject json=new JSONObject().put("version",1).put("id",batch.id).put("name",batch.name).put("started",batch.started).put("items",rows).put("phones",phones);
            out=file.startWrite(); out.write(json.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out);
        } catch(Exception e) { if(out!=null) file.failWrite(out); throw new IOException("Cannot save the import queue",e); }
    }
    static Batch snapshot(Batch source) {
        Batch copy=new Batch(); copy.id=source.id; copy.name=source.name; copy.started=source.started;
        copy.phones.putAll(source.phones);
        for(Item item:source.items) {
            Item row=item(copy,item.name); row.title=item.title; row.hash=item.hash; row.problem=item.problem; row.detail=item.detail;
            row.kind=item.kind; row.role=item.role; row.state=item.state; row.selected=item.selected; row.canSave=item.canSave; row.system=item.system;
        }
        return copy;
    }
    void recover() throws IOException {
        CompressedGameArchive.recover(current());
        File[] files=root.listFiles(f->f.getName().startsWith("staging-")); if(files!=null) for(File file:files) remove(file);
    }
    void discard() throws IOException {
        if(!exists()) return; File discarded=new File(root,"staging-discard-"+UUID.randomUUID());
        Files.move(current().toPath(),discarded.toPath()); remove(discarded);
    }
    private void remove(File target) throws IOException {
        Path path=target.toPath().toAbsolutePath().normalize();
        if(!path.getParent().equals(root.toPath().toAbsolutePath().normalize())) throw new IOException("Invalid queue cleanup path");
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a) throws IOException { Files.delete(p); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path p,IOException e) throws IOException { if(e!=null) throw e; Files.delete(p); return FileVisitResult.CONTINUE; }
        });
    }
}
