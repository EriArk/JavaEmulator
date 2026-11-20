/* SPDX-License-Identifier: GPL-3.0-only */
#define FRONTEND_FLASH_IMAGE
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_piece_PieceCore_##name
#define CORE_TAG "AbyssPiece"
enum { W = 128, H = 88, RATE = 32000 };
#include "../../handheld/native/frontend.h"
#include "flash.h"

JNIEXPORT jboolean JNICALL JNI_METHOD(flush)(JNIEnv *env, jclass cls) {
    if (!running) return JNI_TRUE;
    pthread_mutex_lock(&core_lock);
    bool ok = flush_image();
    pthread_mutex_unlock(&core_lock);
    return ok;
}
