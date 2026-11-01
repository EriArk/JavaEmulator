// SPDX-License-Identifier: GPL-3.0-only
#include "file_duplicate.h"
#include <cassert>
#include <cstdio>
#include <memory>
#include <string>
#include <unistd.h>

namespace {
constexpr int read_mode = 1, write_mode = 2;
struct stream {
    std::string path;
    int mode;
    std::FILE *file;
    bool fail_flush = false, fail_seek = false;
    stream(std::string path, int mode) : path(path), mode(mode),
        file(std::fopen(path.c_str(), mode == write_mode ? "wb" : mode & write_mode ? "rb+" : "rb")) {}
    ~stream() { if (file) std::fclose(file); }
    std::string file_name() { return path; }
    int file_mode() { return mode; }
    std::uint64_t tell() { return std::ftell(file); }
    bool flush() { return !fail_flush && (!(mode & write_mode) || std::fflush(file) == 0); }
    std::uint64_t seek(std::int64_t position, int begin) {
        return fail_seek || std::fseek(file, position, begin) != 0 ? UINT64_MAX : tell();
    }
};
}
int main() {
    char path[] = "/data/local/tmp/abyss-file-share-XXXXXX";
    int fd = mkstemp(path); assert(fd >= 0); close(fd);
    auto open = [](std::string path, int mode) {
        auto value = std::make_unique<stream>(path, mode);
        return value->file ? std::move(value) : nullptr;
    };
    {
        auto source = open(path, write_mode); assert(source);
        assert(std::fwrite("abcdefgh", 1, 8, source->file) == 8);
        assert(source->seek(3, SEEK_SET) == 3);
        auto copy = abyssme::duplicate_file_stream(*source, read_mode, SEEK_SET, open);
        assert(copy && copy->tell() == 3 && source->tell() == 3);
        assert(std::fgetc(copy->file) == 'd');
        assert(copy->tell() == 4 && source->tell() == 3);
        assert(source->seek(0, SEEK_SET) == 0);
        source.reset(); // Closing the donor must not reset/invalidate the recipient.
        assert(std::fgetc(copy->file) == 'e');
        assert(copy->seek(5, SEEK_SET) == 5); // Switch the update stream from read to write.
        assert(std::fwrite("Z", 1, 1, copy->file) == 1);
        assert(copy->flush());
        auto second = abyssme::duplicate_file_stream(*copy, read_mode, SEEK_SET, open);
        assert(second && second->tell() == 6);
        second.reset();
        assert(copy->tell() == 6);
        auto reader = open(path, read_mode);
        char contents[9]{};
        assert(std::fread(contents, 1, 8, reader->file) == 8);
        assert(std::string(contents) == "abcdeZgh"); // No truncation on duplicate.
        assert(reader->seek(2, SEEK_SET) == 2);
        auto readonly = abyssme::duplicate_file_stream(*reader, read_mode, SEEK_SET, open);
        assert(readonly && readonly->file_mode() == read_mode && std::fgetc(readonly->file) == 'c');
        reader->fail_flush = true;
        assert(!abyssme::duplicate_file_stream(*reader, read_mode, SEEK_SET, open));
        reader->fail_flush = false;
        auto missing = [](std::string, int) { return std::unique_ptr<stream>(); };
        assert(!abyssme::duplicate_file_stream(*reader, read_mode, SEEK_SET, missing));
        auto bad_seek = [open](std::string path, int mode) {
            auto result = open(path, mode); result->fail_seek = true; return result;
        };
        assert(!abyssme::duplicate_file_stream(*reader, read_mode, SEEK_SET, bad_seek));
        assert(reader->tell() == 2 && std::fgetc(reader->file) == 'c');
    }
    assert(std::remove(path) == 0);
    std::puts("File duplicate: independent cursors, donor/recipient close, non-truncating writes, read-only and failure rollback passed");
}
