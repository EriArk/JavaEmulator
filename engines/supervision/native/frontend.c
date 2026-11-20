/* SPDX-License-Identifier: GPL-3.0-only */
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_supervision_SupervisionCore_##name
#define CORE_TAG "AbyssSupervision"
#define FRONTEND_MEMORY_ROM
enum { W = 160, H = 160, RATE = 44100 };
#include "../../handheld/native/frontend.h"

JNIEXPORT jboolean JNICALL JNI_METHOD(checkpoint)(JNIEnv *env, jclass cls, jstring directory, jboolean save) {
    const char *dir = (*env)->GetStringUTFChars(env, directory, NULL);
    if (!dir) return false;
    char slot[4096], tmp[4096];
    bool ok = false;
    pthread_mutex_lock(&core_lock);
    if (running && save_dir && !strcmp(dir, save_dir) &&
            snprintf(slot, sizeof(slot), "%s/slot.svstate", dir) < sizeof(slot) &&
            snprintf(tmp, sizeof(tmp), "%s.tmp", slot) < sizeof(tmp)) {
        size_t size = retro_serialize_size();
        unsigned char *state = size ? calloc(1, size) : NULL;
        if (state && save && retro_serialize(state, size)) {
            FILE *file = fopen(tmp, "wb");
            if (file) {
                ok = fwrite(state, 1, size, file) == size && !fflush(file) && !fsync(fileno(file));
                if (fclose(file)) ok = false;
                ok = ok && !rename(tmp, slot);
                if (!ok) unlink(tmp);
            }
        } else if (state && !save) {
            FILE *file = fopen(slot, "rb");
            if (file) {
                ok = fread(state, 1, size, file) == size && fgetc(file) == EOF && !ferror(file)
                        && retro_unserialize(state, size);
                fclose(file);
            }
        }
        free(state);
    }
    pthread_mutex_unlock(&core_lock);
    (*env)->ReleaseStringUTFChars(env, directory, dir);
    return ok;
}
