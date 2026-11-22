// SPDX-License-Identifier: GPL-3.0-only
#include <algorithm>
#include <array>
#include <cstring>
#include <filesystem>
#include <fstream>
#include "cpu.hpp"
#include "sys.hpp"
#include "timers.hpp"
#include "gpio.hpp"
#include "spi.hpp"
#include "sd.hpp"
#include "screen.hpp"
#include "state.hpp"

namespace fs = std::filesystem;
namespace Card { bool open(const fs::path &, const fs::path &); void close(); }
namespace GDB { bool connected() { return false; } void interrupt() {} }
volatile EmuState emustate = EmuState::RUNNING;
u32 verbose = 0;

static std::array<u16, 220 * 176> screen;
static fs::path eeprom;
static bool saveEeprom() {
    if (!MMU::eepromDirty) return true;
    auto tmp = eeprom.string() + ".tmp";
    std::ofstream out(tmp, std::ios::binary);
    out.write(reinterpret_cast<const char *>(MMU::eeprom), sizeof(MMU::eeprom));
    out.close();
    if (!out || std::rename(tmp.c_str(), eeprom.c_str())) return false;
    MMU::eepromDirty = false;
    return true;
}

namespace AUDIO {
// Resample the guest DAC on its clock. One host frame contains 800 stereo samples.
static std::array<int16_t, 800 * 2> samples;
static u32 index;
static u8 level = 128;
static u64 origin;
static u32 clockHz;
static void fill(u32 end) {
    while (index < std::min(end, 800u)) {
        samples[index * 2] = samples[index * 2 + 1] = (int(level) - 128) * 256;
        ++index;
    }
}
void write(u8 value) {
    fill((CPU::cpuTotalTicks - origin) * 48000 / clockHz);
    level = value;
}
void checkHLE(u32) {} // Use the emulated timer interrupt and DAC writes.
void update() {}
}
extern "C" bool pokitto_open(const char *file, const char *dir) {
    try {
        std::ifstream in(file, std::ios::binary);
        u32 header[2];
        while (in.read(reinterpret_cast<char *>(header), sizeof(header))) {
            if (header[0] >= 0x10000000) {
                in.seekg(-8, std::ios::cur);
                break;
            }
            in.seekg(header[1], std::ios::cur);
        }
        if (!in || !Card::open(fs::path(file).parent_path(), dir)) return false;
        in.read(reinterpret_cast<char *>(MMU::flash), sizeof(MMU::flash));
        if (in.gcount() < 8) return false;
        eeprom = fs::path(dir) / "eeprom.bin";
        // Direct program boot bypasses the hardware loader that seeds these three settings.
        MMU::eeprom[4000] = 127; // speaker volume
        MMU::eeprom[4002] = 3;   // loader countdown
        MMU::eeprom[4003] = 10;  // volume dialog countdown
        if (fs::exists(eeprom)) {
            std::ifstream saved(eeprom, std::ios::binary);
            if (!saved.read(reinterpret_cast<char *>(MMU::eeprom), sizeof(MMU::eeprom))) return false;
        }
        SCREEN::LCD = screen.data();
        MMU::init();
        SPI::spi0Out(SD::write);
        CPU::init();
        CPU::reset();
        return true;
    } catch (const std::exception &) { return false; }
}

extern "C" bool pokitto_frame(int buttons, u16 **lcd, const int16_t **sound, size_t *count) {
    static constexpr struct { int id, port, bit; } pins[] = {
        {4,1,13}, {5,1,3}, {6,1,25}, {7,1,7}, {8,1,9}, {0,1,4}, {9,1,10}, {2,0,1}
    };
    for (const auto &pin : pins) GPIO::input(pin.port, pin.bit, !!(buttons & (1 << pin.id)));
    GPIO::update();
    AUDIO::origin = CPU::cpuTotalTicks;
    AUDIO::index = 0;
    AUDIO::clockHz = SYS::SYSPLLCTRL == 0x25 ? 72000000 : 45000000;
    u64 end = AUDIO::origin + AUDIO::clockHz / 60;
    while (CPU::cpuTotalTicks < end && emustate == EmuState::RUNNING) {
        u32 slice = end - CPU::cpuTotalTicks;
        slice = std::min(slice, SYS::update());
        slice = std::min(slice, TIMERS::update());
        CPU::cpuNextEvent = CPU::cpuTotalTicks + slice;
        CPU::thumbExecute();
    }
    AUDIO::fill(800);
    *lcd = screen.data();
    *sound = AUDIO::samples.data();
    *count = 800;
    return emustate == EmuState::RUNNING && saveEeprom();
}
extern "C" void pokitto_close() {
    saveEeprom();
    Card::close();
}
