/* SPDX-License-Identifier: GPL-3.0-only */
#include "libretro_impl.cpp"
#include <fcntl.h>
#include <unistd.h>

// Ardens' libretro serialization buffer is padded and its size is cached across games.
// Use the core's stream format directly so each slot can also load after a game switch.
extern "C" bool ardens_checkpoint(const char *directory, bool save) {
    const std::string path = std::string(directory) + "/slot.ardstate";
    if (!save) {
        std::ifstream in(path, std::ios::binary);
        return in && arduboy->load_savestate(in).empty();
    }
    const std::string temp = path + ".tmp";
    std::ofstream out(temp, std::ios::binary | std::ios::trunc);
    if (!out) return false;
    bool ok = arduboy->save_savestate(out).empty();
    out.close();
    if (ok && out) {
        int fd = open(temp.c_str(), O_RDONLY);
        if (fd >= 0) {
            ok = fsync(fd) == 0;
            if (close(fd) == 0 && ok && rename(temp.c_str(), path.c_str()) == 0) return true;
        }
    }
    unlink(temp.c_str());
    return false;
}
