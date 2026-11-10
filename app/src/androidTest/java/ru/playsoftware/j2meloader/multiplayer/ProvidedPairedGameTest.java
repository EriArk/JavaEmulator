/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.ActivityManager;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.dx.command.dexer.Main;
import io.github.eriark.abyssme.peer.PeerBinding;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipFile;
import javax.bluetooth.VirtualBluetooth;
import ru.playsoftware.j2meloader.catalog.SourceHints;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Two dedicated emulators, local signaling only. Synthetic admission; real game/peer/IPC.
 * Manual captures establish gameplay; packet counts and process checks alone do not. */
public class ProvidedPairedGameTest {
    private static final String HOST="1000000000000001",GUEST="1000000000000002";
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File output;
    @Test public void gameDisconnectThenFreshEncryptedLaunch() throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();
        assumeTrue(args.containsKey("pairedGame")); assertTrue(context.getPackageName().endsWith(".debug"));
        String name=args.getString("pairedGame"),role=args.getString("pairedRole");
        assertEquals(new File(name).getName(),name); assertTrue("host".equals(role)||"guest".equals(role));
        boolean host=role.equals("host"); String self=host?HOST:GUEST,remote=host?GUEST:HOST;
        UUID room=UUID.fromString(args.getString("pairedRoom"));
        int port=Integer.parseInt(args.getString("pairedSignalPort")); assertTrue(port>=1024&&port<=65535);
        assertEquals("Do not replace another game process",0,pid());
        File source=new File(context.getExternalFilesDir(null),"sweep/java/"+name); assertTrue(source.isFile());
        String id="paired-"+UUID.randomUUID();
        File game=new File(Config.getAppDir(),id),profileDir=new File(Config.getConfigsDir(),id),saves=new File(Config.getDataDir(),id);
        output=new File(context.getExternalFilesDir(null),"paired-game-"+room+"-"+role); assertTrue(output.mkdirs());
        assertTrue(game.mkdirs());assertTrue(profileDir.mkdirs());
        try {
            try(ZipFile zip=new ZipFile(source);InputStream in=zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                Files.copy(in,new File(game,Config.MIDLET_MANIFEST_FILE).toPath());
            }
            Files.copy(source.toPath(),new File(game,Config.MIDLET_RES_FILE).toPath()); SourceHints.write(game,name);
            Main.main(new String[]{"--no-optimize","--core-library","--output="+new File(game,Config.MIDLET_DEX_FILE),source.getPath()});
            ProfileModel profile=new ProfileModel(profileDir);
            profile.screenWidth=Integer.parseInt(args.getString("pairedWidth","240"));
            profile.screenHeight=Integer.parseInt(args.getString("pairedHeight","320"));
            profile.showKeyboard=false; profile.touchInput=true; assertTrue(ProfilesManager.saveConfig(profile));
            InstalledNetworkGame installed=NetworkGameCatalog.verify(new File(Config.getAppDir()),new NetworkGameCatalog.Entry(game,name));
            try(Signals signals=new Signals(port,role,room.toString())) {
                for(int round=1;round<=2;round++) runRound(signals,room,round,self,remote,host,installed,name);
            }
        } finally {
            kill(); FileUtils.deleteDirectory(game);FileUtils.deleteDirectory(profileDir);FileUtils.deleteDirectory(saves);
        }
    }
    private void runRound(Signals signals,UUID room,int round,String self,String remote,boolean host,
                          InstalledNetworkGame installed,String title) throws Exception {
        PeerBinding binding=new PeerBinding(room,round,UUID.nameUUIDFromBytes((room+":"+round).getBytes(StandardCharsets.UTF_8)),
                "java",installed.game.contentHash,"bluetooth","1",Arrays.asList(HOST,GUEST));
        GameNetworkLease lease=new GameNetworkLease(binding,self,installed,true);
        ScheduledExecutorService owner=Executors.newSingleThreadScheduledExecutor();
        BluetoothPeerSession[] session={null}; AtomicReference<Throwable> error=new AtomicReference<>();
        AtomicReference<String> closed=new AtomicReference<>();AtomicBoolean ready=new AtomicBoolean(),peerReady=new AtomicBoolean(),peerAttached=new AtomicBoolean(),peerEnded=new AtomicBoolean();
        AtomicInteger sent=new AtomicInteger(),received=new AtomicInteger();
        try {
            owner.submit(()->{
                try {
                    PeerPacketPort pipe=new PeerPacketPort(){
                        public void receive(byte[] b)throws IOException{lease.receive(b);received.incrementAndGet();}
                        public byte[] pollOutbound()throws IOException{byte[] b=lease.pollOutbound();if(b!=null)sent.incrementAndGet();return b;}
                        public void close(){lease.close();}
                    };
                    session[0]=new BluetoothPeerSession(binding,self,null,pipe,new BluetoothPeerSession.Listener(){
                        public boolean signal(String recipient,List<String> fragments){
                            try{for(String f:fragments)signals.send(round,"signal",f);return true;}catch(Exception e){error.set(e);return false;}
                        }
                        public void prepared(VirtualBluetooth ignored){ready.set(true);try{signals.send(round,"prepared","");}catch(Exception e){error.set(e);}}
                        public void closed(String reason){closed.set(reason);}
                    });
                }catch(Throwable e){error.set(e);}
            }).get(10,TimeUnit.SECONDS);
            owner.scheduleWithFixedDelay(()->{
                try {
                    if(signals.failure.get()!=null)throw new IOException("Local fixture signaling ended",signals.failure.get());
                    JSONObject message;
                    while((message=signals.incoming.peek())!=null) {
                        int generation=message.getInt("round"); if(generation>round)break;
                        signals.incoming.remove(); if(generation<round)continue;
                        switch(message.getString("kind")) {
                            case "signal":session[0].receive(remote,message.getString("content"));break;
                            case "prepared":peerReady.set(true);break;
                            case "attached":peerAttached.set(true);break;
                            case "ended":peerEnded.set(true);break;
                        }
                    }
                    if(session[0]!=null)session[0].tick();
                }catch(Throwable e){error.compareAndSet(null,e);}
            },0,10,TimeUnit.MILLISECONDS);
            await(()->ready.get()&&peerReady.get(),error,45000,"Both encrypted peers prepared");
            context.startActivity(GameNetworkService.offer(context,title,lease).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(lease::adapterReady,error,30000,"Local game adapter attached");
            signals.send(round,"attached","");await(peerAttached::get,error,30000,"Other game adapter attached");
            lease.release();SystemClock.sleep(5000);assertTrue(pid()>0);
            capture("round-"+round+"-startup");report("round "+round+" gameplay window; host gate round-"+round+".continue");
            File gate=new File(output,"round-"+round+".continue");
            if(host){
                await(gate::isFile,error,900000,"Manual host gameplay gate");assertTrue(gate.delete());
                capture("round-"+round+"-before-disconnect");assertTrue("Real game traffic required",sent.get()>0&&received.get()>0);
                assertNull("Peer must remain connected until the requested disconnect",closed.get());
                assertTrue("Game lease must remain attached until the requested disconnect",lease.attached());
                assertTrue(owner.submit(()->session[0].isPrepared()).get(5,TimeUnit.SECONDS));
                kill();
            }
            await(()->closed.get()!=null,error,host?30000:930000,"Peer closure after host game death");
            assertFalse("Lease revoked",lease.attached());SystemClock.sleep(1000);capture("round-"+round+"-closed");kill();
            signals.send(round,"ended","");await(peerEnded::get,error,30000,"Both ended before next binding");
            JSONObject result=new JSONObject().put("round",round).put("sentPackets",sent.get()).put("receivedPackets",received.get())
                    .put("closure",closed.get()).put("attempt",binding.attempt).put("gameHash",installed.game.contentHash)
                    .put("scope","Synthetic local admission/signaling; manual screenshots establish real gameplay separately");
            Files.write(new File(output,"round-"+round+".json").toPath(),result.toString(2).getBytes(StandardCharsets.UTF_8));
            report("round "+round+" closed; packets="+sent.get()+"/"+received.get());
        } finally {
            kill();lease.close();
            owner.submit(()->{if(session[0]!=null)session[0].close();}).get(5,TimeUnit.SECONDS);
            owner.shutdownNow();assertTrue(owner.awaitTermination(5,TimeUnit.SECONDS));
        }
    }
    private interface Check{boolean ok();}
    private static void await(Check condition,AtomicReference<Throwable> error,long timeout,String what)throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(!condition.ok()&&SystemClock.elapsedRealtime()<end){if(error.get()!=null)throw new AssertionError(what,error.get());Thread.sleep(100);}
        if(error.get()!=null)throw new AssertionError(what,error.get());assertTrue(what,condition.ok());
    }
    private int pid(){List<ActivityManager.RunningAppProcessInfo> list=context.getSystemService(ActivityManager.class).getRunningAppProcesses();
        if(list!=null)for(ActivityManager.RunningAppProcessInfo p:list)if(p.uid==android.os.Process.myUid()&&p.processName.equals(context.getPackageName()+":midlet"))return p.pid;return 0;}
    private void kill()throws Exception{int pid=pid();if(pid>0)android.os.Process.killProcess(pid);long end=SystemClock.elapsedRealtime()+5000;while(pid()>0&&SystemClock.elapsedRealtime()<end)Thread.sleep(50);assertEquals(0,pid());}
    private void capture(String name)throws Exception{Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(OutputStream out=new FileOutputStream(new File(output,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
    private static void report(String message){Bundle status=new Bundle();status.putString("stream",message+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(0,status);}
    private static final class Signals implements Closeable {
        final Socket socket=new Socket();final BufferedWriter out;final Thread reader;
        final ConcurrentLinkedQueue<JSONObject> incoming=new ConcurrentLinkedQueue<>();final AtomicReference<Throwable> failure=new AtomicReference<>();
        Signals(int port,String role,String room)throws Exception{
            // Android emulator's fixed host alias; no provider account, invitation or public room.
            socket.connect(new InetSocketAddress("10.0.2.2",port),10000);socket.setTcpNoDelay(true);
            out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            write(new JSONObject().put("role",role).put("room",room).toString());
            reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))){
                String line;while((line=in.readLine())!=null){if(line.length()>65536||incoming.size()>512)throw new IOException("Fixture signal bounds");incoming.add(new JSONObject(line));}
                if(!socket.isClosed())throw new EOFException("Fixture relay closed");
            }catch(Throwable e){if(!socket.isClosed())failure.set(e);}},"paired-fixture-signals");reader.start();
        }
        synchronized void write(String line)throws IOException{out.write(line);out.newLine();out.flush();}
        void send(int round,String kind,String content)throws Exception{write(new JSONObject().put("round",round).put("kind",kind).put("content",content).toString());}
        public void close()throws IOException{socket.close();try{reader.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}if(reader.isAlive())throw new IOException("Fixture reader did not stop");}
    }
}
