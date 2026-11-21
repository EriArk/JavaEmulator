/* SPDX-License-Identifier: GPL-3.0-only
 * LCD behavior adapted from MAME gamate_v.cpp (BSD-3-Clause).
 * Board and cartridge behavior follows MAME's Gamate and cartridge drivers.
 * See the component notices in bridge/src/main/assets/licenses.
 */
#include "machine.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#define CHIPS_IMPL
#include "m6502.h"
#include "ay38910.h"

typedef struct {
    m6502_t cpu;
    ay38910_t psg;
    uint64_t pins;
    uint32_t cycles;
    float audio_dc[2];
    uint8_t ram[1024], planes[2][8192];
    uint16_t vaddr;
    uint8_t plane, lcd, scroll_x, scroll_y, low_bank, high_bank, card_present;
    uint8_t serial_bit, serial_byte, serial_position;
    bool serial_ready, unlocked, serial_failed;
} Machine;
static Machine m;
static uint8_t bios[4096], *cart;
static size_t cart_size;
static uint32_t identity;
static uint8_t joystick;
/* Presentation state, measured from the first cartridge opcode fetch. */
static unsigned cartridge_frames;
bool gamate_artwork_ready(void) { return cartridge_frames >= 90; }

static uint32_t hash(const void *data, size_t length, uint32_t seed) {
    const uint8_t *p = data;
    for (size_t i = 0; i < length; i++) seed = (seed ^ p[i]) * 16777619u;
    return seed;
}

void gamate_close(void) {
    free(cart);
    cart = NULL;
    cart_size = 0;
}

bool gamate_open(const char *path, const char *boot) {
    gamate_close();
    FILE *f = fopen(boot, "rb");
    if (!f) return false;
    bool ok = fread(bios, 1, sizeof(bios), f) == sizeof(bios) && fgetc(f) == EOF && !ferror(f);
    fclose(f);
    if (!ok || !(f = fopen(path, "rb"))) return false;
    long n = fseek(f, 0, SEEK_END) == 0 ? ftell(f) : -1;
    if (n >= 16384 && n <= 256 * 16384 && n % 16384 == 0 && fseek(f, 0, SEEK_SET) == 0) {
        cart = malloc(n);
        if (cart && fread(cart, 1, n, f) == (size_t)n) cart_size = n;
    }
    fclose(f);
    if (!cart_size) { gamate_close(); return false; }
    memset(&m, 0, sizeof(m));
    memset(m.ram, 0xff, sizeof(m.ram));
    m.high_bank = cart_size <= 32768 ? 1 : 0;
    cartridge_frames = 0;
    m.scroll_y = 10;
    m.pins = m6502_init(&m.cpu, &(m6502_desc_t){0});
    ay38910_init(&m.psg, &(ay38910_desc_t){.tick_hz=1108250, .sound_hz=GAMATE_RATE, .magnitude=0.30f});
    identity = hash(cart, cart_size, hash(bios, sizeof(bios), 2166136261u));
    return true;
}

static void step_vram(void) {
    m.vaddr = (m.vaddr + ((m.lcd & 0x40) ? 32 : 1)) & 8191;
}

static uint8_t read_bus(uint16_t a) {
    if (a < 0x2000) return m.ram[a & 1023];
    if (a >= 0xe000) return bios[a & 4095];
    if (a >= 0x6000) {
        if (!m.unlocked) {
            if (!m.serial_ready) return 0;
            unsigned bit = (0x47 >> (7 - m.serial_bit)) & 1;
            if (++m.serial_bit == 8) { m.unlocked = true; m.serial_bit = 0; }
            return bit << 1;
        }
        unsigned bank = a < 0xa000 ? m.low_bank : m.high_bank;
        return cart[(bank * 16384u + ((a - 0x6000) & 16383)) % cart_size];
    }
    switch (a & 0xfc00) {
        case 0x4000: return m.psg.reg[a & 15];
        case 0x4400: return (uint8_t)~joystick;
        case 0x4800: return 0;
        case 0x5000:
            if ((a & 7) == 6) {
                uint8_t v = m.planes[m.plane][m.vaddr];
                step_vram();
                return v;
            }
            break;
        case 0x5800:
            if (a == 0x5800) { m.card_present = 1; return 0; }
            if (a == 0x5a00) return m.card_present ? 3 : 1;
            break;
    }
    return 0xff;
}

static void write_cart(uint16_t addr, uint8_t v) {
    static const char handshake[] = "BIT CORPORATION";
    if (m.unlocked) {
        if (addr == 0xc000 && cart_size > 32768) m.high_bank = v;
        /* The multi-game mapper can also switch the lower 16 KiB window. */
        if (addr == 0x8000 && cart_size > 32768) m.low_bank = v;
    } else if (!m.serial_ready && !m.serial_failed) {
        if (m.serial_bit < 8) {
            m.serial_byte = (m.serial_byte << 1) | ((v >> 2) & 1);
            m.serial_bit++;
        } else {
            m.serial_failed = m.serial_byte != handshake[m.serial_position++];
            m.serial_bit = m.serial_byte = 0;
            m.serial_ready = !m.serial_failed && m.serial_position == sizeof(handshake) - 1;
        }
    }
}

