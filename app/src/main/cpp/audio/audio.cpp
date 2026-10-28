/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: GPL-3.0-only */
#include <jni.h>
#include <dlfcn.h>
#include <cstdio>
#include <cstdint>
#include <memory>
#include <pthread.h>
extern "C" {
#include "eas.h"
#include "eas_reverb.h"
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
}

namespace {
struct AudioError { const char* message; };
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
template<class T> T symbol(void* lib, const char* name) {
    auto p = reinterpret_cast<T>(dlsym(lib, name));
    if (!p) throw AudioError{name};
    return p;
}
void* library(const char* name) {
    void* lib = dlopen(name, RTLD_NOW | RTLD_LOCAL);
    if (!lib) throw AudioError{name};
    return lib; // Process-lifetime dependency, also owned by its existing Java wrapper.
}

struct Eas {
    void* lib = library("libmidi.so");
#define EAS_FN(name) decltype(&::name) name = symbol<decltype(&::name)>(lib, #name)
    EAS_FN(EAS_Config); EAS_FN(EAS_Init); EAS_FN(EAS_Shutdown);
    EAS_FN(EAS_OpenMIDIStream); EAS_FN(EAS_CloseMIDIStream);
    EAS_FN(EAS_WriteMIDIStream); EAS_FN(EAS_Render); EAS_FN(EAS_SetParameter);
#undef EAS_FN
};
Eas& eas() { static Eas api; return api; }
// The inherited implementation has some shared scratch state; serialize calls,
// while retaining independent synth/voice/controller state for every session.
pthread_mutex_t midiMutex = PTHREAD_MUTEX_INITIALIZER;
struct MidiLock {
    MidiLock() { pthread_mutex_lock(&midiMutex); }
    ~MidiLock() { pthread_mutex_unlock(&midiMutex); }
};
struct Session {
    EAS_DATA_HANDLE data = nullptr;
    EAS_HANDLE stream = nullptr;
    ~Session() {
        if (stream) eas().EAS_CloseMIDIStream(data, stream);
        if (data) eas().EAS_Shutdown(data);
    }
};
struct Av {
    void* util = library("libavutil.so");
    void* codec = library("libavcodec.so");
    void* format = library("libavformat.so");
#define AV_FN(lib, name) decltype(&::name) name = symbol<decltype(&::name)>(lib, #name)
    AV_FN(format, avformat_version); AV_FN(codec, avcodec_version); AV_FN(util, avutil_version);
    AV_FN(format, avformat_open_input); AV_FN(format, avformat_find_stream_info);
    AV_FN(format, avformat_close_input); AV_FN(format, av_read_frame);
    AV_FN(codec, avcodec_find_decoder); AV_FN(codec, avcodec_alloc_context3);
    AV_FN(codec, avcodec_parameters_to_context); AV_FN(codec, avcodec_open2);
    AV_FN(codec, avcodec_send_packet); AV_FN(codec, avcodec_receive_frame);
    AV_FN(codec, avcodec_free_context); AV_FN(codec, av_packet_alloc);
    AV_FN(codec, av_packet_free); AV_FN(codec, av_packet_unref);
    AV_FN(util, av_frame_alloc); AV_FN(util, av_frame_free); AV_FN(util, av_frame_unref);
#undef AV_FN
};
Av& av() { static Av api; return api; }
struct Decode {
    AVFormatContext* format = nullptr;
    AVCodecContext* codec = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    FILE* output = nullptr;
    ~Decode() {
        if (output) fclose(output);
        av().av_frame_free(&frame);
        av().av_packet_free(&packet);
        av().avcodec_free_context(&codec);
        av().avformat_close_input(&format);
    }
};
void require(bool ok, const char* message) { if (!ok) throw AudioError{message}; }
void u16(FILE* file, uint32_t v) { fputc(v & 255, file); fputc((v >> 8) & 255, file); }
void u32(FILE* file, uint32_t v) { u16(file, v); u16(file, v >> 16); }
void header(FILE* f, uint32_t bytes, int rate, int channels) {
    rewind(f);
    fwrite("RIFF", 1, 4, f); u32(f, bytes + 36); fwrite("WAVEfmt ", 1, 8, f);
    u32(f, 16); u16(f, 1); u16(f, channels); u32(f, rate);
    u32(f, rate * channels * 2); u16(f, channels * 2); u16(f, 16);
    fwrite("data", 1, 4, f); u32(f, bytes);
}
struct Utf {
    JNIEnv* env; jstring value; const char* chars;
    Utf(JNIEnv* e, jstring v) : env(e), value(v), chars(e->GetStringUTFChars(v, nullptr)) {}
    ~Utf() { if (chars) env->ReleaseStringUTFChars(value, chars); }
};
}

