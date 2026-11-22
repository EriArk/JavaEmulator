/* SPDX-License-Identifier: GPL-3.0-only */
#include "lfs.h"

/* Each game gets its own WebAssembly instance and flash image. */
static lfs_t fs;
static struct lfs_config config;
typedef int (*read_block)(const struct lfs_config *, lfs_block_t, lfs_off_t, void *, lfs_size_t);
typedef int (*write_block)(const struct lfs_config *, lfs_block_t, lfs_off_t, const void *, lfs_size_t);
typedef int (*erase_block)(const struct lfs_config *, lfs_block_t);
typedef int (*sync_blocks)(const struct lfs_config *);

int start_disk(read_block read, write_block write, erase_block erase, sync_blocks sync,
               unsigned blocks, unsigned block_size)
{
    config = (struct lfs_config){
        .read = read, .prog = write, .erase = erase, .sync = sync,
        .block_count = blocks, .block_size = block_size, .read_size = 256,
        .prog_size = 32, .cache_size = 1024, .lookahead_size = 32, .block_cycles = 100
    };
    int result = lfs_format(&fs, &config);
    return result < 0 ? result : lfs_mount(&fs, &config);
}

int make_directory(const char *path) { return lfs_mkdir(&fs, path); }
int finish_disk(void) { return lfs_unmount(&fs); }

int store_file(const char *path, const void *data, unsigned length)
{
    lfs_file_t file;
    int rc = lfs_file_open(&fs, &file, path, LFS_O_CREAT | LFS_O_TRUNC | LFS_O_WRONLY);
    if (rc < 0) return rc;
    lfs_ssize_t written = lfs_file_write(&fs, &file, data, length);
    rc = lfs_file_close(&fs, &file);
    if (written < 0) return written;
    return (unsigned)written == length ? rc : LFS_ERR_IO;
}
