// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Durable, explicit phone-copy attempts. A completed destination is never copied again. */
final class PhoneTransferQueue {
    static final class ReadFailure extends IOException {
        ReadFailure(IOException cause) { super(cause.getMessage(), cause); }
    }
    static final class SaveFailure extends IOException {
        SaveFailure(IOException cause) { super(cause.getMessage(), cause); }
    }
    enum State { READY, COPYING, COPIED, DONE, FAILED }
    static final class Item {
        final String firmware, label;
        final long bytes;
        boolean selected;
        State state = State.READY;
        String destination = "", error = "", failureRecord = "";
        Item(String firmware, String label, long bytes) {
            this.firmware = firmware; this.label = label; this.bytes = bytes;
        }
    }
    interface Backend {
        String copy(Item item) throws Exception;
        void publish(String destination) throws Exception;
        String message(Exception error);
        default String presentation(Exception error) { return ""; }
    }
    interface Cancellation { boolean requested(); }
    final List<Item> items = new ArrayList<>();
    final File file;
    boolean started;

    PhoneTransferQueue(File file) { this.file = file; }

    static PhoneTransferQueue load(File file) throws IOException {
        try { return read(file); }
        catch (IOException error) { throw new ReadFailure(error); }
    }
    private static PhoneTransferQueue read(File file) throws IOException {
        PhoneTransferQueue queue = new PhoneTransferQueue(file);
        if (!file.isFile()) return queue;
        if (file.length() > 4L * 1024 * 1024) throw new IOException("Phone transfer queue is too large");
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            int version = in.readInt();
            if (version != 0x454b4131 && version != 0x454b4132) throw new IOException("Invalid phone transfer queue");
            queue.started = in.readBoolean();
            int count = in.readInt();
            if (count < 1 || count > 256) throw new IOException("Invalid phone count");
            Set<String> identities = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Item item = new Item(in.readUTF(), in.readUTF(), in.readLong());
                if (!identities.add(item.firmware.toLowerCase(Locale.ROOT)) || item.bytes < 0)
                    throw new IOException("Invalid phone identity or size");
                item.selected = in.readBoolean();
                int state = in.readInt();
                if (state < 0 || state >= State.values().length) throw new IOException("Invalid transfer state");
                item.state = State.values()[state]; item.destination = in.readUTF(); item.error = in.readUTF();
                if (version == 0x454b4132) item.failureRecord = in.readUTF();
                if (!item.destination.isEmpty() && !item.destination.matches("[0-9a-f]{64}"))
                    throw new IOException("Invalid destination phone");
                if ((item.state == State.DONE || item.state == State.COPIED) && item.destination.isEmpty())
                    throw new IOException("Missing destination phone");
                // An interrupted copy is retried only after an explicit review. Its deterministic
                // snapshot ID also recovers a commit that preceded the journal write.
                if (item.state == State.COPYING) item.state = State.READY;
                queue.items.add(item);
            }
            if (in.read() != -1) throw new IOException("Unexpected transfer data");
        }
        return queue;
    }

    void save() throws IOException {
        try { write(); }
        catch (IOException error) { throw new SaveFailure(error); }
    }
    private void write() throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary);
                 DataOutputStream out = new DataOutputStream(stream)) {
                out.writeInt(0x454b4132); out.writeBoolean(started); out.writeInt(items.size());
                for (Item item : items) {
                    out.writeUTF(item.firmware); out.writeUTF(item.label); out.writeLong(item.bytes);
                    out.writeBoolean(item.selected); out.writeInt(item.state.ordinal());
                    out.writeUTF(item.destination); out.writeUTF(item.error);
                    out.writeUTF(item.failureRecord);
                }
                out.flush(); stream.getFD().sync();
            }
            if (temporary.length() > 4L * 1024 * 1024) throw new IOException("Phone transfer queue is too large");
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    int completed() { int n = 0; for (Item item : items) if (item.state == State.DONE) n++; return n; }
    int selected() { int n = 0; for (Item item : items) if (item.selected && item.state != State.DONE) n++; return n; }

    void run(Backend backend, Cancellation cancellation) throws IOException {
        if (selected() == 0) return;
        started = true; save();
        for (Item item : items) {
            if (!item.selected || item.state == State.DONE) continue;
            if (cancellation.requested()) break;
            item.error = ""; item.failureRecord = "";
            if (item.destination.isEmpty()) { item.state = State.COPYING; save(); }
            try {
                if (item.destination.isEmpty()) {
                    String destination = backend.copy(item);
                    if (destination == null || !destination.matches("[0-9a-f]{64}"))
                        throw new IOException("Invalid copied phone identity");
                    item.destination = destination;
                }
                item.state = State.COPIED;
            } catch (InterruptedIOException error) {
                item.state = item.destination.isEmpty() ? State.READY : State.COPIED;
                save(); break;
            } catch (Exception error) {
                failed(item, backend, error); save(); continue;
            }
            // Persist the committed identity before indexing. A failed journal write stops the
            // entire attempt; it must never be misclassified as an ordinary phone failure.
            save();
            if (cancellation.requested()) break;
            try {
                backend.publish(item.destination);
                item.state = State.DONE; item.selected = false;
            } catch (InterruptedIOException error) {
                save(); break;
            } catch (Exception error) {
                failed(item, backend, error);
            }
            save();
        }
    }
    private static void failed(Item item, Backend backend, Exception error) {
        item.state = State.FAILED; item.error = bounded(backend.message(error));
        String record = backend.presentation(error);
        item.failureRecord = record == null || record.length() > 16000 ? "" : record;
    }
    private static String bounded(String message) {
        if (message == null) return "";
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
