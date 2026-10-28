/*
 * Copyright 2020 Nikita Shakarun
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

import android.media.MediaPlayer;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.microedition.shell.GuestTime;
import ru.playsoftware.j2meloader.runtime.AudioSpeed;

public class AndroidPlayer extends MediaPlayer {
	private static final Set<AndroidPlayer> players = Collections.newSetFromMap(new WeakHashMap<>());
	private final AudioSpeed audioSpeed = new AudioSpeed();
	private boolean released;
	private boolean loaded;
	private String path;
	private float leftVolume, rightVolume;
	private int timePos;
	private int duration;
	private boolean looping;

	public AndroidPlayer() {
		super();
		this.leftVolume = 1.0f;
		this.rightVolume = 1.0f;
		synchronized (players) { players.add(this); }
	}

	public static void refreshSpeed() {
		AndroidPlayer[] snapshot;
		synchronized (players) { snapshot = players.toArray(new AndroidPlayer[0]); }
		for (AndroidPlayer player : snapshot) player.applySpeed();
	}

	private synchronized void applySpeed() {
		if (released || !loaded) return;
		try {
			if (super.isPlaying()) audioSpeed.applyPlaying(this, GuestTime.speed());
		} catch (IllegalStateException failure) {
			android.util.Log.w("AudioSpeed", "Skipping inactive media player", failure);
		}
	}

	@Override
	public synchronized void setDataSource(String path) throws IOException, IllegalArgumentException, IllegalStateException, SecurityException {
		if (released || loaded) throw new IllegalStateException("Reset before replacing audio source");
		this.path = path;
		duration = 0;
	}

	@Override
	public synchronized void seekTo(int msec) throws IllegalStateException {
		msec = Math.max(0, msec);
		if (loaded) {
			super.seekTo(msec);
		}
		this.timePos = msec;
	}

	@Override
	public synchronized int getCurrentPosition() {
		if (loaded) {
			return super.getCurrentPosition();
		} else {
			return timePos;
		}
	}

	@Override
	public synchronized int getDuration() {
		if (loaded) {
			return super.getDuration();
		} else {
			return duration;
		}
	}

	@Override
	public synchronized void setLooping(boolean looping) {
		if (loaded) {
			super.setLooping(looping);
		}
		this.looping = looping;
	}

	@Override
	public synchronized void setVolume(float leftVolume, float rightVolume) {
		if (loaded) {
			super.setVolume(leftVolume, rightVolume);
		}
		this.leftVolume = leftVolume;
		this.rightVolume = rightVolume;
	}

	@Override
	public synchronized void start() throws IllegalStateException {
		try {
			load();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot prepare audio", e);
		}
		timePos = 0;
		super.start();
		applySpeed();
	}

	@Override public synchronized void pause() { super.pause(); }

	@Override public synchronized void release() {
		released = true;
		synchronized (players) { players.remove(this); }
		super.release();
	}

	@Override
	public synchronized void reset() {
		super.reset();
		loaded = false;
		timePos = 0;
		audioSpeed.reset();
	}

	@Override public synchronized void prepare() throws IOException { load(); }

	private void load() throws IOException {
		if (released) throw new IllegalStateException("Audio player is released");
		if (!loaded) {
			try {
				super.setDataSource(path);
				super.prepare();
				duration = super.getDuration();
				super.setVolume(leftVolume, rightVolume);
				super.setLooping(looping);
				super.seekTo(timePos);
				loaded = true;
			} catch (IOException | RuntimeException e) {
				// Failed prepare may leave Android in Initialized or Error, not Idle.
				super.reset();
				audioSpeed.reset();
				throw e;
			}
		}
	}
}
