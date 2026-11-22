/* SPDX-License-Identifier: GPL-3.0-only */
#include <stdbool.h>
#include <stdint.h>
#include <dlfcn.h>
static bool open_game(const char *, const char *);
static void close_game(void);
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_pokitto_PokittoCore_##name
#define CORE_TAG "AbyssPokitto"
#define FRONTEND_DIRECT_CORE
#define FRONTEND_OPEN_CARTRIDGE open_game
#define FRONTEND_CLOSE_CARTRIDGE close_game
enum { W = 220, H = 176, RATE = 48000 };
#include "../../handheld/native/frontend.h"

// The hardware has process-global registers. A new instance starts with power-on values.
static void *machine;
static bool (*step)(int, uint16_t **, const int16_t **, size_t *);
static void (*unload)(void);
static bool open_game(const char *path, const char *dir) {
    machine = dlopen("libpokittomachine.so", RTLD_NOW | RTLD_LOCAL);
    if (!machine) return false;
    bool (*load)(const char *, const char *) = dlsym(machine, "pokitto_open");
    step = dlsym(machine, "pokitto_frame");
    unload = dlsym(machine, "pokitto_close");
    return load && step && unload && load(path, dir);
}
static void close_game(void) {
    if (machine) {
        if (unload) unload();
        dlclose(machine);
        machine = NULL;
        unload = NULL;
    }
}
static void run_machine_frame(void) {
    poll();
    uint16_t *lcd = NULL;
    const int16_t *sound = NULL;
    size_t frames = 0;
    if (!step(polled_buttons, &lcd, &sound, &frames)) { atomic_store(&exited, true); return; }
    video(lcd, W, H, W * 2);
    pcm(sound, frames);
}