extern "C" JNIEXPORT void JNICALL
Java_javax_microedition_media_NativeAudio_decodeAdpcm(JNIEnv* env, jclass, jstring input, jstring output) {
    Utf in(env, input), out(env, output);
    if (!in.chars || !out.chars) return;
    bool success = false;
    try {
        auto& a = av();
        require(a.avcodec_version() == LIBAVCODEC_VERSION_INT
            && a.avformat_version() == LIBAVFORMAT_VERSION_INT
            && a.avutil_version() == LIBAVUTIL_VERSION_INT, "Audio decoder ABI mismatch");
        Decode d;
        require(a.avformat_open_input(&d.format, in.chars, nullptr, nullptr) >= 0, "Cannot open ADPCM");
        require(a.avformat_find_stream_info(d.format, nullptr) >= 0, "Cannot inspect ADPCM");
        int stream = -1;
        for (unsigned i = 0; i < d.format->nb_streams; i++) {
            if (d.format->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) { stream = i; break; }
        }
        require(stream >= 0, "No audio stream");
        auto* params = d.format->streams[stream]->codecpar;
        require(params->codec_id == AV_CODEC_ID_ADPCM_IMA_WAV
            || params->codec_id == AV_CODEC_ID_ADPCM_YAMAHA, "Unsupported ADPCM decoder");
        auto* decoder = a.avcodec_find_decoder(params->codec_id);
        require(decoder != nullptr, "ADPCM decoder unavailable");
        d.codec = a.avcodec_alloc_context3(decoder);
        require(d.codec != nullptr, "Cannot allocate decoder");
        require(a.avcodec_parameters_to_context(d.codec, params) >= 0
            && a.avcodec_open2(d.codec, decoder, nullptr) >= 0, "Cannot initialize ADPCM decoder");
        const int channels = d.codec->channels, rate = d.codec->sample_rate;
        require(channels >= 1 && channels <= 2 && rate > 0 && rate <= 192000, "Invalid audio format");
        d.packet = a.av_packet_alloc(); d.frame = a.av_frame_alloc();
        require(d.packet && d.frame, "Cannot allocate audio frame");
        d.output = fopen(out.chars, "wb");
        require(d.output != nullptr, "Cannot create decoded audio");
        header(d.output, 0, rate, channels);
        uint64_t size = 0;
        auto drain = [&]() {
            int rc;
            while ((rc = a.avcodec_receive_frame(d.codec, d.frame)) >= 0) {
                auto* f = d.frame;
                require(f->channels == channels && f->sample_rate == rate
                    && (f->format == AV_SAMPLE_FMT_S16 || f->format == AV_SAMPLE_FMT_S16P),
                    "Unexpected ADPCM output format");
                size += static_cast<uint64_t>(f->nb_samples) * channels * 2;
                require(size <= 256 * 1024 * 1024, "Decoded audio exceeds 256 MiB");
                if (f->format == AV_SAMPLE_FMT_S16) {
                    size_t count = static_cast<size_t>(f->nb_samples) * channels * 2;
                    require(fwrite(f->data[0], 1, count, d.output) == count, "Cannot write decoded audio");
                } else {
                    for (int n = 0; n < f->nb_samples; n++) for (int c = 0; c < channels; c++) {
                        u16(d.output, reinterpret_cast<const int16_t*>(f->extended_data[c])[n]);
                    }
                    require(!ferror(d.output), "Cannot write decoded audio");
                }
                a.av_frame_unref(f);
            }
            require(rc == AVERROR(EAGAIN) || rc == AVERROR_EOF, "ADPCM decode failed");
        };
        int rc;
        while ((rc = a.av_read_frame(d.format, d.packet)) >= 0) {
            if (d.packet->stream_index == stream) {
                require(a.avcodec_send_packet(d.codec, d.packet) >= 0, "Invalid ADPCM packet");
                drain();
            }
            a.av_packet_unref(d.packet);
        }
        require(rc == AVERROR_EOF, "ADPCM input read failed");
        require(a.avcodec_send_packet(d.codec, nullptr) >= 0, "ADPCM flush failed");
        drain();
        require(size > 0, "Empty decoded audio");
        header(d.output, static_cast<uint32_t>(size), rate, channels);
        require(!ferror(d.output) && fflush(d.output) == 0, "Cannot finish decoded audio");
        success = true;
    } catch (const AudioError& e) { fail(env, e.message); }
    catch (...) { fail(env, "Cannot allocate audio decoder resources"); }
    if (!success) remove(out.chars);
}

