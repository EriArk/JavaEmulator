/* SPDX-License-Identifier: GPL-3.0-only */
#include "machine.h"
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_gamate_GamateCore_##name
#define CORE_TAG "AbyssGamate"
#define FRONTEND_XRGB8888
#define FRONTEND_DIRECT_CORE
#define FRONTEND_OPEN_CARTRIDGE load_cartridge
#define FRONTEND_CLOSE_CARTRIDGE gamate_close
#define FRONTEND_FRAME_PERIOD_NS 16444845LL
enum { W = GAMATE_WIDTH, H = GAMATE_HEIGHT, RATE = GAMATE_RATE };
static bool load_cartridge(const char *game, const char *saves);
#include "../../handheld/native/frontend.h"

static bool load_cartridge(const char *game, const char *saves) {
    char path[4096];
    int length = snprintf(path, sizeof(path), "%s/../../system/boot.rom", saves);
    return length > 0 && (size_t)length < sizeof(path) && gamate_open(game, path);
}

static void run_machine_frame(void) {
    static const int ids[] = {4,5,6,7,8,0,3,2};
    static uint32_t lcd[W * H];
    int16_t audio_data[800 * 2];
    unsigned keys = 0;
    poll();
    for (unsigned bit = 0; bit < 8; bit++)
        if (input(0,1,0,ids[bit])) keys |= 1u << bit;
    size_t frames = gamate_run(keys,lcd,audio_data);
    video(lcd,W,H,W*sizeof(*lcd));
    pcm(audio_data,frames);
}

size_t retro_serialize_size(void) { return gamate_state_size(); }
bool retro_serialize(void *p,size_t size) { return gamate_state(p,size,true); }
bool retro_unserialize(const void *p,size_t size) { return gamate_state((void *)p,size,false); }
#define FRONTEND_STATE_FILE "slot.gamatestate"
#include "../../handheld/native/checkpoint.h"
