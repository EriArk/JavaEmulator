package io.github.eriark.abyssme.symbian;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** A stopped phone snapshot. Callers serialize native shutdown, capture and promotion. */
final class PhoneBackup {
    static final String MANIFEST = "abyssme-phone.json";
    static final long MAX_INPUT = 4L << 30, MAX_EXPANDED = 8L << 30;
    private static final int MAX_FILES = 100000, MAX_MANIFEST = 32 << 20;
    private static final long RESERVE = 64L << 20;
    private final SystemFileCatalog catalog;
    private final File files;
    PhoneBackup(File files) { this.files = files; catalog = new SystemFileCatalog(new File(files,"symbian-systems")); }
    static File profiles(File files, String id) {
        File embedded = new File(files,"symbian-systems/" + id + "/profiles");
        return embedded.isDirectory() ? embedded : new File(files,"symbian-profiles/" + id);
    }
    interface Validator { void validate(File runtime, JSONObject metadata) throws Exception; }
    interface Space { long available(File folder); }
    static final class Preview {
        final JSONObject manifest;
        final long bytes;
        final String phone;
        final int count;
        Preview(JSONObject manifest) throws IOException {
            try {
                this.manifest=manifest;
                if (manifest.getInt("version") != 1 || !"AbyssME phone backup".equals(manifest.getString("format")))
                    throw new IOException("This is not a supported AbyssME phone backup");
                JSONObject set=manifest.getJSONObject("phone"); validateMetadata(set);
                phone=set.getString("model") + " / " + set.getString("firmware");
                bytes=manifest.getLong("bytes"); count=manifest.getJSONArray("files").length();
                if (bytes < 0 || bytes > MAX_EXPANDED || count < 1 || count > MAX_FILES)
                    throw new IOException("Phone backup exceeds supported limits");
                JSONArray bindings=manifest.getJSONArray("bindings");
                if(bindings.length()>10000) throw new IOException("Too many game bindings");
                for(int i=0;i<bindings.length();i++) if(!bindings.getString(i).matches("[0-9a-f]{64}"))
                    throw new IOException("Invalid game binding");
            } catch(JSONException e) { throw new IOException("Incomplete phone backup",e); }
        }
        String description() {
            return phone + "\n\n" + (bytes / (1024*1024)) + " MB unpacked. Includes phone system files, installed games, saves and game settings."
                    + "\n\nRestores a separate phone. Existing phones, games and saves are not replaced."
                    + "\n\nOnly restore backups you trust.";
        }
    }
    private static final class Item {
        final String path, hash; final long size; final boolean directory;
        File source; byte[] generated;
        Item(String path,long size,String hash,boolean directory) throws IOException {
            this.path=path; this.size=size; this.hash=hash; this.directory=directory;
            safePath(path,directory);
            if(size<0 || size>MAX_INPUT || (directory && (size!=0 || !hash.isEmpty()))
                    || (!directory && !hash.matches("[0-9a-f]{64}"))) throw new IOException("Invalid backup inventory");
            if((path.equals("runtime/config.yml") || path.equals("runtime/data/devices.yml")) && size>131072
                    || path.startsWith("profiles/") && !directory && size>2*1024*1024)
                throw new IOException("Backup settings are too large");
        }
        JSONObject json() throws JSONException { return new JSONObject().put("path",path).put("size",size).put("sha256",hash).put("directory",directory); }
    }
    private static void validateMetadata(JSONObject set) throws IOException, JSONException {
        if((set.getInt("version")!=1 && set.getInt("version")!=2) || !set.getString("id").matches("[0-9a-f]{64}")
                || !set.getString("firmware").matches("[A-Za-z0-9_-]{1,64}")
                || !set.getString("os").matches("epoc[a-z0-9]{1,16}")
                || set.getString("model").isEmpty() || set.getString("model").length()>256
                || !set.getString("romSha256").matches("[0-9a-f]{64}")
                || !set.getString("rpkgSha256").matches("(?:[0-9a-f]{64})?")) throw new IOException("Invalid phone metadata");
    }
    private static void safePath(String path,boolean dir) throws IOException {
        if(path.length()>1024 || path.contains("\\") || path.contains(":") || path.startsWith("/") || path.endsWith("/"))
            throw new IOException("Unsafe backup path");
        for(char c:path.toCharArray()) if(c<32) throw new IOException("Unsafe backup path");
        String[] parts=path.split("/",-1);
        if(parts.length>64) throw new IOException("Backup paths are too deep");
        for(String part:parts) if(part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe backup path");
        boolean allowed=path.equals("source/phone.rom") || path.equals("source/phone.rpkg")
                || path.equals("runtime/config.yml") || path.equals("runtime/data/devices.yml")
                || path.startsWith("runtime/data/drives/") || path.startsWith("runtime/data/roms/") || path.startsWith("runtime/data/j2me/")
                || path.matches("profiles/[1-9][0-9]{0,9}/(?:config|settings-trial)\\.json(?:\\.bak)?");
        if(dir) allowed|=Arrays.asList("source","runtime","runtime/data","runtime/data/drives","runtime/data/roms","runtime/data/j2me","profiles").contains(path)
                || path.matches("profiles/[1-9][0-9]{0,9}");
        if(!allowed) throw new IOException("Unsupported file in phone backup: " + path);
    }
    static Preview inspect(File archive) throws IOException {
        if(archive.length()>MAX_INPUT) throw new IOException("Phone backup exceeds 4 GB");
        try(ZipFile zip=new ZipFile(archive)) {
            ZipArchiveEntry entry=zip.getEntry(MANIFEST);
            if(entry==null || entry.isDirectory() || entry.isUnixSymlink()) throw new IOException("Select an AbyssME phone backup ZIP, not a game or phone dump");
            Preview result=new Preview(readJson(zip.getInputStream(entry),MAX_MANIFEST));
            inventory(zip,result);
            return result;
        }
    }
    private static List<Item> inventory(ZipFile zip,Preview preview) throws IOException {
        try {
            JSONArray rows=preview.manifest.getJSONArray("files");
            Map<String,Item> items=new LinkedHashMap<>(); Set<String> folded=new HashSet<>(); long total=0;
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                Item item=new Item(row.getString("path"),row.getLong("size"),row.getString("sha256"),row.getBoolean("directory"));
                if(!folded.add(item.path.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate backup path");
                items.put(item.path,item); total+=item.size;
                if(total>MAX_EXPANDED) throw new IOException("Phone backup is too large");
            }
            if(total!=preview.bytes) throw new IOException("Backup size does not match its inventory");
            for(Item item:items.values()) {
                String parent=item.path;
                while(parent.contains("/")) {
                    parent=parent.substring(0,parent.lastIndexOf('/'));
                    Item directory=items.get(parent);
                    if(directory==null || !directory.directory) throw new IOException("Missing backup directory");
                }
            }
            Set<String> seen=new HashSet<>();
            for(Enumeration<ZipArchiveEntry> entries=zip.getEntries();entries.hasMoreElements();) {
                ZipArchiveEntry entry=entries.nextElement(); String name=entry.getName();
                if(!seen.add(name) || seen.size()>MAX_FILES+1 || entry.isUnixSymlink()
                        || !zip.canReadEntryData(entry)) throw new IOException("Unsupported or duplicate backup entry");
                if(name.equals(MANIFEST)) continue;
                String path=entry.isDirectory() && name.endsWith("/") ? name.substring(0,name.length()-1) : name;
                Item item=items.get(path);
                int type=entry.getUnixMode() & 0170000;
                if(item==null || item.directory!=entry.isDirectory() || item.size!=entry.getSize()
                        || (type!=0 && type!=0100000 && type!=0040000)) throw new IOException("Backup contents do not match the inventory");
            }
            if(seen.size()!=items.size()+1) throw new IOException("Backup files are missing");
            return new ArrayList<>(items.values());
        } catch(JSONException e) { throw new IOException("Invalid backup inventory",e); }
    }
    void exportPhone(SystemFileCatalog.Entry entry,File target,String[] portableSettings,PreparedSystemArchive.Progress progress) throws Exception {
        File root=catalog.set(entry.id);
        JSONObject metadata=readJson(new FileInputStream(new File(root,"set.json")),65536); validateMetadata(metadata);
        List<Item> items=new ArrayList<>();
        collect(new File(root,"source"),"source",items,progress);
        addDirectory("runtime",items); addDirectory("runtime/data",items);
        for(String name:new String[]{"drives","roms","j2me"}) {
            File folder=new File(root,"runtime/data/"+name);
            if(folder.exists()) collect(folder,"runtime/data/"+name,items,progress);
        }
        generated("runtime/config.yml",portableSettings[0],items);
        generated("runtime/data/devices.yml",portableSettings[1],items);
        File profiles=profiles(files,entry.id);
        if(profiles.exists()) collect(profiles,"profiles",items,progress); else addDirectory("profiles",items);
        long total=0; JSONArray rows=new JSONArray();
        for(Item item:items) { total+=item.size; rows.put(item.json()); }
        if(total>MAX_EXPANDED || items.size()>MAX_FILES) throw new IOException("Phone backup exceeds supported limits");
        requireSpace(target.getParentFile(),total,File::getUsableSpace);
        JSONObject manifest=new JSONObject().put("format","AbyssME phone backup").put("version",1)
                .put("created",System.currentTimeMillis()).put("phone",metadata).put("bytes",total).put("files",rows)
                .put("bindings",new JSONArray(catalog.sourceBindings(entry)));
        byte[] header=manifest.toString().getBytes(StandardCharsets.UTF_8);
        if(header.length>MAX_MANIFEST) throw new IOException("Backup inventory is too large");
        boolean complete=false;
        try(FileOutputStream disk=new FileOutputStream(target); ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(disk))) {
            zip.setLevel(1); zip.putNextEntry(new ZipEntry(MANIFEST)); zip.write(header); zip.closeEntry();
            int done=0;
            for(Item item:items) {
                progress.update("Backing up phone: " + (++done) + "/" + items.size());
                zip.putNextEntry(new ZipEntry(item.path+(item.directory?"/":"")));
                if(!item.directory) try(InputStream input=item.generated==null ? new FileInputStream(item.source) : new ByteArrayInputStream(item.generated)) {
                    transfer(input,zip,item,progress,null);
                }
                zip.closeEntry();
                if(disk.getChannel().position()>MAX_INPUT) throw new IOException("Compressed backup exceeds 4 GB");
            }
            zip.finish(); zip.flush(); disk.getFD().sync();
            if(disk.getChannel().position()>MAX_INPUT) throw new IOException("Compressed backup exceeds 4 GB");
            complete=true;
        } finally { if(!complete) Files.deleteIfExists(target.toPath()); }
    }
    SystemFileCatalog.Entry restore(File archive,Validator validator,PreparedSystemArchive.Progress progress) throws Exception {
        return restore(archive,validator,progress,File::getUsableSpace);
    }
    SystemFileCatalog.Entry restore(File archive,Validator validator,PreparedSystemArchive.Progress progress,Space space) throws Exception {
        Preview preview=inspect(archive);
        File stage=catalog.newRestore();
        try(ZipFile zip=new ZipFile(archive)) {
            requireSpace(stage,preview.bytes,space);
            List<Item> items=inventory(zip,preview); int done=0;
            for(Item item:items) {
                progress.update("Restoring phone: " + (++done) + "/" + items.size());
                File target=new File(stage,item.path);
                if(item.directory) Files.createDirectories(target.toPath());
                else {
                    Files.createDirectories(target.getParentFile().toPath());
                    ZipArchiveEntry entry=zip.getEntry(item.path); CRC32 crc=new CRC32();
                    try(InputStream input=zip.getInputStream(entry); FileOutputStream output=new FileOutputStream(target)) {
                        transfer(input,output,item,progress,crc); output.getFD().sync();
                    }
                    if(crc.getValue()!=entry.getCrc()) throw new IOException("Damaged backup file: " + item.path);
                    if(item.path.startsWith("profiles/")) readJson(new FileInputStream(target),2*1024*1024);
                }
            }
            JSONObject metadata=new JSONObject(preview.manifest.getJSONObject("phone").toString());
            String original=metadata.getString("id");
            String id=SystemFileCatalog.digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            metadata.put("id",id).put("restoredFrom",original).put("restoredAt",System.currentTimeMillis())
                    .put("sourceBindings",preview.manifest.getJSONArray("bindings"));
            if(!SystemFileCatalog.hash(new File(stage,"source/phone.rom")).equals(metadata.getString("romSha256")))
                throw new IOException("Phone ROM identity does not match the backup");
            if(!metadata.getString("rpkgSha256").isEmpty() && !SystemFileCatalog.hash(new File(stage,"source/phone.rpkg")).equals(metadata.getString("rpkgSha256")))
                throw new IOException("Phone RPKG identity does not match the backup");
            File runtime=new File(stage,"runtime");
            if(!new File(runtime,"data/devices.yml").isFile()) throw new IOException("Phone registration is missing");
            progress.update("Checking restored phone"); validator.validate(runtime,metadata);
            if(metadata.getInt("version")==2 && (!new File(runtime,"data/drives/z/"+metadata.getString("firmware").toLowerCase(Locale.ROOT)).isDirectory()
                    || !new File(runtime,"data/roms/"+metadata.getString("firmware").toLowerCase(Locale.ROOT)+"/SYM.ROM").isFile()))
                throw new IOException("Restored system files are incomplete");
            Files.createDirectories(new File(stage,"profiles").toPath());
            try(FileOutputStream output=new FileOutputStream(new File(stage,"set.json"))) {
                output.write(metadata.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
            }
            progress.update("Adding restored phone to library");
            return catalog.commitRestore(stage,id);
        } finally { catalog.discardRestore(stage); }
    }
    private static void requireSpace(File folder,long bytes,Space space) throws IOException {
        if(space.available(folder)<bytes+RESERVE) throw new IOException("Not enough free space. Free at least " + ((bytes+RESERVE)/(1024*1024)) + " MB and try again. Existing phones are unchanged.");
    }
    private static void addDirectory(String path,List<Item> items) throws IOException { items.add(new Item(path,0,"",true)); }
    private static void generated(String path,String value,List<Item> items) throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        Item item=new Item(path,bytes.length,SystemFileCatalog.digest(bytes),false); item.generated=bytes; items.add(item);
    }
    private static void collect(File root,String prefix,List<Item> items,PreparedSystemArchive.Progress progress) throws IOException {
        if(Files.isSymbolicLink(root.toPath())) throw new IOException("Linked phone folders cannot be backed up");
        Files.walkFileTree(root.toPath(),new SimpleFileVisitor<Path>() {
            private String name(Path path) { String sub=root.toPath().relativize(path).toString().replace(File.separatorChar,'/'); return prefix+(sub.isEmpty()?"":"/"+sub); }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs) throws IOException {
                if(items.size()>=MAX_FILES) throw new IOException("Too many phone files");
                addDirectory(name(path),items); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs) throws IOException {
                if(!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Linked phone files cannot be backed up");
                if(items.size()>=MAX_FILES) throw new IOException("Too many phone files");
                progress.update("Checking phone files: " + items.size());
                MessageDigest digest=SystemFileCatalog.sha256();
                try(InputStream input=Files.newInputStream(path)) {
                    byte[] buffer=new byte[65536];
                    long checked=0;
                    for(int n;(n=input.read(buffer))!=-1;) {
                        if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Backup cancelled");
                        digest.update(buffer,0,n); checked+=n;
                        if(checked>=8*1024*1024) {progress.update("Checking phone files: " + items.size());checked=0;}
                    }
                }
                Item item=new Item(name(path),attrs.size(),SystemFileCatalog.hex(digest.digest()),false); item.source=path.toFile(); items.add(item);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    private static void transfer(InputStream input,OutputStream output,Item item,PreparedSystemArchive.Progress progress,CRC32 crc) throws IOException {
        MessageDigest hash=SystemFileCatalog.sha256(); byte[] buffer=new byte[65536]; long total=0,reported=0;
        for(int n;(n=input.read(buffer))!=-1;) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Transfer cancelled");
            total+=n; if(total>item.size) throw new IOException("Backup file size changed");
            output.write(buffer,0,n); hash.update(buffer,0,n); if(crc!=null) crc.update(buffer,0,n);
            if(total-reported>=8*1024*1024) { progress.update("Copying phone data: " + total/(1024*1024)+" MB"); reported=total; }
        }
        if(total!=item.size || !SystemFileCatalog.hex(hash.digest()).equals(item.hash)) throw new IOException("Backup verification failed: " + item.path);
    }
    private static JSONObject readJson(InputStream input,int max) throws IOException {
        try(InputStream in=input; ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int total=0;
            for(int n;(n=in.read(buffer))!=-1;) { total+=n; if(total>max) throw new IOException("Backup metadata is too large"); output.write(buffer,0,n); }
            try { return new JSONObject(output.toString("UTF-8")); }
            catch(JSONException e) { throw new IOException("Invalid backup metadata",e); }
        }
    }
}
