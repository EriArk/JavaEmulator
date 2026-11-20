/* SPDX-License-Identifier: GPL-3.0-only */
/* Keep the upstream libretro frame loop, input mapping, orientation and audio resampler. */
#include "libretro/libretro.c"
#include <unistd.h>

extern int abyss_card_dirty(gp32_t *);
extern int abyss_card_present(gp32_t *);
static bool card_session, pending_write;
static char card_path[4096];

static bool commit_file(const char *temp, const char *path) {
    FILE *f = fopen(temp, "rb");
    if (!f) return false;
    bool ok = fsync(fileno(f)) == 0;
    fclose(f);
    return ok && rename(temp, path) == 0;
}
bool abyss_gp32_load(const char *path, const char *dir) {
    card_session = has_ext(path, "smc");
    pending_write = false;
    if (snprintf(card_path, sizeof(card_path), "%s/card.smc", dir) >= sizeof(card_path)) return false;
    if (has_ext(path, "bin")) {
        smartmedia_save_path[0] = 0;
        cycle_accum = 0;
        emu = create_core_with_optional_bios(path);
        return emu != NULL;
    }
    if (card_session && access(card_path, F_OK) == 0) path = card_path;
    bool ok = retro_load_game(&(struct retro_game_info){.path = path});
    smartmedia_save_path[0] = 0; /* The host commits changed cards atomically. */
    return ok;
}
bool abyss_gp32_flush(void) {
    if (!card_session || !emu || (!pending_write && !abyss_card_dirty(emu))) return true;
    char temp[4096];
    if (snprintf(temp, sizeof(temp), "%s.tmp", card_path) >= sizeof(temp)) return false;
    pending_write = true;
    if (gp32_save_smartmedia(emu, temp) != GP32_OK || !commit_file(temp, card_path)) {
        unlink(temp);
        return false;
    }
    pending_write = false;
    return true;
}
bool abyss_gp32_checkpoint(const char *dir, bool save) {
    char slot[4096], temp[4096];
    if (!emu || snprintf(slot, sizeof(slot), "%s/slot.gp32st", dir) >= sizeof(slot)) return false;
    if (!save) {
        bool ok = gp32_load_state(emu, slot) == GP32_OK;
        if (ok) {
            gp32_video_effects_reset(&effects);
            gp32_audio_resampler_reset(&audio_resampler);
            if (card_session && abyss_card_present(emu)) pending_write = true;
        }
        return ok;
    }
    if (snprintf(temp, sizeof(temp), "%s.tmp", slot) >= sizeof(temp)) return false;
    if (gp32_save_state(emu, temp) == GP32_OK && commit_file(temp, slot)) return true;
    unlink(temp);
    return false;
}
