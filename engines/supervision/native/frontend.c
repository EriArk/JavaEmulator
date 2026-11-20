/* SPDX-License-Identifier: GPL-3.0-only */
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_supervision_SupervisionCore_##name
#define CORE_TAG "AbyssSupervision"
#define FRONTEND_MEMORY_ROM
enum { W = 160, H = 160, RATE = 44100 };
#include "../../handheld/native/frontend.h"

#define FRONTEND_STATE_FILE "slot.svstate"
#include "../../handheld/native/checkpoint.h"
