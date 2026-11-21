// SPDX-License-Identifier: GPL-3.0-only
#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <poll.h>
#include <stdexcept>
#include <string>
#include <vector>
#include <unistd.h>

#include "MainLoop.h"
#include "app_launcher.h"
#include "audio_queue.h"
#include "cputil.h"
#include "db_installer.h"
#include "device.h"
#include "rom_info5.h"
#include "session/session_file5.h"
#include "soc_PXA.h"
#include "soc_pv.h"
#include "system_state.h"

using Bytes = std::vector<uint8_t>;
using Error = std::runtime_error;
static FILE* output;

// The Android parent uses a private pipe for each direction. Words are little-endian.
static void event(char type, const void* data = nullptr, uint32_t size = 0) {
    if (fputc(type, output) == EOF || fwrite(&size, sizeof(size), 1, output) != 1 ||
        (size && fwrite(data, 1, size, output) != size) || fflush(output) != 0) {
        throw Error("Palm display disconnected");
    }
}

static Bytes readFile(const char* path) {
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in || in.tellg() <= 0) throw Error("Cannot open Palm library file");
    Bytes data(static_cast<size_t>(in.tellg()));
    in.seekg(0);
    if (!in.read(reinterpret_cast<char*>(data.data()), data.size())) throw Error("Cannot read Palm library file");
    return data;
}

static void assign(Bytes& dest, const void* src, size_t size) {
    if (size) {
        auto first = static_cast<const uint8_t*>(src);
        dest.assign(first, first + size);
    }
}

static void checkpoint(SoC& soc, const Bytes& metadata) {
    if (!soc.Save()) throw Error("Could not snapshot the Palm device");
    auto nor = soc.GetRomData(), nand = soc.GetNandData(), ram = soc.GetMemoryData(), state = soc.GetSavestate();
    SessionFile5 file;
    file.SetDeviceType(soc.GetDeviceType()).SetDisplayMode(static_cast<int>(soc.GetDisplayMode()))
        .SetRamSize(soc.GetRamSize()).SetNor(nor.size, nor.data).SetNand(nand.size, nand.data)
        .SetMemory(ram.size, ram.data).SetSavestate(state.size, state.data)
        .SetMetadata(metadata.size(), metadata.data());
    if (!file.Serialize()) throw Error("Could not encode the Palm session");
    event('S', file.GetSerializedSession(), file.GetSerializedSessionSize());
}

static void install(SoC& soc, int count, char** files, int selected) {
    auto dispatch = soc.GetSyscallDispatch();
    auto load = [&](int index, bool launch) {
        auto db = readFile(files[index]);
        if (dbInstallerInstall(dispatch, db.size(), db.data()) < 0) throw Error("Could not install Palm database");
        auto name = reinterpret_cast<const char*>(db.data());
        if (launch && (db.size() < 32 || strnlen(name, 32) == 32 || !launchAppByName(dispatch, name))) {
            throw Error("Could not launch installed Palm application");
        }
    };
    for (int i = 0; i < count; ++i) if (i != selected) load(i, false);
    if (selected >= 0 && selected < count) load(selected, true);
}

