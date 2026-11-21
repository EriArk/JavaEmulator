/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdbool.h>
#include <android/asset_manager_jni.h>
#include <limits.h>
static bool start_game(const char *path, const char *directory);
#define W 240
#define H 320
#define FRONTEND_FRAME_PERIOD_NS 33333333LL
#define FRONTEND_MEMORY_ROM
#define FRONTEND_OPEN_CARTRIDGE start_game
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_mre_MreCore_##name
#define CORE_TAG "AbyssMRE"
enum { RATE = 44100 };
#include "../../handheld/native/frontend.h"

static bool start_game(const char *path, const char *directory) {
    // MRE runs in a dedicated process. Its virtual drives live below this game's directory.
    return directory && chdir(directory) == 0 && load_rom(path);
}

static AAssetManager *assets;

JNIEXPORT void JNICALL JNI_METHOD(assets)(JNIEnv *env, jclass cls, jobject manager) {
    assets = AAssetManager_fromJava(env, manager);
}

extern void *tsf_load_memory(const void *data, int length);

void *mre_load_soundfont(void) {
    if (!assets) return NULL;
    AAsset *asset = AAssetManager_open(assets, "resources/generaluser-gs.sf2", AASSET_MODE_BUFFER);
    if (!asset) return NULL;
    off64_t length = AAsset_getLength64(asset);
    const void *data = AAsset_getBuffer(asset);
    void *synth = data && length > 0 && length <= INT_MAX ? tsf_load_memory(data, (int)length) : NULL;
    AAsset_close(asset);
    return synth;
}

extern void mre_key(unsigned event, int code);
extern void mre_pen(unsigned event, int x, int y);

JNIEXPORT void JNICALL JNI_METHOD(key)(JNIEnv *env, jclass cls, jint code, jboolean down) {
    pthread_mutex_lock(&core_lock);
    if (running) mre_key(down ? 2 : 1, code);
    pthread_mutex_unlock(&core_lock);
}

JNIEXPORT void JNICALL JNI_METHOD(pen)(JNIEnv *env, jclass cls, jint event, jint x, jint y) {
    pthread_mutex_lock(&core_lock);
    if (running) mre_pen(event, x, y);
    pthread_mutex_unlock(&core_lock);
}

#define FRONTEND_STATE_FILE "slot.mrestate"
#include "../../handheld/native/checkpoint.h"
