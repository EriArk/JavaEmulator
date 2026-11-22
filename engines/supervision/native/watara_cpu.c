/* SPDX-License-Identifier: GPL-3.0-only */
#include "watara_cpu.h"
#include <string.h>
/* The pinned MIT core provides getters but no full-state import interface. */
#include "vrEmu6502.c"

static VrEmu6502 *cpu;

static uint8_t read_bus(uint16_t address, bool debug)
{
    (void)debug;
    return Rd6502(address);
}

void watara_cpu_reset(void)
{
    if (cpu)
        vrEmu6502Reset(cpu);
    else
        cpu = vrEmu6502New(CPU_W65C02, read_bus, Wr6502);
    if (cpu) {
        cpu->ac = cpu->ix = cpu->iy = 0;
        cpu->sp = 0xff;
        cpu->flags = FlagU | FlagB | FlagI;
    }
}

bool watara_cpu_ready(void) { return cpu != NULL; }

void watara_cpu_done(void)
{
    vrEmu6502Destroy(cpu);
    cpu = NULL;
}

void watara_cpu_run(unsigned cycles)
{
    while (cycles--)
        vrEmu6502Tick(cpu);
}

void watara_cpu_irq(bool asserted)
{
    if (cpu)
        *vrEmu6502Int(cpu) = asserted ? IntRequested : IntCleared;
}

void watara_cpu_nmi(void) { *vrEmu6502Nmi(cpu) = IntRequested; }

void watara_cpu_save(uint8_t *state)
{
    memset(state, 0, WATARA_CPU_STATE_SIZE);
    state[0] = cpu->ac;
    state[1] = cpu->flags;
    state[2] = cpu->ix;
    state[3] = cpu->iy;
    state[4] = cpu->sp;
    state[5] = (uint8_t)cpu->pc;
    state[6] = cpu->pc >> 8;
    /* Period and countdown are 256 in the original little-endian format. */
    state[8] = state[12] = 1;
    state[15] = state[21] = cpu->intPin == IntRequested;
    memcpy(state + 22, "VRC1", 4);
    state[26] = cpu->step;
    state[27] = cpu->currentOpcode;
    state[28] = cpu->wai;
    state[29] = cpu->stp;
    state[30] = cpu->nmiPin == IntRequested;
}

void watara_cpu_load(const uint8_t *state)
{
    bool extended = memcmp(state + 22, "VRC1", 4) == 0;
    cpu->ac = state[0];
    cpu->flags = state[1] | FlagU | FlagB;
    cpu->ix = state[2];
    cpu->iy = state[3];
    cpu->sp = state[4];
    cpu->pc = (uint16_t)(state[5] | (state[6] << 8));
    watara_cpu_irq(state[15] == 1 || state[21] != 0);
    cpu->step = extended ? state[26] : 0;
    cpu->currentOpcode = extended ? state[27] : 0;
    cpu->wai = extended && state[28];
    cpu->stp = extended && state[29];
    cpu->nmiPin = extended && state[30] ? IntRequested : IntCleared;
}
