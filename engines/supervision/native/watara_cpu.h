/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <stdbool.h>
#include <stdint.h>

/* Preserve the original 22-byte register record and 128-byte reserved area. */
enum { WATARA_CPU_STATE_SIZE = 150 };
uint8_t Rd6502(uint16_t address);
void Wr6502(uint16_t address, uint8_t value);
void watara_cpu_reset(void);
bool watara_cpu_ready(void);
void watara_cpu_done(void);
void watara_cpu_run(unsigned cycles);
void watara_cpu_irq(bool asserted);
void watara_cpu_nmi(void);
void watara_cpu_save(uint8_t *state);
void watara_cpu_load(const uint8_t *state);
