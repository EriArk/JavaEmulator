/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class FramedL2capConnectionTest {
    private static FramedL2capConnection channel(InputStream in, ByteArrayOutputStream out, AtomicInteger closes) {
        return new FramedL2capConnection(in,out,closes::incrementAndGet,672,672);
    }
    @Test public void separatesCoalescedPacketsAndDiscardsShortBufferRemainder() throws Exception {
        FramedL2capConnection c=channel(new ByteArrayInputStream(new byte[]{0,3,1,2,3,0,2,4,5}),new ByteArrayOutputStream(),new AtomicInteger());
        assertTrue(c.ready()); byte[] shortBuf=new byte[1]; assertEquals(1,c.receive(shortBuf)); assertEquals(1,shortBuf[0]);
        byte[] next=new byte[8]; assertTrue(c.ready()); assertEquals(2,c.receive(next)); assertEquals(4,next[0]); assertEquals(5,next[1]);
    }
    @Test public void receiveAssemblesSingleByteFragments() throws Exception {
        InputStream fragmented=new ByteArrayInputStream(new byte[]{0,3,5,6,7}) {
            @Override public synchronized int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(1,len)); }
        };
        byte[] result=new byte[3]; assertEquals(3,channel(fragmented,new ByteArrayOutputStream(),new AtomicInteger()).receive(result));
        assertArrayEquals(new byte[]{5,6,7},result);
    }
    @Test public void readyWaitsForCompletePacketWithoutBlocking() throws Exception {
        class Incremental extends InputStream {
            final byte[] data={0,3,1,2,3,0,0}; int pos, visible;
            @Override public int available() { return visible-pos; }
            @Override public int read() { if(pos>=visible) throw new AssertionError("ready must not block"); return data[pos++]; }
        }
        Incremental in=new Incremental(); FramedL2capConnection c=channel(in,new ByteArrayOutputStream(),new AtomicInteger());
        for(int i=0;i<5;i++) { in.visible=i; assertFalse(c.ready()); }
        in.visible=5; assertTrue(c.ready()); assertTrue(c.ready()); assertEquals(3,c.receive(new byte[10]));
        in.visible=7; assertTrue(c.ready()); assertEquals(0,c.receive(new byte[1]));
    }
    @Test public void zeroLengthDestinationConsumesExactlyOnePacket() throws Exception {
        FramedL2capConnection c=channel(new ByteArrayInputStream(new byte[]{0,1,9,0,1,8}),new ByteArrayOutputStream(),new AtomicInteger());
        assertEquals(0,c.receive(new byte[0])); byte[] next=new byte[1]; assertEquals(1,c.receive(next)); assertEquals(8,next[0]);
    }
    @Test public void sendsEmptyAndMtuTruncatedPackets() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); FramedL2capConnection c=channel(new ByteArrayInputStream(new byte[0]),out,new AtomicInteger());
        c.send(new byte[0]); c.send(new byte[1000]); byte[] wire=out.toByteArray();
        assertEquals(676,wire.length); assertEquals(0,wire[0]); assertEquals(0,wire[1]);
        assertEquals(2,wire[2]); assertEquals(160,wire[3]&255);
    }
    @Test public void oversizedPacketClosesChannelBeforeReadingPayload() throws Exception {
        AtomicInteger closes=new AtomicInteger(); FramedL2capConnection c=channel(new ByteArrayInputStream(new byte[]{(byte)255,(byte)255}),new ByteArrayOutputStream(),closes);
        assertThrows(IOException.class,c::ready); assertEquals(1,closes.get());
        assertThrows(IOException.class,()->c.send(new byte[]{1})); c.close(); assertEquals(1,closes.get());
    }
    @Test public void truncatedHeaderAndBodyFailClosed() throws Exception {
        for(byte[] bytes : new byte[][]{{0},{0,2,1},{}}) {
            AtomicInteger closes=new AtomicInteger(); FramedL2capConnection c=channel(new ByteArrayInputStream(bytes),new ByteArrayOutputStream(),closes);
            assertThrows(IOException.class,()->c.receive(new byte[10])); assertEquals(1,closes.get());
        }
    }
    @Test public void mtuValidationAndClosedAccess() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new FramedL2capConnection(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),()->{},47,672));
        assertThrows(IllegalArgumentException.class,()->new FramedL2capConnection(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),()->{},672,65536));
        FramedL2capConnection c=channel(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),new AtomicInteger());
        c.close(); assertThrows(IOException.class,c::getReceiveMTU); assertThrows(IOException.class,c::getTransmitMTU); assertThrows(IOException.class,c::ready);
    }
    @Test(timeout=10000) public void twoLoopbackPeersExchangePacketsAndCloseUnblocksRead() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try (ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
             Socket client=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
             Socket accepted=server.accept()) {
            client.setSoTimeout(3000); accepted.setSoTimeout(3000);
            FramedL2capConnection a=new FramedL2capConnection(client.getInputStream(),client.getOutputStream(),client,672,672);
            FramedL2capConnection b=new FramedL2capConnection(accepted.getInputStream(),accepted.getOutputStream(),accepted,672,672);
            byte[] data=new byte[672]; Arrays.fill(data,(byte)37); a.send(data); a.send(new byte[]{9});
            byte[] result=new byte[672]; assertEquals(672,b.receive(result)); assertArrayEquals(data,result);
            assertEquals(1,b.receive(result)); assertEquals(9,result[0]); b.send(new byte[]{42}); assertEquals(1,a.receive(result)); assertEquals(42,result[0]);
            CountDownLatch reading=new CountDownLatch(1);
            Future<Boolean> blocked=pool.submit(()->{reading.countDown(); try { b.receive(new byte[8]); return false; } catch(IOException e) { return true; }});
            assertTrue(reading.await(1,TimeUnit.SECONDS)); b.close(); assertTrue(blocked.get(1,TimeUnit.SECONDS)); a.close();
        } finally { pool.shutdownNow(); }
    }
}
