/* SPDX-License-Identifier: GPL-3.0-only */
#define JNI_METHOD(name) Java_io_github_eriark_abyssme_megaduck_MegaDuckCore_##name
#define CORE_TAG "AbyssMegaDuck"
#define FRONTEND_XRGB8888
#define FRONTEND_BUFFER_SAMPLES
#define FRONTEND_FRAME_PERIOD_NS 16742706LL
enum { W = 160, H = 144, RATE = 48000 };
#include "../../handheld/native/frontend.h"

#define FRONTEND_STATE_FILE "slot.duckstate"
#include "../../handheld/native/checkpoint.h"
