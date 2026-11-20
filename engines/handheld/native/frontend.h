/* SPDX-License-Identifier: GPL-3.0-only */
#include <aaudio/AAudio.h>
#include <jni.h>
#include <stdarg.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdio.h>
#include <unistd.h>
#include <android/log.h>


struct retro_game_info { const char *path; const void *data; size_t size; const char *meta; };
extern unsigned retro_api_version(void);
extern void retro_init(void), retro_deinit(void), retro_run(void), retro_unload_game(void);
extern bool retro_load_game(const struct retro_game_info *);
extern size_t retro_serialize_size(void);
extern bool retro_serialize(void *,size_t), retro_unserialize(const void *,size_t);
static pthread_mutex_t core_lock = PTHREAD_MUTEX_INITIALIZER;
extern void retro_set_environment(bool (*)(unsigned, void *));
extern void retro_set_video_refresh(void (*)(const void *,unsigned,unsigned,size_t));
extern void retro_set_audio_sample(void (*)(int16_t,int16_t));
extern void retro_set_audio_sample_batch(size_t (*)(const int16_t *,size_t));
extern void retro_set_input_poll(void (*)(void));
extern void retro_set_input_state(int16_t (*)(unsigned,unsigned,unsigned,unsigned));

static char *save_dir;
static pthread_t runner;
static pthread_mutex_t frame_lock = PTHREAD_MUTEX_INITIALIZER;
static uint32_t pixels[W * H];
static bool fresh, running;
static atomic_bool stopping, paused, exited;
static atomic_int buttons, pressed_since_poll, speed;
static int polled_buttons;
static unsigned char minimum_ticks[16];
static AAudioStream *audio;

#ifdef FRONTEND_FLASH_IMAGE
static bool load_image(const char *path);
static bool flush_image(void);
static void release_image(void);
#endif

static void core_log(int level, const char *fmt, ...) {
    if (level < 1) return;
    va_list args;
    va_start(args,fmt);
    __android_log_vprint(level > 2 ? ANDROID_LOG_ERROR : level == 2 ? ANDROID_LOG_WARN : ANDROID_LOG_INFO,
            CORE_TAG, fmt, args);
    va_end(args);
}
static bool environment(unsigned command, void *data) {
#ifdef FRONTEND_ENVIRONMENT
    if (FRONTEND_ENVIRONMENT(command, data)) return true;
#endif
    switch (command) {
        case 7: atomic_store(&exited, true); return true;
#ifdef FRONTEND_XRGB8888
        case 10: return *(unsigned *)data == 1;
#else
        case 10: return *(unsigned *)data == 2; /* RGB565 */
#endif
        case 11: return true;
        case 27: *(void (**)(int,const char *,...))data = core_log; return true;
        case 31: *(const char **)data = save_dir; return true;
        default: return false;
    }
}

static void video(const void *data, unsigned w, unsigned h, size_t pitch) {
#ifdef FRONTEND_XRGB8888
    typedef uint32_t Pixel;
#else
    typedef uint16_t Pixel;
#endif
    if (!data || w != W || h != H || pitch < W * sizeof(Pixel)) return;
    pthread_mutex_lock(&frame_lock);
    for (unsigned y = 0; y < h; ++y) {
        const Pixel *row = (const Pixel *)((const char *)data + y * pitch);
        for (unsigned x = 0; x < w; ++x) {
#ifdef FRONTEND_XRGB8888
            pixels[y * W + x] = 0xff000000u | row[x];
#else
            unsigned c = row[x], r = c >> 11, g = (c >> 5) & 63, b = c & 31;
            pixels[y * W + x] = 0xff000000u | ((r * 255 / 31) << 16) | ((g * 255 / 63) << 8) | b * 255 / 31;
#endif
        }
    }
    fresh = true;
    pthread_mutex_unlock(&frame_lock);
}

static bool start_audio(void) {
    AAudioStreamBuilder *builder = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return false;
    AAudioStreamBuilder_setSampleRate(builder, RATE);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_GAME);
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &audio);
    AAudioStreamBuilder_delete(builder);
    if (result == AAUDIO_OK && AAudioStream_requestStart(audio) == AAUDIO_OK) return true;
    if (audio) AAudioStream_close(audio);
    audio = NULL;
    return false;
}

