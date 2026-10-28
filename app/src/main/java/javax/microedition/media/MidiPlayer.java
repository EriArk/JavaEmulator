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

import javax.microedition.media.control.MIDIControl;

public class MidiPlayer extends BasePlayer implements MIDIControl {

	private MidiSession session;
	private float left = 1, right = 1;
	private boolean stopped;
	private final MidiChannelState channels = new MidiChannelState();

	public MidiPlayer() {
		addControl(MIDIControl.class.getName(), this);
	}

	@Override
	public int[] getBankList(boolean custom) {
		return new int[0];
	}

	@Override
	public synchronized int getChannelVolume(int channel) {
		checkPrefetched();
		checkRange(channel, 15);
		return channels.volume(channel);
	}

	@Override
	public String getKeyName(int bank, int prog, int key) {
		return null;
	}

	@Override
	public int[] getProgram(int channel) {
		return new int[0];
	}

	@Override
	public int[] getProgramList(int bank) {
		return new int[0];
	}

	@Override
	public String getProgramName(int bank, int prog) {
		return "";
	}

	@Override
	public boolean isBankQuerySupported() {
		return false;
	}

	@Override
	public synchronized int longMidiEvent(byte[] data, int offset, int length) {
		checkPrefetched();
		if (data == null || offset < 0 || length < 0 || offset > data.length - length) {
			throw new IllegalArgumentException("Invalid MIDI slice");
		}
		if (length == 0) return 0;
		stopped = false;
		session.volume(left, right);
		if (!session.write(data, offset, length)) return -1;
		channels.accept(data, offset, length);
		return length;
	}

	@Override
	public synchronized void setChannelVolume(int channel, int volume) {
		checkPrefetched();
		checkRange(channel, 15);
		checkRange(volume, 127);
		shortMidiEvent(0xB0 | channel, 7, volume);
	}

	@Override
	public synchronized void setProgram(int channel, int bank, int program) {
		checkPrefetched();
		checkRange(channel, 15);
		checkRange(program, 127);
		if (bank < -1 || bank > 16383) throw new IllegalArgumentException("Invalid MIDI bank");
		if (bank == -1) bank = 0;
		shortMidiEvent(0xB0 | channel, 0, bank >> 7);
		shortMidiEvent(0xB0 | channel, 32, bank & 127);
		shortMidiEvent(0xC0 | channel, program, 0);
	}

	@Override
	public synchronized void shortMidiEvent(int type, int data1, int data2) {
		checkPrefetched();
		int size = MidiChannelState.messageSize(type);
		if (size >= 2) checkRange(data1, 127);
		if (size == 3) checkRange(data2, 127);
		byte[] event = {(byte) type, (byte) data1, (byte) data2};
		longMidiEvent(event, 0, size);
	}

	private void checkPrefetched() {
		checkClosed();
		if (state < PREFETCHED || session == null) throw new IllegalStateException("Prefetch MIDI player first");
	}

	private static void checkRange(int value, int max) {
		if (value < 0 || value > max) throw new IllegalArgumentException("MIDI value out of range");
	}

	@Override public synchronized void doPrefetch() throws IOException {
		if (session == null) { session = new MidiSession(left, right); stopped = false; }
	}

	@Override public synchronized void doStart() {
		stopped = false;
		if (session != null) session.volume(left, right);
	}

	@Override public synchronized void doStop() {
		if (session == null) return;
		stopped = true;
		session.volume(0, 0);
		for (int channel = 0; channel < 16; channel++) {
			byte[] event = {(byte) (0xB0 | channel), 120, 0};
			session.write(event, 0, event.length);
		}
	}

	@Override public synchronized void stop() {
		checkClosed();
		if (state == STARTED) super.stop();
		else if (state == PREFETCHED) doStop(); // Direct events can sound without Player.start().
	}

	@Override public synchronized void doReset() {
		if (session != null) { session.close(); session = null; }
		channels.reset();
	}

	@Override public void doClose() { doReset(); }
	@Override public String doGetContentType() { return "audio/midi"; }
	@Override public synchronized void doSetVolume(float left, float right) {
		this.left = left; this.right = right;
		if (session != null && !stopped) session.volume(left, right);
	}

	int audioSessionId() { return session.audioSessionId(); }
}
