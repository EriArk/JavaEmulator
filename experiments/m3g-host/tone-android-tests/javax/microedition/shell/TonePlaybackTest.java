package javax.microedition.shell;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.tone.ToneManager;
import javax.microedition.media.tone.ToneSequence;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android playback completion, not a claim of listening to the device. */
@RunWith(AndroidJUnit4.class)
public class TonePlaybackTest {
    @Test public void toneStreamUsesToneControlBackend() throws Exception {
        playback(Manager.createPlayer(new java.io.ByteArrayInputStream(new byte[]{-2,1,60,4}), "audio/x-tone-seq"));
    }
    @Test public void gameToneCanReplayAndReplaceAfterDeallocation() throws Exception {
        Player player = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
        try {
            player.realize();
            ToneControl control = (ToneControl) player.getControl("ToneControl");
            control.setSequence(new byte[]{-2,1,60,4});
            CountDownLatch ended = new CountDownLatch(2);
            player.addPlayerListener((source, event, data) -> {
                if (PlayerListener.END_OF_MEDIA.equals(event)) ended.countDown();
            });
            player.setLoopCount(2);
            player.start();
            assertTrue("Finite repeat did not finish", ended.await(8, TimeUnit.SECONDS));
            player.deallocate();
            control.setSequence(new byte[]{-2,1,67,8});
            player.setLoopCount(1);
            playback(player);
        } finally { player.close(); }
    }
    @Test public void gameToneDeviceCompletes() throws Exception {
        Player player = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
        player.realize();
        ((ToneControl) player.getControl("ToneControl")).setSequence(new byte[]{-2,1,69,4});
        playback(player);
    }
    @Test public void gameToneDeviceEnforcesStateAndSequenceValidation() throws Exception {
        Player player = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
        try {
            player.realize();
            ToneControl control = (ToneControl) player.getControl("ToneControl");
            try { control.setSequence(null); fail("Null sequence accepted"); }
            catch (IllegalArgumentException expected) { }
            control.setSequence(new byte[]{-2,1,69,4});
            player.prefetch();
            assertEquals(125000, player.getDuration());
            try { control.setSequence(new byte[]{-2,1,60,8}); fail("Prefetched sequence changed"); }
            catch (IllegalStateException expected) { }
            player.deallocate();
            control.setSequence(new byte[]{-2,1,60,8});
            player.prefetch();
            assertEquals(250000, player.getDuration());
        } finally { player.close(); }
    }
    @Test public void singleToneStartsAndCompletes() throws Exception {
        playback(ToneManager.createPlayer(69, 120, 80));
    }
    @Test public void compiledBlocksAndSilencePlayThroughHost() throws Exception {
        ToneSequence sequence = new ToneSequence(new byte[]{-2,1,-5,0,60,4,-1,2,-6,0,-7,0,-7,0});
        sequence.process();
        playback(Manager.createPlayer(sequence.getStream(), "audio/midi"));
    }
    @Test public void invalidSingleToneIsRejectedBeforePlayerCreation() throws Exception {
        for (int[] pair : new int[][]{{-1, 10}, {128, 10}, {60, 0}}) {
            try { ToneManager.createPlayer(pair[0], pair[1], 100); fail("Invalid tone accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    private void playback(Player player) throws Exception {
        CountDownLatch ended = new CountDownLatch(1);
        player.addPlayerListener((source, event, data) -> {
            if (PlayerListener.END_OF_MEDIA.equals(event)) ended.countDown();
        });
        try {
            player.realize();
            player.start();
            assertTrue("No Android playback completion", ended.await(8, TimeUnit.SECONDS));
        } finally { player.close(); }
        assertEquals(Player.CLOSED, player.getState());
    }
}
