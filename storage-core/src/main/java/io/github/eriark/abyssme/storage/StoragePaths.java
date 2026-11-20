/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.storage;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Cross-process storage pointer. Never falls back when a selected volume disappears. */
public final class StoragePaths {
    private StoragePaths() { }
    public static File pointer(Context c) { return new File(c.getFilesDir(), "library-storage.json"); }
    public static File journal(Context c) { return new File(c.getFilesDir(), "library-move.json"); }
    public static JSONObject read(File file) {
        if (!file.exists() && !new File(file + ".bak").exists()) return new JSONObject();
        try { return new JSONObject(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException("Library storage record could not be read. No data was reset.", e); }
    }
    public static void write(File file, JSONObject value) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(value.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (Exception e) { if(out != null) atomic.failWrite(out); throw new IOException("Could not save storage selection", e); }
    }
    public static File primary(Context c) {
        File f = c.getExternalFilesDir(null); return f == null ? c.getFilesDir() : f;
    }
    public static File library(Context c) {
        String root = read(pointer(c)).optString("root");
        return root.isEmpty() ? primary(c) : new File(root, "library");
    }
    public static File phones(Context c) {
        String root = read(pointer(c)).optString("root");
        return root.isEmpty() ? c.getFilesDir() : new File(root, "phones");
    }
    public static String resolve(Context c,String path) {
        if(path==null)return null;
        JSONObject pointer=read(pointer(c));String current=library(c).getPath();
        if(path.equals(current)||path.startsWith(current+"/"))return path;
        org.json.JSONArray previous=pointer.optJSONArray("previousLibraries");String match="";
        if(previous!=null)for(int i=0;i<previous.length();i++) {
            String old=previous.optString(i);
            if(old.length()>match.length()&&(path.equals(old)||path.startsWith(old+"/")))match=old;
        }
        return match.isEmpty()?path:current+path.substring(match.length());
    }
    public static android.net.Uri resolve(Context c,android.net.Uri uri) {
        if(uri==null)return null;
        if("file".equals(uri.getScheme()))return android.net.Uri.fromFile(new File(resolve(c,uri.getPath())));
        return uri.getScheme()==null?android.net.Uri.parse(resolve(c,uri.toString())):uri;
    }
    public static boolean available(Context c) {
        try {
            JSONObject p=read(pointer(c)); String root=p.optString("root");
            return root.isEmpty() ? library(c).isDirectory() :
                new File(root,".verified").isFile() && new File(root,"library").isDirectory()
                && new File(root,"phones").isDirectory() && new File(root).canWrite();
        } catch(RuntimeException e) { return false; }
    }
    public static boolean moving(Context c) { return journal(c).exists() || new File(journal(c)+".bak").exists(); }
    public static void requireAvailable(Context c) {
        if (!available(c)) throw new IllegalStateException("Library storage is unavailable. Reinsert the selected SD card and reopen AbyssME. Your library has not been reset.");
    }
}
