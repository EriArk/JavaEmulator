// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import com.github.eka2l1.emu.Emulator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;

/** Copies an EKA2L1 data snapshot into independent, validated AbyssME phones. */
final class Eka2l1Transfer implements AutoCloseable {
    static final long LIMIT = 8L << 30;
    private static final long RESERVE = 64L << 20;
    private static final int MAX_FILES = 100000;
    static final class Phone {
        final String firmware, model, os, registration;
        final boolean isolated;
        Phone(String[] rows, int at) {
            firmware=rows[at]; model=rows[at+1]; os=rows[at+2];
            isolated=Boolean.parseBoolean(rows[at+3]); registration=rows[at+4];
        }
        String label() { return model + " / " + firmware; }
    }
    final File stage;
    final List<Phone> phones = new ArrayList<>();
    private final Map<String,File> paths = new TreeMap<>();
    private final Map<String,String> spellings = new HashMap<>();
    private long bytes;
    private int checkedFiles;
    private String prefix;
    private final PreparedSystemArchive.Progress progress;

    Eka2l1Transfer(File cache, PreparedSystemArchive.Progress progress) throws IOException {
        stage=Files.createTempDirectory(cache.toPath(), "eka-transfer-").toFile(); this.progress=progress;
    }
    private Eka2l1Transfer(File stage, PreparedSystemArchive.Progress progress, boolean retained) {
        this.stage=stage; this.progress=progress;
    }
    /** Retain the original entry list, including explicit versus implicit ZIP directories. */
    void retain(File manifest) throws Exception {
        JSONArray entries=new JSONArray();
        for (File file:paths.values()) {
            progress.update(SystemProgress.CHECK_FILES,checkedFiles);
            String relative=stage.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');
            entries.put(new JSONObject().put("path",relative).put("directory",file.isDirectory())
                    .put("size",file.isDirectory()?0:file.length())
                    .put("hash",file.isDirectory()?"":hashFile(file)));
        }
        JSONObject data=new JSONObject().put("version",1).put("stage",stage.getName()).put("entries",entries);
        byte[] encoded=data.toString().getBytes(StandardCharsets.UTF_8);
        if(encoded.length>64L*1024*1024) throw new SystemFailure(SystemFailure.Code.METADATA_LIMIT);
        File pending=new File(manifest.getParentFile(),manifest.getName()+".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(pending)) {
                out.write(encoded); out.getFD().sync();
            }
            Files.move(pending.toPath(),manifest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending.toPath()); }
    }
    static Eka2l1Transfer reopen(File manifest,PreparedSystemArchive.Progress progress) throws Exception {
        if(!manifest.isFile() || manifest.length()>64L*1024*1024) throw new SystemFailure(SystemFailure.Code.METADATA_LIMIT);
        JSONObject data=new JSONObject(new String(Files.readAllBytes(manifest.toPath()),StandardCharsets.UTF_8));
        String name=data.getString("stage");
        if(data.getInt("version")!=1 || !name.matches("eka-transfer-[A-Za-z0-9-]+"))
            throw new SystemFailure(SystemFailure.Code.PHONE_METADATA);
        File stage=new File(manifest.getParentFile(),name);
        if(!stage.isDirectory() || Files.isSymbolicLink(stage.toPath())) throw new SystemFailure(SystemFailure.Code.MISSING_FILES);
        Eka2l1Transfer transfer=new Eka2l1Transfer(stage,progress,true);
        JSONArray entries=data.getJSONArray("entries");
        if(entries.length()>MAX_FILES) throw new SystemFailure(SystemFailure.Code.METADATA_LIMIT);
        for(int i=0;i<entries.length();i++) {
            progress.update(SystemProgress.CHECK_FILES,transfer.checkedFiles);
            JSONObject entry=entries.getJSONObject(i); String path=entry.getString("path");
            if(path.length()>1024 || path.split("/",-1).length>64) throw new SystemFailure(SystemFailure.Code.UNSAFE_PATH);
            for(String part:path.split("/",-1)) safe(part);
            File file=new File(stage,path);
            if(!file.getCanonicalFile().toPath().equals(stage.getCanonicalFile().toPath().resolve(path)))
                throw new SystemFailure(SystemFailure.Code.LINKED_FILES);
            String key=path.toLowerCase(Locale.ROOT);
            if(transfer.paths.put(key,file)!=null) throw new SystemFailure(SystemFailure.Code.DUPLICATE_PATH);
            boolean directory=entry.getBoolean("directory");
            if(directory ? !file.isDirectory() : !file.isFile() || file.length()!=entry.getLong("size"))
                throw new SystemFailure(SystemFailure.Code.FILE_SIZE_CHANGED);
            if(!directory) {
                transfer.bytes+=file.length();
                if(transfer.bytes>LIMIT) throw new SystemFailure(SystemFailure.Code.EXPANDED_LIMIT);
                if(!transfer.hashFile(file).equals(entry.getString("hash")))
                    throw new SystemFailure(SystemFailure.Code.VERIFICATION_FAILED,path);
            }
        }
        transfer.inspect(); return transfer;
    }
    void readZip(File archive) throws IOException {
        if (archive.length()>PhoneBackup.MAX_INPUT) throw new SystemFailure(SystemFailure.Code.INPUT_LIMIT);
        try (ZipFile zip=new ZipFile(archive)) {
            for (Enumeration<ZipArchiveEntry> entries=zip.getEntries(); entries.hasMoreElements();) {
                progress.update(SystemProgress.COPY_DATA,bytes/(1024*1024));
                ZipArchiveEntry entry=entries.nextElement();
                int type=entry.getUnixMode() & 0170000;
                if (entry.isUnixSymlink() || !zip.canReadEntryData(entry)
                        || (type!=0 && type!=0100000 && type!=0040000))
                    throw new SystemFailure(SystemFailure.Code.UNSUPPORTED_ENTRY);
                String path=entry.getName();
                if (entry.isDirectory() && path.endsWith("/")) path=path.substring(0,path.length()-1);
                File target=target(path,entry.isDirectory());
                if (!entry.isDirectory()) try (InputStream in=zip.getInputStream(entry)) {
                    CRC32 crc=new CRC32(); copy(in,target,entry.getSize(),crc);
                    if (crc.getValue()!=entry.getCrc()) throw new SystemFailure(SystemFailure.Code.DAMAGED_FILE,path);
                }
            }
        }
        inspect();
    }
    void readDirectory(File root) throws IOException {
        directory(root,"",0); inspect();
    }
    private void directory(File source,String relative,int depth) throws IOException {
        progress.update(SystemProgress.COPY_DATA,bytes/(1024*1024));
        if (depth>64 || Files.isSymbolicLink(source.toPath())) throw new SystemFailure(SystemFailure.Code.LINKED_FILES);
        if (source.isDirectory()) {
            if (!relative.isEmpty()) target(relative,true);
            File[] children=source.listFiles();
            if (children==null) throw new SystemFailure(SystemFailure.Code.MISSING_FILES);
            for (File child:children) directory(child,relative.isEmpty()?child.getName():relative+"/"+child.getName(),depth+1);
        } else {
            if (!source.isFile()) throw new SystemFailure(SystemFailure.Code.UNSUPPORTED_FILE,relative);
            long size=source.length(), modified=source.lastModified();
            try (InputStream in=new FileInputStream(source)) { copy(in,target(relative,false),size,null); }
            if (size!=source.length() || modified!=source.lastModified()) throw new SystemFailure(SystemFailure.Code.FILE_SIZE_CHANGED);
        }
    }
    void readFolder(Context context,Uri tree) throws IOException {
        try {
            if (!DocumentsContract.isTreeUri(tree)) throw new SystemFailure(SystemFailure.Code.MISSING_REGISTRATION);
            folder(context,tree,DocumentsContract.getTreeDocumentId(tree),"",new HashSet<>(),0);
            inspect();
        } catch (SecurityException | IllegalArgumentException error) {
            throw new SystemFailure(SystemFailure.Code.MISSING_FILES,error);
        }
    }
    private void folder(Context context,Uri tree,String id,String path,Set<String> visited,int depth) throws IOException {
        progress.update(SystemProgress.COPY_DATA,bytes/(1024*1024));
        if (depth>64 || !visited.add(id)) throw new SystemFailure(SystemFailure.Code.LINKED_FOLDERS);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        try (Cursor cursor=context.getContentResolver().query(children,columns,null,null,null)) {
            if (cursor==null) throw new SystemFailure(SystemFailure.Code.MISSING_FILES);
            while (cursor.moveToNext()) {
                String childId=cursor.getString(0), name=cursor.getString(1);
                safe(name);
                String relative=path.isEmpty()?name:path+"/"+name;
                boolean dir=DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2));
                File target=target(relative,dir);
                if (dir) folder(context,tree,childId,relative,visited,depth+1);
                else {
                    Uri child=DocumentsContract.buildDocumentUriUsingTree(tree,childId);
                    if (cursor.isNull(3)) throw new SystemFailure(SystemFailure.Code.FILE_SIZE_CHANGED);
                    long size=cursor.getLong(3), modified=cursor.getLong(4);
                    try (InputStream in=context.getContentResolver().openInputStream(child)) { copy(in,target,size,null); }
                    try (Cursor after=context.getContentResolver().query(child,columns,null,null,null)) {
                        if (after==null || !after.moveToFirst() || after.isNull(3)
                                || size!=after.getLong(3) || modified!=after.getLong(4))
                            throw new SystemFailure(SystemFailure.Code.FILE_SIZE_CHANGED);
                    }
                }
            }
        }
    }
    private static void safe(String part) throws IOException {
        if (part==null || part.isEmpty() || part.equals(".") || part.equals("..") || part.contains("/") || part.contains("\\") || part.contains(":"))
            throw new SystemFailure(SystemFailure.Code.UNSAFE_PATH);
        for (char ch:part.toCharArray()) if (ch<32) throw new SystemFailure(SystemFailure.Code.UNSAFE_PATH);
    }
    private File target(String relative,boolean directory) throws IOException {
        if (relative.length()>1024 || relative.split("/",-1).length>64) throw new SystemFailure(SystemFailure.Code.PATH_DEPTH);
        for (String part:relative.split("/",-1)) safe(part);
        String key=relative.toLowerCase(Locale.ROOT);
        if (paths.size()>=MAX_FILES || paths.containsKey(key)) throw new SystemFailure(SystemFailure.Code.DUPLICATE_PATH);
        String parent="";
        for (String part:relative.split("/")) {
            parent=parent.isEmpty()?part:parent+"/"+part;
            String folded=parent.toLowerCase(Locale.ROOT);
            String existing=spellings.putIfAbsent(folded,parent);
            if (existing!=null && !existing.equals(parent)) throw new SystemFailure(SystemFailure.Code.ARCHIVE_PATH_COLLISION,relative);
            if (!parent.equals(relative) && paths.containsKey(folded) && !paths.get(folded).isDirectory())
                throw new SystemFailure(SystemFailure.Code.ARCHIVE_PATH_COLLISION,relative);
        }
        File result=new File(stage,relative);
        if (!directory && result.exists()) throw new SystemFailure(SystemFailure.Code.ARCHIVE_PATH_COLLISION,relative);
        paths.put(key,result);
        Files.createDirectories((directory?result:result.getParentFile()).toPath());
        return result;
    }
    private void copy(InputStream input,File target,long size,CRC32 crc) throws IOException {
        if (input==null || size<0 || size>LIMIT || bytes>LIMIT-size) throw new SystemFailure(SystemFailure.Code.EXPANDED_LIMIT);
        if (stage.getUsableSpace()<size+RESERVE) throw new SystemFailure(SystemFailure.Code.SPACE_REQUIRED,(size+RESERVE)/(1024*1024));
        long total=0;
        try (FileOutputStream out=new FileOutputStream(target)) {
            byte[] buffer=new byte[65536];
            for (int n;(n=input.read(buffer))!=-1;) {
                total+=n; bytes+=n;
                if (total>size || bytes>LIMIT) throw new SystemFailure(SystemFailure.Code.FILE_SIZE_CHANGED);
                progress.update(SystemProgress.COPY_DATA,bytes/(1024*1024));
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Transfer cancelled");
                out.write(buffer,0,n); if (crc!=null) crc.update(buffer,0,n);
            }
            if (total!=size) throw new SystemFailure(SystemFailure.Code.FILE_SIZE_CHANGED);
            out.getFD().sync();
        }
    }
    private void inspect() throws IOException {
        List<String> registrations=new ArrayList<>();
        for (String path:paths.keySet()) if (path.equals("devices.yml") || path.endsWith("/devices.yml")) registrations.add(path);
        if (registrations.size()!=1) throw new SystemFailure(SystemFailure.Code.MISSING_REGISTRATION);
        String registration=registrations.get(0); prefix=registration.substring(0,registration.length()-"devices.yml".length());
        File file=paths.get(registration);
        if (!file.isFile() || file.length()>131072) throw new SystemFailure(SystemFailure.Code.METADATA_LIMIT);
        String[] rows=Emulator.inspectEka2l1Phones(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        if (rows==null || rows.length==0 || rows.length%5!=0 || rows.length/5>256)
            throw new SystemFailure(SystemFailure.Code.MISSING_REGISTRATION);
        for (int at=0;at<rows.length;at+=5) {
            Phone phone=new Phone(rows,at);
            if (!phone.os.matches("epoc[a-z0-9]{1,16}")) throw new SystemFailure(SystemFailure.Code.PHONE_METADATA);
            phones.add(phone);
        }
    }
    private Map<String,File> payload(Phone phone) throws IOException {
        Map<String,File> result=new TreeMap<>(); String firmware=phone.firmware.toLowerCase(Locale.ROOT);
        File rom=paths.get(prefix+"roms/"+firmware+"/sym.rom");
        if (rom==null || !rom.isFile() || rom.length()==0) throw new SystemFailure(SystemFailure.Code.ROM_MISSING);
        result.put("roms/"+firmware+"/SYM.ROM",rom);
        String sourceDrive="drives/"+(phone.isolated?firmware+"/":"");
        for (Map.Entry<String,File> entry:paths.entrySet()) {
            String key=entry.getKey(); if (!key.startsWith(prefix)) continue;
            String relative=key.substring(prefix.length());
            String target=null;
            if (relative.startsWith("drives/z/"+firmware+"/")) target=relative;
            else if (relative.startsWith(sourceDrive)) {
                String tail=relative.substring(sourceDrive.length());
                if (tail.matches("[a-y](?:/.*)?")) target="drives/"+firmware+"/"+tail;
            } else if (relative.startsWith("j2me/")) target=relative;
            if (target!=null) {
                // Preserve guest filename case. Only EKA2L1's structural prefixes are normalized.
                String[] targetParts=target.split("/");
                String[] original=stage.toPath().relativize(entry.getValue().toPath()).toString().replace(File.separatorChar,'/').split("/");
                int structural=target.startsWith("drives/")?3:1;
                int suffix=targetParts.length-structural;
                for(int i=0;i<suffix;i++) targetParts[structural+i]=original[original.length-suffix+i];
                result.put(String.join("/",targetParts),entry.getValue());
            }
        }
        return result;
    }
    long size(Phone phone) throws IOException { long total=0; for(File file:payload(phone).values()) if(file.isFile()) total+=file.length(); return total; }

    private String[] portableSettings(Phone phone) throws IOException {
        File settings=Files.createTempDirectory(stage.toPath(),"portable-").toFile();
        try {
            Files.createDirectories(new File(settings,"data").toPath());
            Files.write(new File(settings,"data/devices.yml").toPath(),phone.registration.getBytes(StandardCharsets.UTF_8));
            // A complete EKA2L1 folder has config.yml alongside data/. A data-only export may omit it.
            String parent=prefix.endsWith("data/")?prefix.substring(0,prefix.length()-5):prefix;
            File config=paths.get(parent+"config.yml");
            if (config!=null) {
                if (!config.isFile() || config.length()>131072) throw new SystemFailure(SystemFailure.Code.SETTINGS_LIMIT);
                Files.copy(config.toPath(),new File(settings,"config.yml").toPath());
            }
            return Emulator.portablePhoneSettings(settings.getAbsolutePath());
        } finally { remove(settings); }
    }

    SystemFileCatalog.Entry install(File files,Phone phone,PhoneBackup.Validator validator) throws Exception {
        if (!phones.contains(phone)) throw new SystemFailure(SystemFailure.Code.PHONE_METADATA);
        Map<String,File> payload=payload(phone); Map<String,String> hashes=new TreeMap<>();
        String[] settings=portableSettings(phone);
        MessageDigest digest=SystemFileCatalog.sha256();
        digest.update(settings[0].getBytes(StandardCharsets.UTF_8));
        digest.update(settings[1].getBytes(StandardCharsets.UTF_8));
        long total=0;
        for(Map.Entry<String,File> item:payload.entrySet()) {
            progress.update(SystemProgress.CHECK_FILES,hashes.size());
            String hash=item.getValue().isFile()?hashFile(item.getValue()):"directory";
            hashes.put(item.getKey(),hash); if(item.getValue().isFile()) total+=item.getValue().length();
            digest.update((item.getKey()+"\0"+hash+"\n").getBytes(StandardCharsets.UTF_8));
        }
        String fingerprint=SystemFileCatalog.hex(digest.digest());
        String id=SystemFileCatalog.digest(("eka2l1:"+fingerprint).getBytes(StandardCharsets.UTF_8));
        SystemFileCatalog catalog=new SystemFileCatalog(new File(files,"symbian-systems"));
        if(catalog.set(id).exists()) return catalog.read(id);
        File pending=catalog.newRestore();
        try {
            total+=payload.get("roms/"+phone.firmware.toLowerCase(Locale.ROOT)+"/SYM.ROM").length();
            if(pending.getUsableSpace()<total+RESERVE) throw new SystemFailure(SystemFailure.Code.SPACE_REQUIRED,(total+RESERVE)/(1024*1024));
            File runtime=new File(pending,"runtime"), data=new File(runtime,"data");
            Files.createDirectories(data.toPath());
            long copied=0;
            for(Map.Entry<String,File> item:payload.entrySet()) {
                progress.update(SystemProgress.COPY_DATA,copied/(1024*1024));
                File target=new File(data,item.getKey());
                if(item.getValue().isDirectory()) Files.createDirectories(target.toPath());
                else {
                    Files.createDirectories(target.getParentFile().toPath());
                    copyPhoneFile(item.getValue(),target,copied);
                    copied+=item.getValue().length();
                    if(!hashFile(target).equals(hashes.get(item.getKey())))
                        throw new SystemFailure(SystemFailure.Code.VERIFICATION_FAILED,item.getKey());
                }
            }
            String firmware=phone.firmware.toLowerCase(Locale.ROOT);
            File rom=new File(data,"roms/"+firmware+"/SYM.ROM");
            File sourceRom=new File(pending,"source/phone.rom"); Files.createDirectories(sourceRom.getParentFile().toPath());
            copyPhoneFile(rom,sourceRom,copied);
            Files.write(new File(data,"devices.yml").toPath(),settings[1].getBytes(StandardCharsets.UTF_8));
            // Host paths, network configuration, scripts and external drive mounts never migrate.
            Files.write(new File(runtime,"config.yml").toPath(),settings[0].getBytes(StandardCharsets.UTF_8));
            JSONObject metadata=new JSONObject().put("version",1).put("id",id).put("model",phone.model)
                    .put("firmware",phone.firmware).put("os",phone.os).put("romSha256",hashFile(sourceRom))
                    .put("rpkgSha256","").put("restoredFrom","EKA2L1").put("sourceBindings",new JSONArray()).put("ekaSourceHash",fingerprint);
            validator.validate(runtime,metadata);
            Files.createDirectories(new File(pending,"profiles").toPath());
            Files.write(new File(pending,"set.json").toPath(),metadata.toString().getBytes(StandardCharsets.UTF_8));
            progress.update(SystemProgress.ADD_PHONE);
            return catalog.commitRestore(pending,id);
        } finally {
            // A catalog commit is final even if best-effort staging cleanup later fails.
            try { catalog.discardRestore(pending); }
            catch(IOException error) { if(!catalog.set(id).isDirectory()) throw error; }
        }
    }
    private String hashFile(File file) throws IOException {
        MessageDigest hash=SystemFileCatalog.sha256();
        progress.update(SystemProgress.CHECK_FILES,checkedFiles);
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];
            for(int n;(n=in.read(buffer))!=-1;) {
                progress.update(SystemProgress.CHECK_FILES,checkedFiles);
                hash.update(buffer,0,n);
            }
        }
        checkedFiles++; return SystemFileCatalog.hex(hash.digest());
    }
    private void copyPhoneFile(File source,File target,long copied) throws IOException {
        try(InputStream in=new FileInputStream(source); FileOutputStream out=new FileOutputStream(target)) {
            byte[] buffer=new byte[65536];
            for(int n;(n=in.read(buffer))!=-1;) {
                progress.update(SystemProgress.COPY_DATA,copied/(1024*1024));
                out.write(buffer,0,n); copied+=n;
            }
            out.getFD().sync();
        }
    }
    @Override public void close() throws IOException { remove(stage); }
    static void remove(File root) throws IOException {
        if(!root.exists()) return;
        if(Files.isSymbolicLink(root.toPath())) throw new SystemFailure(SystemFailure.Code.LINKED_FILES);
        File[] children=root.listFiles(); if(children!=null) for(File child:children) remove(child);
        Files.delete(root.toPath());
    }
}
