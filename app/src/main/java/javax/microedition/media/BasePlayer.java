/*
 * Copyright 2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.amms.control.PanControl;
import javax.microedition.amms.control.audioeffect.EqualizerControl;
import javax.microedition.media.control.VolumeControl;

public class BasePlayer implements Player, VolumeControl, PanControl {
	private TimeBase timeBase;
	protected volatile int state;
	private int loopCount;
	private int remainingLoops;
	private final ThreadPoolExecutor callbacks = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(), task -> new Thread(task, "MIDletPlayerCallback"));

	private final ArrayList<PlayerListener> listeners;
	private final HashMap<String, Control> controls;
	private boolean mute;
	private int level, pan;

	public BasePlayer() {
		state = UNREALIZED;

		mute = false;
		level = 100;
		pan = 0;
		loopCount = 1;
		remainingLoops = 1;

		InternalEqualizer equalizer = new InternalEqualizer();

		listeners = new ArrayList<>();
		controls = new HashMap<>();

		controls.put(VolumeControl.class.getName(), this);
		controls.put(PanControl.class.getName(), this);
		controls.put(EqualizerControl.class.getName(), equalizer);
	}

	public void addControl(String name, Control control) {
		controls.put(name, control);
	}

	public synchronized void complete() {
		if (state != STARTED) {
			return;
		}
		long endTime = getMediaTime();
		if (remainingLoops > 0) remainingLoops--;
		if (remainingLoops == 0) {
			state = PREFETCHED;
		}
		postEvent(PlayerListener.END_OF_MEDIA, Long.valueOf(endTime));

		if (state == STARTED) {
			doStart();
			postEvent(PlayerListener.STARTED, Long.valueOf(getMediaTime()));
		}
	}

	@Override
	public Control getControl(String controlType) {
		checkRealized();
		if (!controlType.contains(".")) {
			controlType = "javax.microedition.media.control." + controlType;
		}
		return controls.get(controlType);
	}

	@Override
	public Control[] getControls() {
		checkRealized();
		return controls.values().toArray(new Control[0]);
	}

	@Override
	public synchronized void addPlayerListener(PlayerListener playerListener) {
		checkClosed();
		if (!listeners.contains(playerListener) && playerListener != null) {
			listeners.add(playerListener);
		}
	}

	@Override
	public synchronized void removePlayerListener(PlayerListener playerListener) {
		checkClosed();
		listeners.remove(playerListener);
	}

	public synchronized void postEvent(String event, Object eventData) {
		if (callbacks.isShutdown() || listeners.isEmpty()) return;
		PlayerListener[] snapshot = listeners.toArray(new PlayerListener[0]);
		callbacks.execute(() -> {
			// Wait for the posting state transition, then invoke guest code outside its lock.
			synchronized (BasePlayer.this) { }
			for (PlayerListener listener : snapshot) {
				try { listener.playerUpdate(this, event, eventData); }
				catch (RuntimeException failure) {
					android.util.Log.w("J2MEAudio", "Player listener failed", failure);
				}
			}
		});
	}

	@Override
	public synchronized void realize() throws MediaException {
		checkClosed();

		if (state == UNREALIZED) {
			try {
				doRealize();
			} catch (IOException e) {
				throw new MediaException(e.getMessage());
			}

			state = REALIZED;
		}
	}

	@Override
	public synchronized void prefetch() throws MediaException {
		checkClosed();

		if (state == UNREALIZED) {
			realize();
		}

		if (state == REALIZED) {
			try {
				doPrefetch();
			} catch (Exception e) {
				throw new MediaException(e.toString());
			}
			state = PREFETCHED;
		}
	}

	@Override
	public synchronized void start() throws MediaException {
		prefetch();

		if (state == PREFETCHED) {
			try {
				doStart();
			} catch (IllegalStateException e) {
				throw new MediaException(e.toString());
			}

			state = STARTED;
			if (remainingLoops == 0) remainingLoops = loopCount;
			postEvent(PlayerListener.STARTED, new Long(getMediaTime()));
		}
	}

	@Override
	public synchronized void stop() {
		checkClosed();
		if (state == STARTED) {
			doStop();

			state = PREFETCHED;
			postEvent(PlayerListener.STOPPED, new Long(getMediaTime()));
		}
	}

	@Override
	public synchronized void deallocate() {
		stop();

		if (state == PREFETCHED) {
			doReset();
			state = REALIZED;
		}
	}

	@Override
	public synchronized void close() {
		if (state != CLOSED) {
			deallocate();
			doClose();

			state = CLOSED;
			postEvent(PlayerListener.CLOSED, null);
			callbacks.shutdown(); // Deliver already queued events, then release the worker.
			listeners.clear();
		}
	}

	protected void checkClosed() {
		if (state == CLOSED) {
			throw new IllegalStateException("player is closed");
		}
	}

	protected void checkRealized() {
		checkClosed();

		if (state == UNREALIZED) {
			throw new IllegalStateException("call realize() before using the player");
		}
	}

	@Override
	public synchronized long setMediaTime(long now) throws MediaException {
		checkRealized();
		if (state < PREFETCHED) {
			return 0;
		} else {
			if (now != doGetMediaTime()) {
				doSetMediaTime(now);
			}
			return getMediaTime();
		}
	}

	@Override
	public synchronized long getMediaTime() {
		checkClosed();
		if (state < PREFETCHED) {
			return TIME_UNKNOWN;
		} else {
			return doGetMediaTime();
		}
	}

	@Override
	public synchronized long getDuration() {
		checkClosed();
		if (state < PREFETCHED) {
			return TIME_UNKNOWN;
		} else {
			return doGetDuration();
		}
	}

	@Override
	public synchronized void setLoopCount(int count) {
		checkClosed();
		if (state == STARTED)
			throw new IllegalStateException("player must not be in STARTED state while using setLoopCount()");

		if (count == 0 || count < -1) {
			throw new IllegalArgumentException("loop count must be positive or -1");
		}

		doSetLooping(count == -1);
		loopCount = count;
		remainingLoops = count;
	}

	@Override
	public int getState() {
		return state;
	}

	@Override
	public String getContentType() {
		checkRealized();
		return doGetContentType();
	}

	// VolumeControl

	private void updateVolume() {
		float left, right;

		if (mute) {
			left = right = 0;
		} else {
			if (level == 100) {
				left = right = 1.0f;
			} else {
				left = right = (float) (1 - (Math.log(100 - level) / Math.log(100)));
			}

			if (pan >= 0) {
				left *= (float) (100 - pan) / 100f;
			}

			if (pan < 0) {
				right *= (float) (100 + pan) / 100f;
			}
		}

		doSetVolume(left, right);
		traceVolume("volume", left, right);
		postEvent(PlayerListener.VOLUME_CHANGED, this);
	}

	protected final void traceVolume(String action, float left, float right) {
		if (ru.playsoftware.j2meloader.BuildConfig.DEBUG
				&& android.util.Log.isLoggable("J2MEAudio", android.util.Log.DEBUG)) {
			String record = action + " player=" + System.identityHashCode(this)
					+ " type=" + doGetContentType() + " state=" + state + " level=" + level
					+ " mute=" + mute + " pan=" + pan + " gain=" + left + "," + right;
			android.util.Log.d("J2MEAudio", record);
			// Some handheld firmware discards debug logcat; keep the opt-in trace private and bounded.
			synchronized (BasePlayer.class) {
				java.io.File file = new java.io.File(javax.microedition.util.ContextHolder.getAppContext()
						.getCacheDir(), "j2me-audio-trace.txt");
				try (java.io.FileWriter writer = new java.io.FileWriter(file, file.length() < 262144)) {
					writer.write(android.os.SystemClock.elapsedRealtime() + " " + record + "\n");
				} catch (IOException ignored) { /* Diagnostics must not interrupt playback. */ }
			}
		}
	}

	@Override
	public synchronized void setMute(boolean mute) {
		if (state == CLOSED) {
			// Avoid IllegalStateException in MediaPlayer.setVolume()
			return;
		}

		this.mute = mute;
		updateVolume();
	}

	@Override
	public synchronized boolean isMuted() {
		return mute;
	}

	@Override
	public synchronized int setLevel(int level) {
		if (state == CLOSED) {
			// Avoid IllegalStateException in MediaPlayer.setVolume()
			return this.level;
		}

		if (level < 0) {
			level = 0;
		} else if (level > 100) {
			level = 100;
		}

		this.level = level;
		updateVolume();

		return level;
	}

	@Override
	public synchronized int getLevel() {
		return level;
	}

	// PanControl

	@Override
	public synchronized int setPan(int pan) {
		if (state == CLOSED) return this.pan;
		if (pan < -100) {
			pan = -100;
		} else if (pan > 100) {
			pan = 100;
		}

		this.pan = pan;
		updateVolume();

		return pan;
	}

	@Override
	public synchronized int getPan() {
		return pan;
	}

	@Override
	public TimeBase getTimeBase() {
		if (timeBase == null) {
			return Manager.getSystemTimeBase();
		}
		return timeBase;
	}

	@Override
	public void setTimeBase(TimeBase master) throws MediaException {
		timeBase = master;
	}

	// Implementation-defined methods

	public void doRealize() throws IOException {
	}

	public void doPrefetch()  throws IOException {
	}

	public void doStart() {
	}

	public void doStop() {
	}

	public void doClose() {
	}

	public void doReset() {
	}

	public void doSetMediaTime(long usec) {
	}

	public long doGetMediaTime() {
		return 0;
	}

	public long doGetDuration() {
		return 0;
	}

	public void doSetLooping(boolean looping) {
	}

	public String doGetContentType() {
		return "";
	}

	public void doSetVolume(float left, float right) {
	}
}
