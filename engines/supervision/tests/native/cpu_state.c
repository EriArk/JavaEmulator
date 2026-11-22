/* SPDX-License-Identifier: GPL-3.0-only */
#include "watara_cpu.h"
#include <assert.h>
#include <string.h>

static uint8_t memory[65536];
uint8_t Rd6502(uint16_t address) { return memory[address]; }
void Wr6502(uint16_t address, uint8_t value) { memory[address] = value; }

int main(void)
{
    memory[0xfffd] = 0x80; /* reset -> 8000 */
    memory[0xfffb] = 0x90; /* NMI -> 9000 */
    memory[0xffff] = 0x91; /* IRQ -> 9100 */
    const uint8_t program[] = {0x58, 0xf8, 0xcb, 0x80, 0xfd}; /* CLI, SED, WAI, BRA */
    const uint8_t nmi[] = {0xe6, 0x20, 0x40}; /* INC $20, RTI */
    const uint8_t irq[] = {0x08, 0x68, 0x85, 0x21, 0x40}; /* PHP, PLA, STA $21, RTI */
    memcpy(memory + 0x8000, program, sizeof(program));
    memcpy(memory + 0x9000, nmi, sizeof(nmi));
    memcpy(memory + 0x9100, irq, sizeof(irq));
    watara_cpu_reset();
    assert(watara_cpu_ready());
    watara_cpu_run(16);
    watara_cpu_nmi();
    uint8_t before[WATARA_CPU_STATE_SIZE], after[WATARA_CPU_STATE_SIZE], replay[WATARA_CPU_STATE_SIZE];
    watara_cpu_save(before);
    assert(before[28] && before[30]); /* waiting with a pending NMI */
    watara_cpu_run(24);
    assert(memory[0x20] == 1);
    watara_cpu_save(after);
    watara_cpu_load(before);
    watara_cpu_run(24);
    assert(memory[0x20] == 2);
    watara_cpu_save(replay);
    assert(!memcmp(after, replay, sizeof(after)));
    watara_cpu_irq(true);
    watara_cpu_run(1);
    watara_cpu_irq(false);
    watara_cpu_run(28);
    assert((memory[0x21] & 0x0c) == 0x04); /* IRQ handler observes I set and D clear */
    /* Old checkpoints end after the register and interrupt fields. */
    memset(before + 22, 0, sizeof(before) - 22);
    before[5] = 0; before[6] = 0x90;
    watara_cpu_load(before);
    watara_cpu_run(1);
    assert(memory[0x20] == 3);
    watara_cpu_done();
}
