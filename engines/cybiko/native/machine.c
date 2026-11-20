/* SPDX-License-Identifier: GPL-3.0-only */
#include "cybiko.h"
#include <errno.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <zlib.h>

static cybiko_t machine;
static uint8_t rom[CY_ROM_BUF], flash[CY_AT45_SIZE], ram[CY_RAM_SIZE], disk[CY_RAMDISK_SIZE];
static bool classic, dirty;
static float frequency, duty, phase;
static int64_t wall_time(void *ctx) { return time(NULL); }
static void tone(void *ctx, float hz, float fraction) {
    frequency = hz;
    duty = fraction > 0 && fraction < 1 ? fraction : 0.5f;
}
static long read_image(const char *path, uint8_t *out, size_t capacity) {
    FILE *f = fopen(path, "rb");
    if (!f) return -1;
    size_t n = fread(out, 1, capacity, f);
    bool ok = !ferror(f) && fgetc(f) == EOF;
    fclose(f);
    return ok ? (long)n : -1;
}
static uint16_t be16(const uint8_t *p) { return (p[0] << 8) | p[1]; }
static void put16(uint8_t *p, unsigned v) { p[0] = v >> 8; p[1] = v; }
static bool occupied(const uint8_t *p) {
    return (p[8] & 0x80) && crc32(0, p + 8, 254) == ((uint32_t)be16(p) << 16 | be16(p + 2));
}
static void seal_page(uint8_t *p, unsigned sequence) {
    uint32_t crc = crc32(0, p + 8, 254);
    put16(p, crc >> 16); put16(p + 2, crc); put16(p + 4, sequence);
    unsigned check = 0xaf17 ^ be16(p) ^ be16(p + 2) ^ be16(p + 4);
    p[6] = check; p[7] = check >> 8;
}
/* Classic DataFlash uses 264-byte physical pages, unlike the Xtreme RAM disk. */
static bool install_classic(const uint8_t *game, size_t bytes, const char *name) {
    int id = -1, sequence = 0;
    for (int i = 5; i < CY_AT45_PAGES; i++) {
        uint8_t *p = flash + i * CY_AT45_PAGE;
        if (occupied(p) && be16(p + 4) > sequence) sequence = be16(p + 4);
    }
    for (int i = 5, part = 0; i < CY_AT45_PAGES && bytes; i++) {
        uint8_t *p = flash + i * CY_AT45_PAGE, *b = p + 8;
        // A live system page may have a stale checksum. Never reuse it.
        if (b[0] & 0x80) continue;
        if (id < 0) id = i - 5;
        unsigned off = part == 0 ? 0x4e : 6, count = bytes < 254 - off ? bytes : 254 - off;
        memset(p, 0xff, 264);
        memset(b, 0, 254);
        b[0] = 0x80; b[1] = count;
        put16(b + 2, id); put16(b + 4, part);
        if (part == 0) memcpy(b + 7, name, strlen(name));
        memcpy(b + off, game, count);
        seal_page(p, ++sequence);
        game += count; bytes -= count; part++;
    }
    if (bytes != 0) return false;
    return true;
}
static bool boot_machine(bool is_classic, const char *boot, const char *os, const char *game,
        const char *save, const char *name) {
    classic = is_classic;
    if (strlen(name) > 56 || read_image(boot, rom, CY_ROM_SIZE) != CY_ROM_SIZE) return false;
    memcpy(rom + CY_ROM_SIZE, rom, CY_ROM_SIZE);
    memset(ram, 0, sizeof ram); memset(disk, 0, sizeof disk);
    long n = read_image(os, flash, sizeof flash);
    if (classic && n == CY_AT45_DB041_SIZE) cybiko_at45_format_free(flash, n / 264);
    else if (n != (classic ? CY_AT45_SIZE : CY_FLASH_SIZE)) return false;
    frequency = duty = phase = 0;
    size_t size = classic ? CY_AT45_SIZE : sizeof disk;
    dirty = false;
    if (access(save, F_OK) == 0) {
        if (read_image(save, classic ? flash : disk, size) != (long)size) return false;
    } else {
        if (errno != ENOENT) return false;
        uint8_t *app = malloc(size);
        if (!app) return false;
        long length = read_image(game, app, size);
        bool ok = length >= 6 && app[0] == 'C' && app[1] == 'y' &&
            (classic ? install_classic(app, length, name) : cy_disk_write(disk, sizeof disk, name, app, length, 0) == 0);
        free(app);
        if (!ok) return false;
        dirty = true;
    }
    cy_host_t host = { .wall_time = wall_time, .speaker = tone };
    if (classic) cybiko_init_classic(&machine, &host, rom, flash, ram);
    else {
        cybiko_init(&machine, &host, rom, flash, ram);
        cybiko_set_disk(&machine, disk, sizeof disk);
        memcpy(ram, disk, sizeof disk);
    }
    return true;
}
static bool save_volume(const char *path) {
    if (classic) {
        uint32_t bits[CY_AT45_PAGES / 32];
        if (cybiko_at45_take_dirty(&machine, bits)) dirty = true;
    } else {
        for (size_t i = 0; i < sizeof machine.disk_dirty / sizeof(uint32_t); i++)
            if (machine.disk_dirty[i]) dirty = true;
        memset(machine.disk_dirty, 0, sizeof machine.disk_dirty);
    }
    if (!dirty) return true;
    char tmp[4096];
    if (snprintf(tmp, sizeof tmp, "%s.tmp", path) >= sizeof tmp) return false;
    FILE *f = fopen(tmp, "wb");
    if (!f) return false;
    size_t length = classic ? sizeof flash : sizeof disk;
    bool ok = fwrite(classic ? flash : disk, 1, length, f) == length && !fflush(f) && !fsync(fileno(f));
    if (fclose(f)) ok = false;
    if (ok && rename(tmp, path) == 0) { dirty = false; return true; }
    unlink(tmp);
    return false;
}
static void frame_machine(uint8_t *pixels, int16_t *stereo) {
    unsigned clock = cybiko_clock_hz(&machine);
    for (int i = 0; i < 800; i += 40) {
        cybiko_run(&machine, clock / 1200);
        for (int j = i; j < i + 40; j++) {
            stereo[j * 2] = stereo[j * 2 + 1] = frequency > 0 ? (phase < duty ? 8192 : -8192) : 0;
            phase += frequency / 48000;
            phase -= floorf(phase);
        }
    }
    cybiko_lcd_render(&machine, pixels);
}
static void machine_key(int xt_key, bool down) {
    int key = classic ? cybiko_classic_key(xt_key) : xt_key;
    if (key >= 0) cybiko_key(&machine, key, down);
}
