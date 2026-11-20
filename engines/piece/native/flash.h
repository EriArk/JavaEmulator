/* SPDX-License-Identifier: GPL-3.0-only */
/* Called under the emulation mutex or with the worker stopped. */
extern void *retro_get_memory_data(unsigned);
extern size_t retro_get_memory_size(unsigned);
static uint8_t *baseline;
static size_t image_size;

static void release_image(void) {
    free(baseline);
    baseline = NULL;
}

static bool load_image(const char *path) {
    FILE *file = fopen(path, "rb");
    if (!file) return false;
    bool ok = false;
    if (fseek(file, 0, SEEK_END) == 0) {
        long length = ftell(file);
        if (length >= 40 && length <= 3 * 1024 * 1024 && fseek(file, 0, SEEK_SET) == 0) {
            image_size = (size_t)length;
            baseline = malloc(image_size);
            if (baseline && fread(baseline, 1, image_size, file) == image_size)
                ok = retro_load_game(&(struct retro_game_info){.data = baseline, .size = image_size, .path = path});
        }
    }
    fclose(file);
    return ok;
}

static bool flush_image(void) {
    const void *current = retro_get_memory_data(0);
    if (!current || retro_get_memory_size(0) != image_size) return false;
    if (memcmp(baseline, current, image_size) == 0) return true;
    char path[4096], temp[4096];
    if (snprintf(path, sizeof(path), "%s/flash.pfi", save_dir) >= sizeof(path) ||
            snprintf(temp, sizeof(temp), "%s.tmp", path) >= sizeof(temp)) return false;
    FILE *file = fopen(temp, "wb");
    if (!file) return false;
    bool ok = fwrite(current, 1, image_size, file) == image_size &&
            fflush(file) == 0 && fsync(fileno(file)) == 0;
    if (fclose(file) != 0) ok = false;
    if (ok && rename(temp, path) == 0) {
        memcpy(baseline, current, image_size);
        return true;
    }
    unlink(temp);
    return false;
}
