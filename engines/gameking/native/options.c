/* SPDX-License-Identifier: GPL-3.0-only */
#include "libretro.h"
#include <string.h>

static const struct retro_core_option_v2_definition *options;

bool gameking_environment(unsigned cmd, void *data, const char *directory) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_CONTENT_DIRECTORY:
            *(const char **)data = directory;
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *(unsigned *)data = 2;
            return true;
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL:
            options = ((struct retro_core_options_v2_intl *)data)->us->definitions;
            return true;
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
            options = ((struct retro_core_options_v2 *)data)->definitions;
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *var = data;
            for (const struct retro_core_option_v2_definition *p = options; p && p->key; ++p) {
                if (strcmp(p->key, var->key)) continue;
                var->value = p->default_value;
                return true;
            }
            return false;
        }
        default: return false;
    }
}
