/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.peer;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real native ICE/DTLS/SCTP, two endpoints on one Android device. No accounts. */
public class SecurePeerDeviceTest {
    private static final String A="1563512156446527490", B="1563519321118879744";
    private static PeerBinding binding() {
        return new PeerBinding(UUID.randomUUID(),1,UUID.randomUUID(),"java",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","bt","1",Arrays.asList(A,B));
    }
    private static class Sink implements SecurePeer.Listener {
        final List<String> signal=new ArrayList<>();
        final List<byte[]> data=new ArrayList<>();
        int connected,closed;
        public boolean signal(String remote,List<String> parts){signal.addAll(parts);return true;}
        public void connected(){connected++;}
        public void message(byte[] bytes){data.add(bytes);}
        public void closed(String reason){closed++;}
    }
    private static void pump(SecurePeer a,SecurePeer b,Sink sa,Sink sb)throws Exception{
        a.poll();b.poll();
        for(String s:new ArrayList<>(sa.signal))b.receiveSignal(A,s);sa.signal.clear();
        for(String s:new ArrayList<>(sb.signal))a.receiveSignal(B,s);sb.signal.clear();
        Thread.sleep(5);
    }
    private static void connect(SecurePeer a,SecurePeer b,Sink sa,Sink sb)throws Exception{
        long until=System.nanoTime()+15000000000L;
        while(!(a.isConnected()&&b.isConnected())&&System.nanoTime()<until)pump(a,b,sa,sb);
        assertEquals(0,sa.closed);assertEquals(0,sb.closed);
        assertTrue("Host handshake",a.isConnected());assertTrue("Guest handshake",b.isConnected());
    }
    @Test public void encryptedOrderedBinaryRoundTripAndClose()throws Exception{
        Sink sa=new Sink(),sb=new Sink();PeerBinding binding=binding();
        try(SecurePeer a=new SecurePeer(binding,A,null,sa);SecurePeer b=new SecurePeer(binding,B,null,sb)){
            assertThrows(IOException.class,()->a.send(new byte[]{1}));
            connect(a,b,sa,sb);
            for(int i=0;i<100;i++){
                byte[] bytes=new byte[i==99?16383:672];Arrays.fill(bytes,(byte)i);a.send(bytes);b.send(bytes);
                pump(a,b,sa,sb);
            }
            long until=System.nanoTime()+10000000000L;
            while((sa.data.size()<100||sb.data.size()<100)&&System.nanoTime()<until)pump(a,b,sa,sb);
            assertEquals(100,sa.data.size());assertEquals(100,sb.data.size());
            for(int i=0;i<100;i++){
                byte[] expected=new byte[i==99?16383:672];Arrays.fill(expected,(byte)i);
                assertArrayEquals(expected,sa.data.get(i));assertArrayEquals(expected,sb.data.get(i));
            }
            assertEquals(1,sa.connected);assertEquals(1,sb.connected);
            a.close();assertThrows(IOException.class,()->a.send(new byte[]{1}));
            until=System.nanoTime()+5000000000L;
            while(sb.closed==0&&System.nanoTime()<until){b.poll();Thread.sleep(10);}
            assertEquals(1,sb.closed);assertFalse(b.isConnected());
        }
    }
    @Test public void wrongRoomNeverConnectsAndTimeoutCloses()throws Exception{
        Sink sa=new Sink(),sb=new Sink();long[] now={0};
        try(SecurePeer a=new SecurePeer(binding(),A,null,sa,()->now[0]);
            SecurePeer b=new SecurePeer(binding(),B,null,sb,()->now[0])){
            for(int i=0;i<100;i++)pump(a,b,sa,sb);
            assertFalse(a.isConnected());assertFalse(b.isConnected());
            now[0]=120001;a.poll();b.poll();assertEquals(1,sa.closed);assertEquals(1,sb.closed);
        }
    }
    @Test public void peersWithDifferentClockOriginsConnect()throws Exception{
        Sink sa=new Sink(),sb=new Sink();PeerBinding binding=binding();
        try(SecurePeer a=new SecurePeer(binding,A,null,sa,()->1000);
            SecurePeer b=new SecurePeer(binding,B,null,sb,()->900000000)){
            connect(a,b,sa,sb);a.send(new byte[]{42});
            long until=System.nanoTime()+5000000000L;
            while(sb.data.isEmpty()&&System.nanoTime()<until)pump(a,b,sa,sb);
            assertEquals(1,sb.data.size());assertArrayEquals(new byte[]{42},sb.data.get(0));
        }
    }
    @Test public void failedSignalingClosesWithoutOpeningGameChannel()throws Exception{
        Sink sink=new Sink(){@Override public boolean signal(String p,List<String> s){return false;}};
        try(SecurePeer a=new SecurePeer(binding(),A,null,sink)){
            long until=System.nanoTime()+5000000000L;
            while(sink.closed==0&&System.nanoTime()<until){a.poll();Thread.sleep(5);}
            assertEquals(1,sink.closed);assertEquals(0,sink.connected);
        }
    }
    @Test public void closeInterruptsNativePollAndIsIdempotent()throws Exception{
        NativePeer peer=new NativePeer(false,"");peer.start();
        AtomicReference<Throwable> problem=new AtomicReference<>();
        Thread reader=new Thread(()->{try{peer.poll(250);}catch(IOException expected){}catch(Throwable t){problem.set(t);}});
        reader.start();Thread.sleep(20);peer.close();peer.close();reader.join(2000);
        assertFalse(reader.isAlive());assertNull(problem.get());assertThrows(IOException.class,()->peer.send(new byte[]{1}));
    }
    @Test public void repeatedConnectCloseDoesNotRetainNativeHandles()throws Exception{
        for(int i=0;i<12;i++)try(NativePeer peer=new NativePeer(false,"")){peer.start();}
    }
    @Test public void stalledConsumerFailsInsteadOfDroppingPackets()throws Exception{
        Sink sa=new Sink(),sb=new Sink();PeerBinding binding=binding();
        try(SecurePeer a=new SecurePeer(binding,A,null,sa);SecurePeer b=new SecurePeer(binding,B,null,sb)){
            connect(a,b,sa,sb);
            for(int i=0;i<300;i++)a.send(new byte[]{(byte)i});
            // Do not poll the receiver: native callbacks must enforce the queue bound.
            Thread.sleep(1500);b.poll();
            assertEquals(1,sb.closed);assertFalse(b.isConnected());assertTrue(sb.data.isEmpty());
        }
    }
    @Test public void nativeInstanceBudgetIsReleasedOnClose()throws Exception{
        List<NativePeer> peers=new ArrayList<>();
        try{
            for(int i=0;i<8;i++)peers.add(new NativePeer(false,""));
            assertThrows(IOException.class,()->new NativePeer(false,""));
            peers.remove(0).close();
            try(NativePeer replacement=new NativePeer(false,"")){replacement.start();}
        }finally{for(NativePeer peer:peers)peer.close();}
    }
    @Test public void crossThreadCloseWaitsForCurrentListenerAndFencesLaterPackets()throws Exception{
        SecurePeer[] receiver={null};Thread[] closer={null};
        java.util.concurrent.atomic.AtomicBoolean returned=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.CountDownLatch closing=new java.util.concurrent.CountDownLatch(1);
        Sink sa=new Sink(),sb=new Sink(){@Override public void message(byte[] bytes){
            super.message(bytes);
            closer[0]=new Thread(()->{closing.countDown();receiver[0].close();returned.set(true);});
            closer[0].start();
            try{
                assertTrue(closing.await(1,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(50);
                assertFalse("Close returned while the listener was still delivering",returned.get());
            }catch(InterruptedException failure){throw new AssertionError(failure);}
        }};
        PeerBinding binding=binding();
        try(SecurePeer a=new SecurePeer(binding,A,null,sa);SecurePeer b=new SecurePeer(binding,B,null,sb)){
            receiver[0]=b;connect(a,b,sa,sb);a.send(new byte[]{1});
            long until=System.nanoTime()+5000000000L;
            while(sb.data.isEmpty()&&System.nanoTime()<until)pump(a,b,sa,sb);
            assertNotNull(closer[0]);closer[0].join(2000);assertTrue(returned.get());
            b.poll();assertEquals(1,sb.data.size());assertFalse(b.isConnected());
        }
    }
    @Test public void wrongDtlsFingerprintCannotOpenChannel()throws Exception{
        try(NativePeer a=new NativePeer(true,"");NativePeer b=new NativePeer(false,"")){
            a.start();b.start();boolean failed=false;long until=System.nanoTime()+10000000000L;
            while(!failed&&System.nanoTime()<until){
                for(int side=0;side<2;side++){
                    NativePeer source=side==0?a:b,target=side==0?b:a;NativePeer.Event e;
                    while((e=source.poll(0))!=null){
                        assertNotEquals("Untrusted DTLS fingerprint opened channel",NativePeer.OPEN,e.kind);
                        if(e.kind==NativePeer.ERROR){failed=true;break;}
                        if(e.kind==NativePeer.DESCRIPTION){
                            String sdp=e.text;
                            if(side==0){
                                java.util.regex.Matcher fingerprint=java.util.regex.Pattern.compile("a=fingerprint:sha-256 ([0-9A-Fa-f]{2})").matcher(sdp);
                                assertTrue(fingerprint.find());
                                int at=fingerprint.start(1);
                                sdp=sdp.substring(0,at)+(fingerprint.group(1).equals("00")?"01":"00")+sdp.substring(at+2);
                            }
                            target.description(sdp,e.extra);
                        }else if(e.kind==NativePeer.CANDIDATE)target.candidate(e.text,e.extra);
                    }
                }
                Thread.sleep(5);
            }
            assertTrue("DTLS rejected forged fingerprint",failed);
        }
    }
}
