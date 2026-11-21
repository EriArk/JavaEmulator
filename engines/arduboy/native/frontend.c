/* SPDX-License-Identifier: GPL-3.0-only */
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_arduboy_ArduboyCore_##name
#define CORE_TAG "AbyssArduboy"
#define FRONTEND_XRGB8888
#define FRONTEND_MEMORY_ROM
#define FRONTEND_FRAME_PERIOD_NS 16666667LL
enum { W = 128, H = 64, RATE = 50000 };
#include "../../handheld/native/frontend.h"

extern bool ardens_checkpoint(const char *directory, bool save);
JNIEXPORT jboolean JNICALL JNI_METHOD(checkpoint)(JNIEnv *env, jclass cls, jboolean save) {
    pthread_mutex_lock(&core_lock);
    bool ok = running && save_dir && ardens_checkpoint(save_dir, save);
    pthread_mutex_unlock(&core_lock);
    return ok;
}
