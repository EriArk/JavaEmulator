/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdbool.h>
#include <string.h>

static int screen_width, screen_height;
static bool color;
extern bool gameking_environment(unsigned, void *, const char *);
static bool load_game(const char *, const char *);
#define W screen_width
#define H screen_height
#define FRONTEND_PIXEL_CAPACITY (160 * 160)
#define FRONTEND_XRGB8888
#define FRONTEND_FRAME_PERIOD_NS (color ? 25439996LL : 16666667LL)
#define FRONTEND_OPEN_CARTRIDGE load_game
#define FRONTEND_ENVIRONMENT(cmd, data) gameking_environment(cmd, data, save_dir)
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_gameking_GameKingCore_##name
#define CORE_TAG "AbyssGameKing"
enum { RATE = 48000 };
#include "../../handheld/native/frontend.h"

static bool load_game(const char *path, const char *saves) {
    (void)saves;
    const char *base = strrchr(path, '/');
    base = base ? base + 1 : path;
    bool hartung = !strcmp(base, "gmaster.cmd");
    bool epoch = !strcmp(base, "gamepock.cmd");
    color = !strcmp(base, "gamekin3.cmd");
    screen_width = epoch ? 75 : hartung ? 61 : color ? 160 : 48;
    screen_height = epoch || hartung ? 64 : color ? 160 : 32;
    return retro_load_game(&(struct retro_game_info){.path = path});
}

#define FRONTEND_STATE_FILE "slot.gamekingstate"
#include "../../handheld/native/checkpoint.h"
