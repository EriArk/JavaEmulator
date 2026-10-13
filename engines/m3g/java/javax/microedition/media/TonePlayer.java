/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: Apache-2.0 */
package javax.microedition.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.protocol.DataSource;
import javax.microedition.media.tone.ToneSequence;

/** ToneControl sequences use the same timed Android backend as MIDI files. */
public final class TonePlayer extends MicroPlayer implements ToneControl {
    private long duration;

    public TonePlayer() throws IOException {
        super(compile(new byte[]{ToneControl.VERSION, 1, ToneControl.SILENCE, 1}));
        addControl(ToneControl.class.getName(), this);
    }

    public TonePlayer(InputStream input) throws IOException {
        this();
        try (InputStream stream = input) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > 1024 * 1024) throw new IOException("Tone sequence too large");
                bytes.write(buffer, 0, count);
            }
            setSequence(bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    private static DataSource compile(byte[] sequence) throws IOException {
        ToneSequence tone = new ToneSequence(sequence);
        tone.process();
        return new InternalDataSource(tone.getStream(), "audio/midi");
    }

    @Override public synchronized void setSequence(byte[] sequence) {
        checkClosed();
        if (state == PREFETCHED || state == STARTED) {
            throw new IllegalStateException("Deallocate before changing a tone sequence");
        }
        ToneSequence tone = new ToneSequence(sequence);
        tone.process();
        DataSource next;
        try { next = new InternalDataSource(tone.getStream(), "audio/midi"); }
        catch (IOException e) { throw new IllegalStateException("Cannot prepare tone sequence", e); }
        DataSource previous = source;
        source = next;
        try {
            doReset();
            if (state == REALIZED) doRealize();
            duration = tone.getDuration();
            previous.disconnect();
        } catch (IOException e) {
            source = previous;
            next.disconnect();
            throw new IllegalStateException("Cannot load tone sequence", e);
        }
    }

    @Override public long doGetDuration() { return duration; }
    @Override public String doGetContentType() { return "audio/x-tone-seq"; }
}
