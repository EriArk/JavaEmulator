package io.github.eriark.abyssme.symbian;

import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Serialized by the host worker. A failed native operation must not erase the launcher reference. */
final class GameRemoval {
    interface Backend {
        String[] inspect(long uid) throws Exception;
        String[] apps() throws Exception;
        void remove(long uid, long owner, int index) throws Exception;
        void reload() throws Exception;
        boolean complete(Plan plan) throws Exception;
        void publish(String[] apps, Set<Long> removed) throws Exception;
    }
    static final class Plan {
        final long uid, owner;
        final int index;
        final String[] rows;
        final Set<Long> affected = new LinkedHashSet<>();
        Plan(long uid, String[] rows) throws IOException {
            this.uid=uid; this.rows=rows==null?new String[0]:rows.clone();
            try {
                if(this.rows.length<8 || this.rows.length>20000) throw new IllegalArgumentException();
                for(String value:this.rows) if(value==null || value.length()>2048) throw new IllegalArgumentException();
                owner=identity(this.rows[0]); index=Integer.parseInt(this.rows[1]);
                if(index<0) throw new IllegalArgumentException();
                int count=Integer.parseInt(this.rows[4]);
                if(count<1 || count>2048 || 5+count*2>=this.rows.length) throw new IllegalArgumentException();
                for(int i=5;i<5+count*2;i+=2) if(!affected.add(identity(this.rows[i]))) throw new IllegalArgumentException();
                if(!affected.contains(uid)) throw new IllegalArgumentException();
            } catch(RuntimeException error) { throw new SystemFailure(SystemFailure.Code.REMOVAL_PLAN,error); }
        }
        String description(String phone) {
            StringBuilder text=new StringBuilder("Phone: ").append(phone).append("\nPackage: ").append(rows[2]).append(" ").append(rows[3]);
            text.append("\n\nApplications to remove:");
            for(int i=5;i<5+affected.size()*2;i+=2) text.append("\n").append(rows[i+1].isEmpty()?rows[i]:rows[i+1]);
            return text.append("\n\nRemoves the installed package and its private data. Game saves may be deleted; some legacy saves may remain."
                    + "\n\nThere is no automatic undo. Back up this phone first if you want to keep its progress.").toString();
        }
    }
    private final AtomicFile journal;
    private final Backend backend;
    GameRemoval(File directory,String system,Backend backend) throws IOException {
        this.journal=new AtomicFile(journal(directory,system)); this.backend=backend;
    }
    static File journal(File directory,String system) throws IOException {
        if(system==null || !system.matches("[0-9a-f]{64}")) throw new SystemFailure(SystemFailure.Code.REMOVAL_PHONE);
        return new File(directory,system+"-removal.json");
    }
    static boolean pending(File directory,String system) throws IOException {
        File file=journal(directory,system);
        return file.exists() || new File(file+".bak").exists();
    }
    boolean pending() { return journal.getBaseFile().exists() || new File(journal.getBaseFile()+".bak").exists(); }
    Plan inspect(long uid) throws Exception {
        Plan plan=new Plan(uid,backend.inspect(uid));
        if(pending()) {
            JSONObject saved=read(); Plan previous=plan(saved);
            if(previous.uid!=uid || !Arrays.equals(previous.rows,plan.rows))
                throw new SystemFailure(SystemFailure.Code.REMOVAL_PENDING);
        }
        return plan;
    }
    void remove(Plan confirmed) throws Exception {
        Plan current=inspect(confirmed.uid);
        if(!Arrays.equals(current.rows,confirmed.rows)) throw new SystemFailure(SystemFailure.Code.PACKAGE_CHANGED);
        String[] before=pending()?strings(read().getJSONArray("before")):backend.apps();
        if(!apps(before).containsAll(current.affected)) throw new SystemFailure(SystemFailure.Code.LIBRARY_CHANGED);
        if(!pending()) write(new JSONObject().put("version",1).put("uid",current.uid)
                .put("plan",new JSONArray(Arrays.asList(current.rows))).put("before",new JSONArray(Arrays.asList(before))));
        backend.remove(current.uid,current.owner,current.index);
        backend.reload();
        if(!recover()) throw new SystemFailure(SystemFailure.Code.REMOVAL_INCOMPLETE);
    }
    /** Reconcile an interrupted operation only when every intended app is absent and all others remain. */
    boolean recover() throws Exception {
        if(!pending()) return false;
        JSONObject saved=read(); Plan plan=plan(saved);
        Set<Long> expected=apps(strings(saved.getJSONArray("before")));
        String[] fresh=backend.apps(); Set<Long> actual=apps(fresh);
        for(long uid:plan.affected) if(actual.contains(uid)) return false;
        if(!backend.complete(plan)) return false;
        expected.removeAll(plan.affected);
        if(!actual.containsAll(expected)) throw new SystemFailure(SystemFailure.Code.OTHER_APPS_MISSING);
        backend.publish(fresh,plan.affected);
        journal.delete();
        if(pending()) throw new SystemFailure(SystemFailure.Code.RECOVERY_NOT_CLEARED);
        return true;
    }
    private Plan plan(JSONObject saved) throws Exception {
        if(saved.getInt("version")!=1) throw new SystemFailure(SystemFailure.Code.RECOVERY_UNSUPPORTED);
        return new Plan(saved.getLong("uid"),strings(saved.getJSONArray("plan")));
    }
    static Set<Long> apps(String[] rows) throws IOException {
        if(rows==null || rows.length%3!=0 || rows.length>6144) throw new SystemFailure(SystemFailure.Code.APPS_INVALID);
        Set<Long> result=new LinkedHashSet<>();
        try { for(int i=0;i<rows.length;i+=3) if(!result.add(identity(rows[i]))) throw new IllegalArgumentException(); }
        catch(RuntimeException error) { throw new SystemFailure(SystemFailure.Code.APP_ID_INVALID,error); }
        return result;
    }
    private static long identity(String value) {
        long uid=Long.parseLong(value); if(uid<=0 || uid>0xffffffffL) throw new IllegalArgumentException(); return uid;
    }
    private static String[] strings(JSONArray array) throws JSONException, IOException {
        if(array.length()>20000) throw new SystemFailure(SystemFailure.Code.RECOVERY_LIMIT);
        String[] rows=new String[array.length()]; for(int i=0;i<rows.length;i++) rows[i]=array.getString(i); return rows;
    }
    private JSONObject read() throws IOException, JSONException {
        try(InputStream in=journal.openRead(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int total=0;
            for(int n;(n=in.read(buffer))!=-1;) { total+=n; if(total>2*1024*1024) throw new SystemFailure(SystemFailure.Code.RECOVERY_LIMIT); out.write(buffer,0,n); }
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private void write(JSONObject value) throws IOException {
        byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>2*1024*1024) throw new SystemFailure(SystemFailure.Code.RECOVERY_WRITE_LIMIT);
        File parent=journal.getBaseFile().getParentFile();
        if(!parent.isDirectory() && !parent.mkdirs()) throw new SystemFailure(SystemFailure.Code.RECOVERY_SAVE_FAILED);
        FileOutputStream out=null;
        try { out=journal.startWrite(); out.write(bytes); journal.finishWrite(out); }
        catch(IOException error) { if(out!=null) journal.failWrite(out); throw error; }
    }
}