extern "C" JNIEXPORT jlong JNICALL
Java_javax_microedition_media_NativeAudio_openMidi(JNIEnv* env, jclass, jintArray config) {
    MidiLock lock;
    try {
        auto& a = eas();
        const auto* c = a.EAS_Config();
        require(c && c->libVersion == LIB_VERSION && c->numChannels == 2
            && c->mixBufferSize > 0 && c->mixBufferSize <= 4096, "MIDI ABI mismatch");
        std::unique_ptr<Session> s(new Session);
        require(a.EAS_Init(&s->data) == EAS_SUCCESS, "Cannot initialize MIDI");
        a.EAS_SetParameter(s->data, EAS_MODULE_REVERB, EAS_PARAM_REVERB_PRESET, EAS_PARAM_REVERB_CHAMBER);
        a.EAS_SetParameter(s->data, EAS_MODULE_REVERB, EAS_PARAM_REVERB_BYPASS, EAS_FALSE);
        require(a.EAS_OpenMIDIStream(s->data, &s->stream, nullptr) == EAS_SUCCESS, "Cannot open MIDI stream");
        jint values[] = {static_cast<jint>(c->sampleRate), static_cast<jint>(c->mixBufferSize)};
        env->SetIntArrayRegion(config, 0, 2, values);
        return reinterpret_cast<jlong>(s.release());
    } catch (const AudioError& e) { fail(env, e.message); return 0; }
    catch (...) { fail(env, "Cannot allocate MIDI resources"); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_javax_microedition_media_NativeAudio_closeMidi(JNIEnv*, jclass, jlong handle) {
    MidiLock lock;
    delete reinterpret_cast<Session*>(handle);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_javax_microedition_media_NativeAudio_writeMidi(JNIEnv* env, jclass, jlong handle, jbyteArray data, jint offset, jint count) {
    if (!handle || !data || offset < 0 || count < 0 || offset > env->GetArrayLength(data) - count) return false;
    MidiLock lock;
    auto* s = reinterpret_cast<Session*>(handle);
    auto* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return false;
    auto rc = eas().EAS_WriteMIDIStream(s->data, s->stream, reinterpret_cast<EAS_U8*>(bytes) + offset, count);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return rc == EAS_SUCCESS;
}
extern "C" JNIEXPORT jint JNICALL
Java_javax_microedition_media_NativeAudio_renderMidi(JNIEnv* env, jclass, jlong handle, jshortArray data) {
    if (!handle) return -1;
    MidiLock lock;
    auto* s = reinterpret_cast<Session*>(handle);
    auto& a = eas();
    auto* c = a.EAS_Config();
    if (env->GetArrayLength(data) < c->mixBufferSize * 2) return -1;
    auto* pcm = env->GetShortArrayElements(data, nullptr);
    if (!pcm) return -1;
    EAS_I32 frames = 0;
    auto rc = a.EAS_Render(s->data, pcm, c->mixBufferSize, &frames);
    env->ReleaseShortArrayElements(data, pcm, 0);
    return rc == EAS_SUCCESS ? static_cast<jint>(frames * 2) : -1;
}
