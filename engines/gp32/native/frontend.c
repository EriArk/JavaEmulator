/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdbool.h>
#include <string.h>
#include <stdio.h>

static bool core_environment(unsigned command, void *data);
#define FRONTEND_ENVIRONMENT core_environment
#define FRONTEND_FLASH_IMAGE
#define FRONTEND_XRGB8888
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_gp32_Gp32Core_##name
#define CORE_TAG "AbyssGP32"
enum { W = 320, H = 240, RATE = 44100 };
#include "../../handheld/native/frontend.h"

extern bool abyss_gp32_load(const char *, const char *);
extern bool abyss_gp32_flush(void), abyss_gp32_checkpoint(const char *, bool);
static bool card;
static char firmware_dir[4096];
static bool core_environment(unsigned command, void *data) {
    if (command == 9 && save_dir) {
        if (snprintf(firmware_dir, sizeof(firmware_dir), "%s/../../system", save_dir) >= sizeof(firmware_dir)) return false;
        *(const char **)data = firmware_dir;
        return true;
    }
    if (command == 15) {
        struct variable { const char *key; const char *value; };
        struct variable *v = data;
        if (!strcmp(v->key, "gp32emu_boot_mode")) { v->value = card ? "auto" : "direct_hle"; return true; }
    }
    return false;
}
static bool load_image(const char *path) {
    const char *dot = strrchr(path, '.');
    card = dot && !strcmp(dot, ".smc");
    return abyss_gp32_load(path, save_dir);
}
static bool flush_image(void) { return abyss_gp32_flush(); }
static void release_image(void) {}
JNIEXPORT jboolean JNICALL JNI_METHOD(checkpoint)(JNIEnv *env, jclass cls, jstring dir, jboolean save) {
    const char *path = (*env)->GetStringUTFChars(env, dir, NULL);
    if (!path) return false;
    pthread_mutex_lock(&core_lock);
    bool ok = running && save_dir && !strcmp(path, save_dir) && abyss_gp32_checkpoint(path, save);
    pthread_mutex_unlock(&core_lock);
    (*env)->ReleaseStringUTFChars(env, dir, path);
    return ok;
}