static size_t pcm(const int16_t *samples, size_t frames) {
    if (!audio || atomic_load(&paused) || frames == 0) return frames;
    int percent = atomic_load(&speed);
    size_t output = frames * 100 / percent;
    int16_t converted[1024 * 2];
    for (size_t i = 0; i < output;) {
        size_t n = output - i > 1024 ? 1024 : output - i;
        for (size_t j = 0; j < n; ++j) {
            size_t index = (i + j) * percent / 100;
            converted[j * 2] = samples[index * 2];
            converted[j * 2 + 1] = samples[index * 2 + 1];
        }
        int written = AAudioStream_write(audio, converted, n, 30000000);
        if (written <= 0) {
            if (written == AAUDIO_ERROR_DISCONNECTED) { AAudioStream_close(audio); audio = NULL; start_audio(); }
            break;
        }
        i += written;
    }
    return frames;
}
#ifdef FRONTEND_BUFFER_SAMPLES
static int16_t sample_buffer[512 * 2];
static size_t buffered;
static void flush_samples(void) {
    pcm(sample_buffer, buffered);
    buffered = 0;
}
static void sample(int16_t left, int16_t right) {
    sample_buffer[2 * buffered] = left;
    sample_buffer[2 * buffered + 1] = right;
    if (++buffered == 512) flush_samples();
}
#else
static void sample(int16_t l, int16_t r) { int16_t s[] = {l,r}; pcm(s,1); }
#endif
static void poll(void) {
    // A touchscreen tap can begin and end between two emulated frames.
    int pressed = atomic_exchange(&pressed_since_poll, 0);
    polled_buttons = atomic_load(&buttons);
    for (int id = 0; id < 16; ++id) {
        if (pressed & (1 << id)) minimum_ticks[id] = 4;
        if (minimum_ticks[id]) {
            polled_buttons |= 1 << id;
            --minimum_ticks[id];
        }
    }
}
static int16_t input(unsigned port, unsigned device, unsigned index, unsigned id) {
    return port == 0 && device == 1 && index == 0 && id < 16 && (polled_buttons & (1 << id));
}
static int64_t now(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000000LL + ts.tv_nsec;
}
static void *loop(void *arg) {
    (void)arg;
    int64_t deadline = now();
    bool playing = false;
#ifdef FRONTEND_FLASH_IMAGE
    int64_t next_flush = deadline + 2000000000LL;
#endif
    while (!atomic_load(&stopping) && !atomic_load(&exited)) {
        if (atomic_load(&paused)) {
            if (playing && audio) { AAudioStream_requestPause(audio); AAudioStream_requestFlush(audio); }
            playing = false;
            memset(minimum_ticks, 0, sizeof(minimum_ticks));
            nanosleep(&(struct timespec){0, 5000000}, NULL);
            deadline = now();
            continue;
        }
        if (!playing) {
            if (audio) AAudioStream_requestStart(audio); else start_audio();
            playing = true;
        }
        pthread_mutex_lock(&core_lock);
        retro_run();
#ifdef FRONTEND_BUFFER_SAMPLES
        flush_samples();
#endif
#ifdef FRONTEND_FLASH_IMAGE
        if (now() >= next_flush) {
            if (!flush_image()) __android_log_print(ANDROID_LOG_ERROR, CORE_TAG, "Could not save flash");
            next_flush = now() + 2000000000LL;
        }
#endif
        pthread_mutex_unlock(&core_lock);
#ifdef FRONTEND_FRAME_PERIOD_NS
        deadline += FRONTEND_FRAME_PERIOD_NS * 100 / atomic_load(&speed);
#else
        deadline += 100000000000LL / (60 * atomic_load(&speed));
#endif
        int64_t wait = deadline - now();
        if (wait > 0) nanosleep(&(struct timespec){0, wait}, NULL);
        else if (wait < -50000000) deadline = now();
    }
    if (audio) { AAudioStream_close(audio); audio = NULL; }
    return NULL;
}

