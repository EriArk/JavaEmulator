/* SPDX-License-Identifier: GPL-3.0-only */
#include "machine.c"
#include <limits.h>
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_cybiko_CybikoCore_##name
#define CORE_TAG "AbyssCybiko"
#define FRONTEND_DIRECT_CORE
#define FRONTEND_FLASH_IMAGE
#define FRONTEND_XRGB8888
enum { W = 160, H = 100, RATE = 48000 };
#include "../../handheld/native/frontend.h"

static char firmware_dir[PATH_MAX], volume_path[PATH_MAX];
static bool use_classic;
static int last_buttons;
static char typing[65];
static size_t next_char;
static int held_key, modifier, typing_ticks;
JNIEXPORT jboolean JNICALL JNI_METHOD(model)(JNIEnv *env, jclass cls, jboolean original, jstring directory) {
    if (running) return false;
    const char *path = (*env)->GetStringUTFChars(env, directory, NULL);
    if (!path) return false;
    bool fits = snprintf(firmware_dir,sizeof firmware_dir,"%s",path) < sizeof firmware_dir;
    (*env)->ReleaseStringUTFChars(env,directory,path);
    use_classic = original;
    return fits;
}
static bool load_image(const char *path) {
    char boot[PATH_MAX], os[PATH_MAX];
    if (!save_dir || snprintf(volume_path,sizeof volume_path,"%s/volume.bin",save_dir) >= sizeof volume_path ||
            snprintf(boot,sizeof boot,"%s/boot.bin",firmware_dir) >= sizeof boot ||
            snprintf(os,sizeof os,"%s/system.bin",firmware_dir) >= sizeof os) return false;
    last_buttons = 0; held_key = modifier = -1; next_char = typing_ticks = 0; typing[0] = 0;
    return boot_machine(use_classic,boot,os,path,volume_path,"Play.app");
}
static bool flush_image(void) { return save_volume(volume_path); }
static void release_image(void) { }
static const int letters[] = {CYK_A,CYK_B,CYK_C,CYK_D,CYK_E,CYK_F,CYK_G,CYK_H,CYK_I,CYK_J,CYK_K,CYK_L,
    CYK_M,CYK_N,CYK_O,CYK_P,CYK_Q,CYK_R,CYK_S,CYK_T,CYK_U,CYK_V,CYK_W,CYK_X,CYK_Y,CYK_Z};
JNIEXPORT void JNICALL JNI_METHOD(type)(JNIEnv *env,jclass cls,jstring text) {
    const char *value=(*env)->GetStringUTFChars(env,text,NULL);
    if (!value) return;
    pthread_mutex_lock(&core_lock);
    size_t n=0;
    for (const char *p=value; *p && n<sizeof typing-1; ++p)
        if ((*p>='a' && *p<='z') || (*p>='A' && *p<='Z') || (*p>='0' && *p<='9') || *p==' ')
            typing[n++]=*p;
    typing[n]=0; next_char=0;
    pthread_mutex_unlock(&core_lock);
    (*env)->ReleaseStringUTFChars(env,text,value);
}
static void type_next(void) {
    if (typing_ticks>0) { typing_ticks--; return; }
    if (held_key>=0) {
        cybiko_key(&machine,held_key,false);
        if (modifier>=0) machine_key(modifier,false);
        held_key=modifier=-1; typing_ticks=3; return;
    }
    char c=typing[next_char];
    if (!c) return;
    next_char++;
    static const int digits[]={CYC_0,CYC_1,CYC_2,CYC_3,CYC_4,CYC_5,CYC_6,CYC_7,CYC_8,CYC_9};
    int key=CYK_SPACE;
    if (c>='0' && c<='9') {
        if (classic) held_key=digits[c-'0'];
        else { key=letters["PQWERTYUIO"[c-'0']-'A']; modifier=CYK_FN; }
    } else if (c!=' ') {
        if (c<='Z') { key=letters[c-'A']; modifier=CYK_SHIFT; }
        else key=letters[c-'a'];
    }
    if (held_key<0) held_key=classic ? cybiko_classic_key(key) : key;
    if (modifier>=0) machine_key(modifier,true);
    if (held_key>=0) cybiko_key(&machine,held_key,true);
    typing_ticks=5;
}
static void run_machine_frame(void) {
    static const int keys[] = {CYK_ESC,CYK_SPACE,CYK_TAB,CYK_MENU,CYK_UP,CYK_DOWN,CYK_LEFT,CYK_RIGHT,
        CYK_ENTER,CYK_SELECT,CYK_SHIFT,CYK_HELP,CYK_DEL,CYK_INS,CYK_F1,CYK_F2};
    poll();
    for (int i=0; i<16; ++i)
        if ((last_buttons ^ polled_buttons) & (1 << i)) machine_key(keys[i], (polled_buttons & (1 << i)) != 0);
    last_buttons = polled_buttons;
    type_next();
    static uint8_t lcd[W*H];
    static uint32_t rgb[W*H];
    int16_t stereo[1600];
    frame_machine(lcd,stereo);
    for (int i=0; i<W*H; i++) rgb[i]=lcd[i]*0x010101u;
    video(rgb,W,H,W*sizeof(*rgb)); pcm(stereo,800);
}
