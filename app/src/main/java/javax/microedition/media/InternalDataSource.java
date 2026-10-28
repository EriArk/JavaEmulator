/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
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

import android.util.Log;
import android.webkit.MimeTypeMap;

import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.arthenica.mobileffmpeg.StreamInformation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;

import javax.microedition.media.protocol.DataSource;
import javax.microedition.media.protocol.SourceStream;
import javax.microedition.util.ContextHolder;

public class InternalDataSource extends DataSource {
	private static final String TAG = InternalDataSource.class.getName();

	private File mediaFile;
	private String type;

	public InternalDataSource(InputStream stream, String type) throws IllegalArgumentException, IOException {
		super(null);

		String extension = "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
		this.mediaFile = File.createTempFile("media", extension, ContextHolder.getCacheDir());
		this.type = type;

		final String name = mediaFile.getName();
		Log.d(TAG, "Starting media pipe: " + name);
		// available() is a non-blocking hint, not the length of a guest resource.
		try (InputStream input = stream; FileOutputStream output = new FileOutputStream(mediaFile)) {
			byte[] buf = new byte[0x10000];
			int read;
			while ((read = input.read(buf)) != -1) {
				if (read == 0) {
					int value = input.read();
					if (value == -1) break;
					output.write(value);
				} else output.write(buf, 0, read);
			}
			Log.d(TAG, "Media pipe closed: " + name);
		} catch (IOException | RuntimeException e) {
			mediaFile.delete();
			throw e;
		}

		try {
			convert();
		} catch (IOException e) {
			mediaFile.delete();
			throw e;
		} catch (RuntimeException | LinkageError e) {
			// Inspection may be unavailable on broken vendor builds. Keep the original.
			Log.w(TAG, "Audio inspection unavailable", e);
		}
	}

	private void convert() throws IOException {
		MediaInformation mediaInformation = FFprobe.getMediaInformation(mediaFile.getPath());
		if (mediaInformation != null && mediaInformation.getStreams() != null
				&& !mediaInformation.getStreams().isEmpty()) {
			StreamInformation streamInformation = mediaInformation.getStreams().get(0);
			String codec = streamInformation.getCodec();
			if (codec != null && codec.contains("adpcm")) {
				String newName = mediaFile.getPath() + ".wav";
				try {
					NativeAudio.decodeAdpcm(mediaFile.getPath(), newName);
				} catch (IOException | RuntimeException | LinkageError e) {
					new File(newName).delete();
					throw new IOException("Cannot decode ADPCM without losing precision", e);
				}
				mediaFile.delete();
				mediaFile = new File(newName);
			}
		}
	}

	@Override
	public String getLocator() {
		return mediaFile.getAbsolutePath();
	}

	@Override
	public String getContentType() {
		return type;
	}

	@Override
	public void connect() throws IOException {
	}

	@Override
	public void disconnect() {
		if (mediaFile.delete()) {
			Log.d(TAG, "Temp file deleted: " + mediaFile.getAbsolutePath());
		}
	}

	@Override
	public void start() throws IOException {
	}

	@Override
	public void stop() throws IOException {
	}

	@Override
	public SourceStream[] getStreams() {
		return new SourceStream[0];
	}

	@Override
	public Control[] getControls() {
		return new Control[0];
	}

	@Override
	public Control getControl(String control) {
		return null;
	}

}
