/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import io.github.eriark.abyssme.peer.*;
import org.junit.Assume;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Explicit opt-in: two dedicated accounts, real authenticated DM signaling and
 * native peer traffic. A prearranged admission fixture, not the production room UI
 * or a Java game. Does not change friendships, publish rooms or export credentials. */
public class FluxerLivePeerDeviceTest {
    @Test public void authenticatedSignalsThenDirectEncryptedPackets() throws Exception {
        run(false);
    }
    @Test public void authenticatedSignalsThenBluetoothGameApis() throws Exception {
        run(true);
    }
    private void run(boolean bluetooth) throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();
        String role=args.getString("peerRole",""), remote=args.getString("peerAccount","");
        String room=args.getString("peerRoom",""), attempt=args.getString("peerAttempt","");
        Assume.assumeTrue("Explicit two-device fixture required",(role.equals("host")||role.equals("guest"))
                &&LobbyDirectory.id(remote)&&!room.isEmpty()&&!attempt.isEmpty());
        Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        String credential=new FluxerCredentialStore(app).read();assertNotNull("Sign in normally first",credential);
        FluxerAuth.Reply me=FluxerHttpTransport.exchange("GET","/v1/users/@me",null,credential);
        assertTrue("Account verification",me.ok());
        assertTrue("Dedicated test account only",text(me.body,"username").startsWith("abyssmetest"));
        String self=text(me.body,"id");assertNotEquals(self,remote);
        FluxerAuth.Reply relationships=FluxerHttpTransport.exchange("GET","/v1/users/@me/relationships",null,credential);
        assertTrue(relationships.ok());boolean friend=false;
        for(JsonElement element:relationships.data.getAsJsonArray()){
            JsonObject r=element.getAsJsonObject();
            if(remote.equals(text(r,"id")))friend=number(r,"type")==1
                    &&text(object(r,"user"),"username").startsWith("abyssmetest");
        }
        assertTrue("Designated existing test friend required",friend);
        boolean host=role.equals("host");
        PeerBinding binding=new PeerBinding(UUID.fromString(room),1,UUID.fromString(attempt),"java",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","bt-fixture","1",
                host?Arrays.asList(self,remote):Arrays.asList(remote,self));
        if(bluetooth){LiveBluetoothScenario.run(binding,self,remote,credential,host);return;}
        ScheduledThreadPoolExecutor owner=new ScheduledThreadPoolExecutor(1);
        owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        FluxerHttpTransport http=new FluxerHttpTransport(owner);
        AtomicReference<Throwable> error=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        SecurePeer[] peer={null};LobbyMailbox[] mailbox={null};
        int[] received={0},sent={0},signalFragments={0};boolean[] ack={false},ackSent={false};long[] finishedAt={0};
        Runnable fail=()->{if(peer[0]!=null)peer[0].close();done.countDown();};
        try{
            owner.submit(()->{
                mailbox[0]=new LobbyMailbox(http,SystemClock::elapsedRealtime,new LobbyMailbox.Listener(){
                    public void message(String author,String content){
                        if(content.startsWith(PeerSignals.PREFIX))report(role+": signaling envelope received");
                        if(peer[0]!=null)peer[0].receiveSignal(author,content);
                    }
                    public void changed(){}
                    public void failed(String p,String reason){error.compareAndSet(null,new AssertionError(reason));fail.run();}
                    public void expired(){error.compareAndSet(null,new AssertionError("Test account sign-in expired"));fail.run();}
                });
                mailbox[0].configure(credential,self);mailbox[0].watch(Collections.singletonList(remote));mailbox[0].active(true);
                try{
                    peer[0]=new SecurePeer(binding,self,null,new SecurePeer.Listener(){
                        public boolean signal(String recipient,List<String> fragments){
                            report(role+": queue "+fragments.size()+" signaling fragment(s)");
                            signalFragments[0]+=fragments.size();return mailbox[0].sendPeerSignals(recipient,fragments);
                        }
                        public void state(String phase){report(role+": "+phase);}
                        public void connected(){
                            Bundle status=new Bundle();status.putString("secure_peer",role+" connected");
                            InstrumentationRegistry.getInstrumentation().sendStatus(2,status);
                        }
                        public void message(byte[] bytes){
                            if(bytes.length==1&&bytes[0]==127){ack[0]=true;return;}
                            if(bytes.length!=1024||received[0]>=100||!Arrays.equals(packet(received[0],!host),bytes)){
                                error.compareAndSet(null,new AssertionError("Direct payload corrupt, duplicate or out of order"));fail.run();return;
                            }
                            received[0]++;
                        }
                        public void closed(String reason){
                            if(received[0]==100&&ack[0])done.countDown();
                            else{error.compareAndSet(null,new AssertionError(reason));fail.run();}
                        }
                    });
                }catch(Throwable t){error.compareAndSet(null,t);fail.run();}
            }).get(10,TimeUnit.SECONDS);
            owner.scheduleWithFixedDelay(()->{
                if(done.getCount()==0)return;
                try{
                    mailbox[0].tick();peer[0].poll();
                    if(peer[0].isConnected()){
                        if(sent[0]<100)peer[0].send(packet(sent[0]++,host));
                        if(received[0]==100&&!ackSent[0]){peer[0].send(new byte[]{127});ackSent[0]=true;}
                        if(received[0]==100&&ack[0]){
                            if(finishedAt[0]==0)finishedAt[0]=SystemClock.elapsedRealtime()+2000;
                            if(SystemClock.elapsedRealtime()>=finishedAt[0])done.countDown();
                        }
                    }
                }catch(Throwable t){error.compareAndSet(null,t);fail.run();}
            },0,20,TimeUnit.MILLISECONDS);
            assertTrue("Two-device secure peer timeout",done.await(150,TimeUnit.SECONDS));
            if(error.get()!=null)throw new AssertionError("Live peer check failed",error.get());
            assertEquals(100,received[0]);assertEquals(100,sent[0]);assertTrue(ack[0]);
            Bundle report=new Bundle();report.putString("direct_peer_result",role+": 100 ordered binary packets each way; "+signalFragments[0]+" signaling fragments");
            InstrumentationRegistry.getInstrumentation().sendStatus(2,report);
        }finally{
            done.countDown();
            owner.submit(()->{
                if(mailbox[0]!=null)mailbox[0].active(false);if(peer[0]!=null)peer[0].close();
                http.closeWhenIdle(owner::shutdown);
            }).get(10,TimeUnit.SECONDS);
            if(!owner.awaitTermination(40,TimeUnit.SECONDS)){owner.shutdownNow();fail("HTTP owner did not drain");}
        }
    }
    private static byte[] packet(int sequence,boolean host){
        byte[] bytes=new byte[1024];new Random(sequence+(host?1000:2000)).nextBytes(bytes);
        ByteBuffer.wrap(bytes).putInt(sequence);return bytes;
    }
    private static void report(String phase){
        Bundle status=new Bundle();status.putString("secure_peer_phase",phase);
        InstrumentationRegistry.getInstrumentation().sendStatus(2,status);
    }
}
