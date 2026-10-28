# Existing Audio Backend Bridge

This bridge does not replace the synthesizer, SoundFont or FFmpeg dependency.
It calls the public APIs exported by the already-pinned application libraries:

- `libmidi.so`: billthefarmer/mididriver
  `ce52b068ae3525edf6a8a946c396f9ce08d1dd69` (`v1.21`). The three headers in
  `include/eas` are unchanged copies from `library/src/main/jni/host_src`.
  Sonic Network's Apache-2.0 notices remain in the files.
- `libavcodec.so`, `libavformat.so`, `libavutil.so`: MobileFFmpeg
  `963b4eea110250811f4f4fc7d59c1a86d9dd6b2c` (`v4.3.2-compact`). The headers
  in `include/ffmpeg` are unchanged copies from `src/ffmpeg`, except the small
  original `libavutil/avconfig.h` for the supported little-endian Android ABIs.
  FFmpeg's LGPL notices remain in each upstream file. No FFmpeg implementation
  is compiled here; internal conditional headers are not enabled or vendored.

Both complete source archives and license materials are already pinned in
`tools/release-source-lock.json` and collected by the release source workflow.
No new prebuilt dependency or sound bank is introduced. The original JNI/Java
bridge is GPL-3.0-only, and inherited component licenses remain unchanged.

The ABI version checks intentionally reject a different library build before
using public structs. A future dependency update must update these headers and
run the audio instrumentation checks, not remove the checks to bypass a mismatch.

## Ownership

Every direct MIDI player owns an EAS instance and an Android AudioTrack. All EAS
calls are serialized because the inherited implementation may use shared scratch
space. Voice/controller state, master volume and output sessions remain separate.
Master volume/mute/pan scale the session's PCM before Android output effects;
they never rewrite authored MIDI channel volume, expression or velocity.
AudioTrack is stopped before joining its render thread; the native instance is
freed only after the thread exits. File MIDI and tone sequences keep their
existing MediaPlayer implementation.

ADPCM uses FFmpeg's existing IMA WAV and Yamaha decoders, accepting their signed
16-bit packed/planar output and writing a PCM WAV without resampling, channel
mixing or 8-bit quantization. The conversion is bounded to 256 MiB of PCM. Failed
conversions remove incomplete output and fail the guest load instead of falling
back to lossy output. Mono/stereo are supported; other layouts fail explicitly.

Tests: `AdpcmPrecisionTest`, `DirectMidiTest`, `MidiChannelStateTest`, plus the
existing audio pipeline, gain, output parity and speed suites. This is not
subjective sound-quality or complete-game acceptance.
