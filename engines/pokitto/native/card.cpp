// SPDX-License-Identifier: GPL-3.0-only
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <unistd.h>
#include "types.hpp"
#include "ChaN/ffconf.h"
#include "ChaN/fileff.h"
#include "ChaN/file_diskio.h"

namespace fs = std::filesystem;
namespace Card {
static FILE *disk;
static off_t length;

bool read(u32 block, u8 *data) {
    off_t pos = off_t(block) * 512;
    return disk && pos <= length - 512 && !fseeko(disk, pos, SEEK_SET) && fread(data, 1, 512, disk) == 512;
}
bool write(u32 block, const u8 *data) {
    off_t pos = off_t(block) * 512;
    return disk && pos <= length - 512 && !fseeko(disk, pos, SEEK_SET) && fwrite(data, 1, 512, disk) == 512 && !fflush(disk);
}
void close() { if (disk) { fclose(disk); disk = nullptr; } }
static bool attach(const fs::path &path) {
    length = fs::file_size(path);
    disk = fopen(path.c_str(), "r+b");
    return disk && length >= 512;
}
static std::string version(const fs::path &file) {
    std::ifstream in(file);
    std::string s;
    std::getline(in, s);
    return s;
}
static bool populate(const fs::path &assets, bool format) {
    YAPFS::FATFS fs{};
    if (YAPFS::f_mount(0, &fs) || (format && YAPFS::f_mkfs(0, 1, 0))) return false;
    if (std::filesystem::is_directory(assets)) {
        for (const auto &e : std::filesystem::recursive_directory_iterator(assets)) {
            auto path = std::filesystem::relative(e.path(), assets).generic_string();
            if (e.is_directory()) {
                auto error = YAPFS::f_mkdir(path.c_str());
                if (error && error != YAPFS::FR_EXIST) return false;
                continue;
            }
            YAPFS::FIL out{};
            auto error = YAPFS::f_open(&out, path.c_str(), FA_WRITE | FA_CREATE_NEW);
            if (error == YAPFS::FR_EXIST) continue; // Existing guest files belong to the player.
            if (error) return false;
            std::ifstream in(e.path(), std::ios::binary);
            char buffer[8192];
            while (in.read(buffer, sizeof(buffer)) || in.gcount()) {
                YAPFS::UINT n = 0;
                if (YAPFS::f_write(&out, buffer, in.gcount(), &n) || n != in.gcount()) return false;
            }
            if (!in.eof() || YAPFS::f_close(&out)) return false;
        }
    }
    return YAPFS::f_mount(0, nullptr) == YAPFS::FR_OK;
}
bool open(const fs::path &pkg, const fs::path &dir) {
    const auto path = dir / "sd.img", tmp = dir / "sd.tmp", assets = pkg / "sd-files";
    auto revision = version(pkg / "sd-version");
    bool existing = fs::is_regular_file(path), image = fs::is_regular_file(pkg / "initial.img");
    if (existing && (revision.empty() || revision == version(dir / "sd-version"))) return attach(path);
    if (existing || image) {
        fs::copy_file(existing ? path : pkg / "initial.img", tmp, fs::copy_options::overwrite_existing);
    } else {
        uint64_t size = 8 * 1024 * 1024;
        if (fs::is_directory(assets)) for (const auto &e : fs::recursive_directory_iterator(assets))
            if (e.is_regular_file()) size += ((e.file_size() + 511) / 512) * 1024;
        std::ofstream(tmp, std::ios::binary).close();
        fs::resize_file(tmp, size);
    }
    if (!attach(tmp)) return false;
    bool ok = (image && !existing && revision.empty()) || populate(assets, !existing && !image);
    close();
    if (!ok) return false;
    fs::rename(tmp, path);
    if (!revision.empty()) {
        std::ofstream stamp(dir / "sd-version.tmp");
        stamp << revision;
        stamp.close();
        if (!stamp) return false;
        fs::rename(dir / "sd-version.tmp", dir / "sd-version");
    }
    return attach(path);
}
}

// FatFs uses the same sector interface as the emulated SD controller to prepare a card.
namespace YAPFS {
DWORD get_fattime() { return (46u << 25) | (1u << 21) | (1u << 16); }
DSTATUS disk_initialize(BYTE) { return Card::disk ? 0 : STA_NOINIT; }
DSTATUS disk_status(BYTE drive) { return disk_initialize(drive); }
DRESULT disk_read(BYTE, BYTE *out, DWORD block, BYTE count) {
    for (unsigned i = 0; i < count; ++i)
        if (!Card::read(block + i, out + i * 512)) return RES_ERROR;
    return RES_OK;
}
DRESULT disk_write(BYTE, const BYTE *in, DWORD block, BYTE count) {
    for (unsigned i = 0; i < count; ++i)
        if (!Card::write(block + i, in + i * 512)) return RES_ERROR;
    return RES_OK;
}
DRESULT disk_ioctl(BYTE, BYTE cmd, void *out) {
    switch (cmd) {
        case CTRL_SYNC: return Card::disk && !fflush(Card::disk) ? RES_OK : RES_ERROR;
        case GET_SECTOR_COUNT: *static_cast<DWORD *>(out) = Card::length / 512; return RES_OK;
        case GET_BLOCK_SIZE: *static_cast<DWORD *>(out) = 1; return RES_OK;
        default: return RES_PARERR;
    }
}
}
