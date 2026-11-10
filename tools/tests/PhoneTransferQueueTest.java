// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Host-side behavioral checks; no Android SDK, firmware or games required. */
public final class PhoneTransferQueueTest {
    private static int passed;
    private static void check(boolean condition) { if(!condition) throw new AssertionError(); }
    private static String id(int n) { return String.format(Locale.ROOT,"%064x",n); }
    private static PhoneTransferQueue queue(File root) {
        PhoneTransferQueue q=new PhoneTransferQueue(new File(root,"queue.bin"));
        for(int n=1;n<=3;n++) {
            PhoneTransferQueue.Item item=new PhoneTransferQueue.Item("phone-"+n,"Phone "+n,10*n);
            item.selected=true; q.items.add(item);
        }
        return q;
    }
    private static class Backend implements PhoneTransferQueue.Backend {
        final Map<String,Integer> copies=new HashMap<>();
        final Map<String,String> saves=new HashMap<>();
        String failingCopy="", failingPublish="";
        public String copy(PhoneTransferQueue.Item item) throws Exception {
            copies.merge(item.firmware,1,Integer::sum);
            if(item.firmware.equals(failingCopy)) throw new IOException("fixture copy failure");
            String destination=id(Integer.parseInt(item.firmware.substring(6)));
            saves.putIfAbsent(destination,"original");
            return destination;
        }
        public void publish(String destination) throws Exception {
            if(destination.equals(failingPublish)) throw new IOException("fixture index failure");
        }
        public String message(Exception error) { return error.getMessage(); }
    }
    private static void failureDoesNotStopOtherPhones(File root) throws Exception {
        PhoneTransferQueue q=queue(root); Backend backend=new Backend(); backend.failingCopy="phone-2";
        q.run(backend,()->false);
        check(q.completed()==2 && q.items.get(1).state==PhoneTransferQueue.State.FAILED);
        backend.saves.put(id(1),"newer-save"); backend.failingCopy="";
        q=PhoneTransferQueue.load(q.file); q.run(backend,()->false);
        check(q.completed()==3 && backend.copies.get("phone-1")==1 && backend.copies.get("phone-2")==2);
        check(backend.saves.get(id(1)).equals("newer-save")); passed++;
    }
    private static void publicationRetryNeverCopiesAgain(File root) throws Exception {
        PhoneTransferQueue q=queue(root); Backend backend=new Backend(); backend.failingPublish=id(2);
        q.run(backend,()->false); check(q.completed()==2);
        check(q.items.get(1).destination.equals(id(2)));
        backend.saves.put(id(2),"played-after-copy"); backend.failingPublish="";
        q=PhoneTransferQueue.load(q.file); q.run(backend,()->false);
        check(q.completed()==3 && backend.copies.get("phone-2")==1);
        check(backend.saves.get(id(2)).equals("played-after-copy")); passed++;
    }
    private static void cancellationBeforeWorkDoesNotCopy(File root) throws Exception {
        PhoneTransferQueue q=queue(root); Backend backend=new Backend();
        q.run(backend,()->true); check(backend.copies.isEmpty());
        q=PhoneTransferQueue.load(q.file); check(q.started && q.selected()==3 && q.completed()==0); passed++;
    }
    private static void cancellationDuringCopyPreservesCompleted(File root) throws Exception {
        PhoneTransferQueue q=queue(root);
        Backend backend=new Backend() {
            public String copy(PhoneTransferQueue.Item item) throws Exception {
                if(item.firmware.equals("phone-2")) throw new InterruptedIOException("fixture cancel");
                return super.copy(item);
            }
        };
        q.run(backend,()->false);
        q=PhoneTransferQueue.load(q.file);
        check(q.completed()==1 && q.items.get(1).state==PhoneTransferQueue.State.READY);
        check(!backend.copies.containsKey("phone-3")); passed++;
    }
    private static void cancellationAfterCommitRetainsIdentity(File root) throws Exception {
        PhoneTransferQueue q=queue(root); boolean[] cancel={false};
        Backend backend=new Backend() {
            public String copy(PhoneTransferQueue.Item item) throws Exception {
                String result=super.copy(item); cancel[0]=true; return result;
            }
        };
        q.run(backend,()->cancel[0]);
        q=PhoneTransferQueue.load(q.file);
        check(q.items.get(0).state==PhoneTransferQueue.State.COPIED && q.items.get(0).destination.equals(id(1)));
        cancel[0]=false; q.items.get(1).selected=false; q.items.get(2).selected=false;
        q.run(backend,()->cancel[0]); check(q.completed()==1 && backend.copies.get("phone-1")==1); passed++;
    }
    private static void interruptedStateNeedsExplicitRetry(File root) throws Exception {
        PhoneTransferQueue q=queue(root); q.started=true;
        q.items.get(0).state=PhoneTransferQueue.State.DONE; q.items.get(0).destination=id(1);
        q.items.get(1).state=PhoneTransferQueue.State.COPYING; q.items.get(2).selected=false; q.save();
        PhoneTransferQueue restored=PhoneTransferQueue.load(q.file);
        check(restored.items.get(1).state==PhoneTransferQueue.State.READY && restored.selected()==1);
        Backend backend=new Backend(); restored.run(backend,()->false);
        check(backend.copies.size()==1 && backend.copies.containsKey("phone-2")); passed++;
    }
    private static void corruptQueueFailsClosed(File root) throws Exception {
        PhoneTransferQueue q=queue(root); q.save();
        byte[] bytes=Files.readAllBytes(q.file.toPath());
        Files.write(q.file.toPath(),Arrays.copyOf(bytes,bytes.length-1));
        try { PhoneTransferQueue.load(q.file); throw new AssertionError(); } catch(IOException expected) { }
        q.items.get(0).destination="../unrelated"; q.save();
        try { PhoneTransferQueue.load(q.file); throw new AssertionError(); } catch(IOException expected) { }
        passed++;
    }
    private static void journalFailureStopsBeforeNextPhone(File root) throws Exception {
        PhoneTransferQueue q=queue(root); Backend backend=new Backend() {
            public String copy(PhoneTransferQueue.Item item) throws Exception {
                String result=super.copy(item);
                Files.delete(q.file.toPath()); Files.createDirectory(q.file.toPath());
                return result;
            }
        };
        try { q.run(backend,()->false); throw new AssertionError(); } catch(IOException expected) { }
        check(backend.copies.size()==1 && q.items.get(0).destination.equals(id(1))); passed++;
    }
    private static void failurePresentationSurvivesReloadAndClearsOnRetry(File root) throws Exception {
        PhoneTransferQueue q=queue(root);
        Backend backend=new Backend() {
            @Override public String presentation(Exception error) { return "stable-error-identity"; }
        };
        backend.failingCopy="phone-2"; q.run(backend,()->false);
        q=PhoneTransferQueue.load(q.file);
        check(q.items.get(1).failureRecord.equals("stable-error-identity"));
        check(q.items.get(1).error.equals("fixture copy failure"));
        backend.failingCopy=""; q.run(backend,()->false);
        check(q.items.get(1).failureRecord.isEmpty()); passed++;
    }
    private static void legacyQueueKeepsSelectionWithoutAutomaticWork(File root) throws Exception {
        File file=new File(root,"queue.bin");
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(0x454b4131); out.writeBoolean(true); out.writeInt(1);
            out.writeUTF("phone-1"); out.writeUTF("Phone 1"); out.writeLong(10);
            out.writeBoolean(true); out.writeInt(PhoneTransferQueue.State.FAILED.ordinal());
            out.writeUTF(""); out.writeUTF("old diagnostic");
        }
        PhoneTransferQueue q=PhoneTransferQueue.load(file);
        check(q.selected()==1 && q.completed()==0 && q.items.get(0).failureRecord.isEmpty());
        q.save(); q=PhoneTransferQueue.load(file);
        check(q.items.get(0).error.equals("old diagnostic")); passed++;
    }
    public static void main(String[] args) throws Exception {
        Path base=Files.createTempDirectory("abyssme-phone-queue-test-");
        try {
            failurePresentationSurvivesReloadAndClearsOnRetry(Files.createDirectory(base.resolve("presentation")).toFile());
            legacyQueueKeepsSelectionWithoutAutomaticWork(Files.createDirectory(base.resolve("legacy")).toFile());
            failureDoesNotStopOtherPhones(Files.createDirectory(base.resolve("failure")).toFile());
            publicationRetryNeverCopiesAgain(Files.createDirectory(base.resolve("index")).toFile());
            cancellationBeforeWorkDoesNotCopy(Files.createDirectory(base.resolve("cancel-first")).toFile());
            cancellationDuringCopyPreservesCompleted(Files.createDirectory(base.resolve("cancel-copy")).toFile());
            cancellationAfterCommitRetainsIdentity(Files.createDirectory(base.resolve("cancel-commit")).toFile());
            interruptedStateNeedsExplicitRetry(Files.createDirectory(base.resolve("restart")).toFile());
            corruptQueueFailsClosed(Files.createDirectory(base.resolve("corrupt")).toFile());
            journalFailureStopsBeforeNextPhone(Files.createDirectory(base.resolve("journal")).toFile());
            System.out.println("Phone transfer queue: "+passed+" checks passed");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(base)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
}