#ifdef FRONTEND_MEMORY_ROM
static bool load_rom(const char *path) {
    FILE *file = fopen(path, "rb");
    if (!file) return false;
    bool loaded = false;
    if (!fseek(file, 0, SEEK_END)) {
        long size = ftell(file);
        if (size > 0 && (unsigned long)size <= UINT32_MAX && !fseek(file, 0, SEEK_SET)) {
            void *rom = malloc(size);
            if (rom && fread(rom, 1, size, file) == (size_t)size)
                loaded = retro_load_game(&(struct retro_game_info){.path=path, .data=rom, .size=size});
            free(rom); /* The core copies non-persistent game data during load. */
        }
    }
    fclose(file);
    return loaded;
}
#endif

JNIEXPORT jboolean JNICALL JNI_METHOD(start)(JNIEnv *env, jclass cls, jstring game, jstring saves) {
    if (running || retro_api_version() != 1) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, game, NULL);
    if (!path) return JNI_FALSE;
    const char *dir = (*env)->GetStringUTFChars(env, saves, NULL);
    if (!dir) { (*env)->ReleaseStringUTFChars(env,game,path); return JNI_FALSE; }
    save_dir = strdup(dir);
    (*env)->ReleaseStringUTFChars(env, saves, dir);
    atomic_store(&buttons,0); atomic_store(&pressed_since_poll,0); polled_buttons = 0;
    memset(minimum_ticks, 0, sizeof(minimum_ticks));
    atomic_store(&speed,100); atomic_store(&paused,true);
    atomic_store(&stopping,false); atomic_store(&exited,false); fresh = false;
    retro_set_environment(environment); retro_set_video_refresh(video);
    retro_set_audio_sample(sample); retro_set_audio_sample_batch(pcm);
    retro_set_input_poll(poll); retro_set_input_state(input);
    retro_init();
#ifdef FRONTEND_FLASH_IMAGE
    bool loaded = load_image(path);
#elif defined(FRONTEND_MEMORY_ROM)
    bool loaded = load_rom(path);
#else
    bool loaded = retro_load_game(&(struct retro_game_info){.path=path});
#endif
    (*env)->ReleaseStringUTFChars(env, game, path);
    if (loaded && pthread_create(&runner,NULL,loop,NULL) == 0) { running = true; return JNI_TRUE; }
    if (loaded) retro_unload_game();
#ifdef FRONTEND_FLASH_IMAGE
    release_image();
#endif
    retro_deinit(); free(save_dir); save_dir = NULL;
    return JNI_FALSE;
}
JNIEXPORT void JNICALL JNI_METHOD(stop)(JNIEnv *env,jclass cls) {
    if (!running) return;
    atomic_store(&stopping,true); pthread_join(runner,NULL);
#ifdef FRONTEND_FLASH_IMAGE
    flush_image();
    release_image();
#endif
    retro_unload_game(); retro_deinit(); free(save_dir); save_dir = NULL; running = false;
}
JNIEXPORT void JNICALL JNI_METHOD(pause)(JNIEnv *env,jclass cls,jboolean value) {
    atomic_store(&paused,value);
    if (value) atomic_store(&pressed_since_poll,0);
}
JNIEXPORT void JNICALL JNI_METHOD(speed)(JNIEnv *env,jclass cls,jint value) { atomic_store(&speed,value < 10 ? 10 : value > 1000 ? 1000 : value); }
JNIEXPORT void JNICALL JNI_METHOD(button)(JNIEnv *env,jclass cls,jint id,jboolean down) {
    if (id < 0 || id >= 16) return;
    if (down) {
        atomic_fetch_or(&buttons,1 << id);
        atomic_fetch_or(&pressed_since_poll,1 << id);
    } else atomic_fetch_and(&buttons,~(1 << id));
}
JNIEXPORT jboolean JNICALL JNI_METHOD(exited)(JNIEnv *env,jclass cls) { return atomic_load(&exited); }
JNIEXPORT jboolean JNICALL JNI_METHOD(frame)(JNIEnv *env,jclass cls,jintArray target) {
    if ((*env)->GetArrayLength(env,target) != W * H) return JNI_FALSE;
    pthread_mutex_lock(&frame_lock);
    bool copied = fresh;
    if (fresh) (*env)->SetIntArrayRegion(env,target,0,W*H,(jint *)pixels);
    fresh = false;
    pthread_mutex_unlock(&frame_lock);
    return copied;
}