static void write_bus(uint16_t a, uint8_t v) {
    if (a < 0x2000) { m.ram[a & 1023] = v; return; }
    if (a >= 0x6000 && a < 0xe000) { write_cart(a,v); return; }
    if ((a & 0xfc00) == 0x4000) { ay38910_set_register(&m.psg,a & 15,v); return; }
    if ((a & 0xfc00) != 0x5000) return;
    switch (a & 7) {
        case 1: m.lcd = v; break;
        case 2: m.scroll_x = v; break;
        case 3: m.scroll_y = v; break;
        case 4: m.plane = v >> 7; m.vaddr = (m.vaddr & 0x1fe0) | (v & 31); break;
        case 5: m.vaddr = (v << 5) | (m.vaddr & 31); break;
        case 7: m.planes[m.plane][m.vaddr] = v; step_vram(); break;
    }
}

static void draw(uint32_t *pixels) {
    static const uint32_t palette[] = {0x6ba64a,0x437a63,0x255955,0x12424c};
    for (int y = 0; y < GAMATE_HEIGHT; y++) {
        int row = y, scroll = m.scroll_x;
        if (m.scroll_y < 200) {
            row = (y + m.scroll_y) % 200;
            if ((m.lcd & 32) && y < 16) { row = 208+y; scroll = 0; }
        } else if (m.scroll_y & 8) {
            row = 0;
        } else if (y <= (m.scroll_y & 7)) {
            row = 255+y-(m.scroll_y & 7); scroll = 0;
        }
        for (int x = 0; x < GAMATE_WIDTH; x++) {
            unsigned col = (x + scroll) & 255, addr = row*32 + col/8;
            unsigned shift = 7 - (col & 7), high = (m.lcd & 16) ? 0 : 1;
            unsigned index = ((m.planes[high][addr] >> shift) & 1) * 2
                           + ((m.planes[1-high][addr] >> shift) & 1);
            *pixels++ = palette[(m.lcd & 128) ? 0 : index];
        }
    }
}

/* Headphone wiring mixes voice A to the left, B to the right and C to both.
 * Use the pinned AY core's current tone, noise, envelope and DAC levels. */
static float voice(unsigned i) {
    const ay38910_tone_t *t = &m.psg.tone[i];
    if (!((t->bit | t->tone_disable) & ((m.psg.noise.rng & 1) | t->noise_disable))) return 0;
    unsigned volume = m.psg.reg[AY38910_REG_AMP_A+i];
    return _ay38910_volumes[(volume & 16) ? m.psg.env.shape_state : (volume & 15)];
}

static int16_t headphone(float value, unsigned side) {
    m.audio_dc[side] += (value - m.audio_dc[side]) * 0.0026f;
    return (int16_t)(32767 * (value - m.audio_dc[side]));
}

size_t gamate_run(uint8_t keys, uint32_t *pixels, int16_t *audio) {
    size_t count = 0;
    joystick = keys;
    for (int i = 0; i < 36450; i++) {
        if (((m.cycles++ - 1000u) & 16383) < 10) m.pins |= M6502_IRQ;
        else m.pins &= ~M6502_IRQ;
        m.pins = m6502_tick(&m.cpu,m.pins);
        uint16_t a = M6502_GET_ADDR(m.pins);
        if (!cartridge_frames && (m.pins & M6502_SYNC) && a >= 0x6000 && a < 0xe000) cartridge_frames = 1;
        if (m.pins & M6502_RW) {
            uint8_t value = read_bus(a);
            M6502_SET_DATA(m.pins,value);
        } else write_bus(a,M6502_GET_DATA(m.pins));
        if ((i & 1) && ay38910_tick(&m.psg)) {
            float center = 0.25f * voice(2);
            audio[count*2] = headphone(0.5f * voice(0) + center, 0);
            audio[count*2+1] = headphone(0.5f * voice(1) + center, 1);
            count++;
        }
    }
    if (cartridge_frames && !gamate_artwork_ready()) cartridge_frames++;
    draw(pixels);
    return count;
}

typedef struct {
    uint32_t version, game, checksum;
    Machine machine;
} Snapshot;
size_t gamate_state_size(void) { return sizeof(Snapshot); }

bool gamate_state(void *data, size_t size, bool save) {
    if (!cart || size != sizeof(Snapshot)) return false;
    Snapshot *state = data;
    if (save) {
        state->version = 1; state->game = identity; state->machine = m;
        m6502_snapshot_onsave(&state->machine.cpu);
        ay38910_snapshot_onsave(&state->machine.psg);
        state->checksum = hash(&state->machine, sizeof(m), identity);
    } else {
        Machine *s = &state->machine;
        if (!isfinite(s->audio_dc[0]) || !isfinite(s->audio_dc[1])
            || fabsf(s->audio_dc[0]) > 1 || fabsf(s->audio_dc[1]) > 1
            || state->version != 1 || state->game != identity || state->checksum != hash(s,sizeof(m),identity)
            || s->plane > 1 || s->vaddr >= 8192 || s->cpu.IR >= 2048 || s->serial_bit > 8
            || s->serial_position > 15 || (!s->serial_ready && !s->serial_failed && s->serial_position >= 15)
            || (s->serial_ready && s->serial_bit >= 8) || s->psg.env_shape_cycle > 15
            || s->psg.env.shape_counter >= 32 || s->psg.env.shape_state >= 16
            || s->psg.dcadj_pos >= AY38910_DCADJ_BUFLEN) return false;
        m6502_snapshot_onload(&s->cpu,&m.cpu);
        ay38910_snapshot_onload(&s->psg,&m.psg);
        m = *s;
    }
    return true;
}
