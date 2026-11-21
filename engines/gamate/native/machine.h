/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

enum { GAMATE_WIDTH = 160, GAMATE_HEIGHT = 150, GAMATE_RATE = 48000 };
bool gamate_open(const char *cartridge, const char *bios);
void gamate_close(void);
/* One LCD refresh produces at most 800 stereo frames at 48 kHz. */
size_t gamate_run(uint8_t held_keys, uint32_t *pixels, int16_t *stereo);
size_t gamate_state_size(void);
bool gamate_state(void *data, size_t size, bool save);
