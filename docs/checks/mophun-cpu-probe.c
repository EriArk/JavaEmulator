/* SPDX-License-Identifier: GPL-3.0-only
 * Original throughput probe. Compile with the Mophun SDK pip-gcc -O2.
 * Each inserted 0x00787811 instruction is a self-MOV on a scratch register.
 * The compiled loops execute 300003 and 600004 instructions respectively.
 * cpuopt.bin contains four little-endian 32-bit values: start/end/end/count.
 */
extern unsigned long vGetTickCount(void);
extern int vStreamOpen(const char *, int);
extern int vStreamWrite(int, const void *, int);
extern void vStreamClose(int);
extern void vTerminateVMGP(void);

int main(void) {
    unsigned long result[4];
    int i, file;
    result[0] = vGetTickCount();
    for (i = 0; i < 100000; i++) {
        __asm__ __volatile__(".long 0x00787811");
    }
    result[1] = vGetTickCount();
    for (i = 0; i < 100000; i++) {
        __asm__ __volatile__(".long 0x00787811\n\t.long 0x00787811\n\t.long 0x00787811\n\t.long 0x00787811");
    }
    result[2] = vGetTickCount();
    result[3] = i;
    file = vStreamOpen("cpuopt.bin", 0x1e00);
    vStreamWrite(file, result, sizeof result);
    vStreamClose(file);
    vTerminateVMGP();
    return 0;
}
