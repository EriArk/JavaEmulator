/* SPDX-License-Identifier: GPL-3.0-only */
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_dingoo_DingooCore_##name
#define CORE_TAG "AbyssDingoo"
enum { W = 320, H = 240, RATE = 22050 };
#include "../../handheld/native/frontend.h"

/* The pinned core stores an LZ4 payload length at byte 16 of its 32-byte state header. */
JNIEXPORT jboolean JNICALL JNI_METHOD(checkpoint)(JNIEnv *env,jclass cls,jstring directory,jboolean save) {
    const char *dir = (*env)->GetStringUTFChars(env,directory,NULL);
    if (!dir) return JNI_FALSE;
    bool same = running && save_dir && strcmp(dir,save_dir) == 0;
    (*env)->ReleaseStringUTFChars(env,directory,dir);
    if (!same) return JNI_FALSE;
    char path[4096], staging[4096];
    if (snprintf(path,sizeof(path),"%s/slot.dingstate",save_dir) >= sizeof(path)
            || snprintf(staging,sizeof(staging),"%s.tmp",path) >= sizeof(staging)) return JNI_FALSE;
    pthread_mutex_lock(&core_lock);
    size_t capacity = retro_serialize_size();
    uint8_t *buffer = capacity ? calloc(1,capacity) : NULL;
    bool ok = false;
    if (buffer && save && retro_serialize(buffer,capacity) && capacity >= 32 && memcmp(buffer,"DINGSTAT",8) == 0) {
        uint32_t payload;
        memcpy(&payload,buffer + 16,sizeof(payload));
        if (payload <= capacity - 32) {
            size_t length = 32 + (size_t)payload;
            FILE *f = fopen(staging,"wb");
            if (f) {
                ok = fwrite(buffer,1,length,f) == length && fflush(f) == 0 && fsync(fileno(f)) == 0;
                if (fclose(f) != 0) ok = false;
                ok = ok && rename(staging,path) == 0;
                if (!ok) unlink(staging);
            }
        }
    } else if (buffer && !save) {
        FILE *f = fopen(path,"rb");
        if (f) {
            if (fseek(f,0,SEEK_END) == 0) {
                long length = ftell(f);
                if (length >= 32 && (size_t)length <= capacity && fseek(f,0,SEEK_SET) == 0
                        && fread(buffer,1,length,f) == (size_t)length) {
                    uint32_t payload;
                    memcpy(&payload,buffer + 16,sizeof(payload));
                    if (memcmp(buffer,"DINGSTAT",8) == 0 && payload == (size_t)length - 32)
                        ok = retro_unserialize(buffer,capacity);
                }
            }
            fclose(f);
        }
    }
    free(buffer);
    pthread_mutex_unlock(&core_lock);
    return ok;
}
