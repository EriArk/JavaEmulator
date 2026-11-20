/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.storage;

import android.content.Context;
import io.github.eriark.abyssme.storage.*;
import org.json.*;
import java.io.*;
import java.util.UUID;

/** Durable two-phase move: unchanged source until every destination file is verified. */
public final class LibraryMove {
    public interface Status { void update(String text); }
    private final Context context;
    public LibraryMove(Context context) { this.context=context; }
    private static void add(JSONArray rows,File from,File to) throws Exception {
        File[] children=from.listFiles(); if(children==null)throw new IOException("Storage unavailable: "+from);
        for(File f:children) {
            if(f.getName().equals(".abyss-storage"))continue;
            rows.put(new JSONObject().put("from",f.getPath()).put("to",new File(to,f.getName()).getPath()));
        }
    }
    public JSONObject plan(File volume) throws Exception {
        StoragePaths.requireAvailable(context);
        if(StoragePaths.library(context).equals(context.getFilesDir()))throw new IOException("Primary storage is unavailable. Reconnect it and retry.");
        File target=new File(volume,".abyss-storage/"+UUID.randomUUID());
        JSONArray rows=new JSONArray();
        add(rows,StoragePaths.library(context),new File(target,"library"));
        File phones=StoragePaths.phones(context);
        File[] children=phones.listFiles(); if(children==null)throw new IOException("Phone storage unavailable");
        for(File f:children)if(f.getName().equals("symbian")||f.getName().startsWith("symbian-"))
            rows.put(new JSONObject().put("from",f.getPath()).put("to",new File(target,"phones/"+f.getName()).getPath()));
        long total=0;for(int i=0;i<rows.length();i++)total=Math.addExact(total,VerifiedTree.size(new File(rows.getJSONObject(i).getString("from"))));
        long required=requiredSpace(rows,volume);
        return new JSONObject().put("root",target.getPath()).put("volume",volume.getPath()).put("rows",rows).put("bytes",total).put("requiredBytes",required);
    }
    private long requiredSpace(JSONArray rows,File volume) throws Exception {
        long block=new android.os.StatFs(volume.getPath()).getBlockSizeLong(),total=32L*1024*1024;
        for(int i=0;i<rows.length();i++)total=Math.addExact(total,VerifiedTree.allocatedSize(new File(rows.getJSONObject(i).getString("from")),block));
        return total;
    }
    private interface Job { void run() throws Exception; }
    private void exclusive(Job job) throws Exception {
        try (RandomAccessFile file=new RandomAccessFile(new File(context.getFilesDir(),"library-move.lock"),"rw")) {
            java.nio.channels.FileLock lock;
            try {lock=file.getChannel().tryLock();}catch(java.nio.channels.OverlappingFileLockException e){throw new IOException("A library move is already running");}
            if(lock==null)throw new IOException("A library move is already running");
            try{job.run();}finally{if(lock.isValid())lock.release();}
        }
    }
    public interface StopWriters { void stop() throws Exception; }
    public void beginQuiescent(JSONObject plan,StopWriters stop,Status status) throws Exception {
        exclusive(()->{
            if(StoragePaths.moving(context))throw new IOException("An earlier move needs recovery");
            StoragePaths.write(StoragePaths.journal(context),plan);
            stop.stop();
            JSONObject fresh=plan(new File(plan.getString("volume")));
            StoragePaths.write(StoragePaths.journal(context),fresh);
            runLocked(fresh,status);
        });
    }
    public void begin(JSONObject plan,Status status) throws Exception {
        exclusive(()->{
            if(StoragePaths.moving(context))throw new IOException("An earlier move needs recovery");
            StoragePaths.write(StoragePaths.journal(context),plan);
            runLocked(plan,status);
        });
    }
    public void run(JSONObject plan,Status status) throws Exception {exclusive(()->runLocked(plan,status));}
    private void runLocked(JSONObject plan,Status status) throws Exception {
        String root=plan.getString("root");File target=new File(root),volume=new File(plan.getString("volume"));
        boolean committed=root.equals(StoragePaths.read(StoragePaths.pointer(context)).optString("root"));
        JSONArray rows=plan.getJSONArray("rows");
        if(!committed) {
            if(!volume.isDirectory()||!volume.canWrite())throw new IOException("Destination storage is unavailable. Reinsert the SD card or keep the old location.");
            if(target.exists())VerifiedTree.discard(target);
            long total=0;for(int i=0;i<rows.length();i++)total=Math.addExact(total,VerifiedTree.size(new File(rows.getJSONObject(i).getString("from"))));
            long required=requiredSpace(rows,volume);
            if(volume.getUsableSpace()<required)throw new IOException("Not enough free space. Required: "+required/1048576+" MiB");
            if(!new File(target,"library").mkdirs()||!new File(target,"phones").mkdirs())throw new IOException("Cannot create destination");
            final long bytes=total;final long[] done={0},last={0};
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                VerifiedTree.copy(new File(row.getString("from")),new File(row.getString("to")),n->{
                    done[0]+=n;long now=System.nanoTime();if(now-last[0]>250000000L){status.update("Copying "+done[0]/1048576+" / "+bytes/1048576+" MiB");last[0]=now;}
                });
            }
            status.update("Verifying all copied files…");
            done[0]=0;last[0]=0;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                VerifiedTree.verify(new File(row.getString("from")),new File(row.getString("to")),n->{
                    done[0]+=n;long now=System.nanoTime();if(now-last[0]>250000000L){status.update("Verifying "+done[0]/1048576+" / "+bytes/1048576+" MiB");last[0]=now;}
                });
            }
            try(FileOutputStream marker=new FileOutputStream(new File(target,".verified"))){marker.write(1);marker.getFD().sync();}
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Move cancelled");
            JSONObject previous=StoragePaths.read(StoragePaths.pointer(context));
            JSONArray aliases=previous.optJSONArray("previousLibraries");if(aliases==null)aliases=new JSONArray();
            aliases.put(StoragePaths.library(context).getPath());
            StoragePaths.write(StoragePaths.pointer(context),new JSONObject().put("root",root).put("volume",volume.getPath()).put("previousLibraries",aliases));
        }
        StoragePaths.requireAvailable(context);
        status.update("Move verified. Removing old copies…");
        final long[] removed={0},lastCleanup={0};
        for(int i=0;i<rows.length();i++) {
            if(!StoragePaths.available(context))throw new IOException("Destination removed. Old copies retained; reinsert it to finish.");
            JSONObject row=rows.getJSONObject(i);VerifiedTree.deleteVerified(new File(row.getString("from")),new File(row.getString("to")),n->{
                removed[0]+=n;long now=System.nanoTime();if(now-lastCleanup[0]>250000000L){status.update("Removing verified old copies: "+removed[0]/1048576+" MiB");lastCleanup[0]=now;}
            });
        }
        new android.util.AtomicFile(StoragePaths.journal(context)).delete();
    }
    public void cancelUncommitted() throws Exception {exclusive(this::cancelLocked);}
    private void cancelLocked() throws Exception {
        JSONObject j=StoragePaths.read(StoragePaths.journal(context));if(j.length()==0)return;
        if(j.getString("root").equals(StoragePaths.read(StoragePaths.pointer(context)).optString("root")))
            throw new IOException("The move is already verified. Reinsert its destination and finish cleanup.");
        File target=new File(j.getString("root"));if(target.exists())VerifiedTree.discard(target);
        new android.util.AtomicFile(StoragePaths.journal(context)).delete();
    }
}