static void run(int argc, char** argv) {
    if (argc < 6) throw Error("Missing Palm startup arguments");
    Bytes rom = readFile(argv[1]), nand, metadata;
    RomInfo5 info(rom.data(), rom.size());
    const auto device = info.GetDeviceType();
    if (device == deviceTypeInvalid) { event('W'); return; }

    auto mode = deviceConfigurationDefaultDisplayMode(device);
    uint32_t ramSize = info.GetRecommendedRamSize();
    SoC* soc;
    {
        Bytes file;
        SessionFile5 state;
        const bool restore = *argv[3];
        if (restore) {
            file = readFile(argv[3]);
            if (!state.Deserialize(file.size(), file.data()) || state.GetDeviceType() != device ||
                !state.GetMemorySize() || !state.GetSavestateSize()) throw Error("Invalid Palm session for this device");
            if (state.GetNorSize() != rom.size()) throw Error("Palm session ROM size mismatch");
            std::memcpy(rom.data(), state.GetNor(), rom.size());
            assign(nand, state.GetNand(), state.GetNandSize());
            assign(metadata, state.GetMetadata(), state.GetMetadataSize());
            mode = static_cast<DisplayMode>(state.GetDisplayMode());
            ramSize = state.GetRamSize();
            if (!ramSize) ramSize = 1u << (31 - __builtin_clz(static_cast<unsigned>(state.GetMemorySize())));
        } else if (info.NeedsNand()) {
            if (!*argv[2]) throw Error("Import and select a Palm NAND image before opening this ROM.");
            nand = readFile(argv[2]);
        }
        auto required = deviceConfigurationGetNandSize(device);
        if (nand.empty() && !info.NeedsNand()) nand.resize(required, 0xff);
        if (nand.size() != required) throw Error("Palm NAND requires " + std::to_string(required) +
            " bytes. Selected image contains " + std::to_string(nand.size()) + " bytes.");
        if (!ramSize || !deviceConfigurationDeviceSupportsDisplayMode(device, mode) || !deviceConfigurationSupportsRamSize(device, ramSize))
            throw Error("Unsupported Palm session memory or display configuration");
        soc = device == deviceTypePV ? static_cast<SoC*>(new SocPV(ramSize, rom.data(), rom.size(), mode, -1)) :
            static_cast<SoC*>(new SocPXA(device, ramSize, rom.data(), rom.size(), nand.data(), nand.size(), -1, deviceGetSocRev()));
        if (restore) {
            auto memory = soc->GetMemoryData();
            if (state.GetMemorySize() > memory.size) throw Error("Palm session memory size mismatch");
            std::memcpy(memory.data, state.GetMemory(), state.GetMemorySize());
            if (!soc->Load(state.GetSavestateSize(), const_cast<void*>(state.GetSavestate())))
                throw Error("Could not restore Palm device state");
        }
    }
    const auto display = deviceConfigurationDisplayConfigForMode(mode);
    int32_t dimensions[] = {display.width, display.height};
    std::vector<uint32_t> frame(display.width * display.height), samples(7350);
    AudioQueue* audio = audioQueueCreate(samples.size());
    soc->SetAudioQueue(audio);
    soc->SetPcmOutputEnabled(true);
    MainLoop loop(soc);
    event('R', dimensions, sizeof(dimensions));

    const int selected = std::stoi(argv[5]);
    bool pendingInstall = argc > 6 && std::string(argv[4]) != "resume";
    bool paused = true, lcd = true;
    uint64_t next = 0;
    while (true) {
        auto now = timestampUsec();
        if (!paused && now >= next) {
            if (pendingInstall && systemStateIsUiInitialized(soc->GetSystemState())) {
                install(*soc, argc - 6, argv + 6, selected);
                pendingInstall = false;
                now = timestampUsec();
            }
            loop.Cycle(now);
            next = now + loop.GetTimesliceSizeUsec();
            uint32_t first, last;
            if (auto src = soc->GetPendingFrame(first, last)) {
                // uARM exposes ABGR words. Android's int framebuffer expects ARGB.
                std::transform(src, src + frame.size(), frame.begin(), [](uint32_t p) {
                    return 0xff000000u | ((p & 0xff) << 16) | (p & 0xff00) | ((p >> 16) & 0xff);
                });
                if (soc->LcdEnabled()) event('F', frame.data(), frame.size() * 4);
                soc->ResetPendingFrame();
            }
            if (lcd && !soc->LcdEnabled()) {
                std::fill(frame.begin(), frame.end(), 0xff000000u);
                event('F', frame.data(), frame.size() * 4);
            }
            lcd = soc->LcdEnabled();
            size_t count = audioQueuePopChunk(audio, samples.data(), audioQueuePendingSamples(audio));
            if (count) event('A', samples.data(), count * 4);
        }
        pollfd input{STDIN_FILENO, POLLIN, 0};
        now = timestampUsec();
        int timeout = paused ? -1 : static_cast<int>((next > now ? next - now + 999 : 0) / 1000);
        int ready = poll(&input, 1, timeout);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0 || input.revents & (POLLHUP | POLLERR)) return;
        if (!(input.revents & POLLIN)) continue;
        int32_t cmd[3];
        if (fread(cmd, sizeof(cmd), 1, stdin) != 1) return;
        switch (cmd[0]) {
            case 'P': paused = cmd[1]; if (paused) event('P'); else next = 0; break;
            case 'T': if (cmd[1] < 0) soc->PenUp(); else soc->PenDown(cmd[1], cmd[2]); break;
            case 'K': if (cmd[2]) soc->KeyDown(static_cast<KeyId>(cmd[1])); else soc->KeyUp(static_cast<KeyId>(cmd[1])); break;
            case 'V': loop.SetCyclesPerSecondLimit(static_cast<uint32_t>(std::max(1, cmd[1])) * 1000000u); break;
            case 'I': pendingInstall = true; break;
            case 'S':
                try { checkpoint(*soc, metadata); }
                catch (const std::exception& e) { event('s', e.what(), strlen(e.what())); }
                break;
        }
    }
}

int main(int argc, char** argv) {
    // Upstream prints diagnostics to stdout. Reserve the original descriptor for protocol messages.
    output = fdopen(dup(STDOUT_FILENO), "wb");
    if (!output || dup2(STDERR_FILENO, STDOUT_FILENO) < 0) return 1;
    setvbuf(stdin, nullptr, _IONBF, 0);
    try { run(argc, argv); }
    catch (const std::exception& e) { event('E', e.what(), strlen(e.what())); return 1; }
    return 0;
}
