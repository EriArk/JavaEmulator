// SPDX-License-Identifier: GPL-3.0-only
#include <cpu/arm_dynarmic.h>
#include <cassert>
#include <cstdio>

int main() {
    using namespace eka2l1::arm;
    Dynarmic::TLB<9> tlb(12);
    unsigned char page[4096]{};
    assert(tlb.entries[0].read_addr != 0 && tlb.Lookup(8) == nullptr);
    tlb.Add(0, page, Dynarmic::MemoryPermission::Read);
    assert(tlb.entries[0].read_addr == 0 && tlb.entries[0].write_addr != 0);
    assert(tlb.Lookup(8) == page + 8);
    tlb.MakeDirty(0);
    assert(tlb.entries[0].read_addr != 0 && tlb.Lookup(8) == nullptr);
    tlb.Add(0x200000, page, Dynarmic::MemoryPermission::Write);
    assert(tlb.entries[0].read_addr != 0 && tlb.Lookup(8) == nullptr);
    tlb.Flush();
    assert(tlb.entries[0].write_addr != 0 && tlb.Lookup(8) == nullptr);
    dynarmic_exclusive_monitor monitor(1);
    dynarmic_core cpu(&monitor);
    bool recover = false, resolved = false;
    unsigned faults = 0, writes = 0;
    std::uint32_t instruction = 0xe5901000; // ldr r1, [r0]
    cpu.read_code = [&](address pc, std::uint32_t *out) {
        switch (pc) {
        case 0x1000: *out = instruction; break;
        case 0x1004: *out = 0xe3a0202a; break; // mov r2, #42
        case 0x1008: *out = 0xe5821000; break; // str r1, [r2]
        case 0x100c: *out = 0xeafffffe; break; // b .
        default: return false;
        }
        return true;
    };
    cpu.read_32bit = [&](address addr, std::uint32_t *out) {
        assert(addr == 8);
        if (!resolved) return false;
        *out = 77; return true;
    };
    cpu.write_32bit = [&](address addr, std::uint32_t *) {
        if (addr == 8) return false;
        assert(addr == 42); writes++; return true;
    };
    cpu.exception_handler = [&](exception_type type, std::uint32_t addr) {
        assert(addr == 8);
        assert(type == (instruction == 0xe5901000 ? exception_type_access_violation_read : exception_type_access_violation_write));
        faults++;
        if (recover) { resolved = true; return true; }
        cpu.stop(); // The kernel also stops the core when terminating a guest.
        return false;
    };
    auto run = [&] {
        cpu.set_cpsr(0x10); cpu.set_pc(0x1000);
        cpu.set_reg(0, 8); cpu.set_reg(1, 99); cpu.set_reg(2, 0);
        cpu.run(50);
    };
    run();
    assert(faults == 1 && writes == 0 && cpu.get_reg(2) == 0);
    recover = true;
    run(); // A later guest run must not retain MemoryAbort.
    assert(faults == 2 && writes == 1 && cpu.get_reg(1) == 77 && cpu.get_reg(2) == 42);
    recover = false; resolved = false; faults = 0; writes = 0;
    instruction = 0xe5801000; // str r1, [r0]
    cpu.clear_instruction_cache();
    run();
    assert(faults == 1 && writes == 0 && cpu.get_reg(2) == 0);
    cpu.set_tlb_page(0, page, prot_read);
    faults = 0;
    run(); // Page zero exists, but the write permission must still miss.
    assert(faults == 1 && writes == 0 && page[8] == 0);
    instruction = 0xe5901000;
    cpu.clear_instruction_cache(); faults = 0;
    run(); // A genuinely readable zero page must remain usable.
    assert(faults == 0 && writes == 1 && cpu.get_reg(1) == 0);
    cpu.dirty_tlb_page(0); writes = 0;
    run();
    assert(faults == 1 && writes == 0 && cpu.get_reg(2) == 0);
    std::puts("JIT faults: zero-page permissions/invalidation, read/write abort, recoverable read and later run pass");
}
