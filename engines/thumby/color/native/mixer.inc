/* SPDX-License-Identifier: GPL-3.0-only */
/* Included in the upstream audio module so all four guest channels keep their
 * normal gain, loop, playback time and completion semantics. */
#include <emscripten.h>
#include <math.h>
#include "resources/engine_resource_manager.h"


EMSCRIPTEN_KEEPALIVE float *abyss_color_audio(unsigned count) {
    static float pcm[2048];
    static uint32_t remainder[CHANNEL_COUNT];
    if (count > 2048) return NULL;
    for (unsigned n = 0; n < count; ++n) {
        float mix = 0;
        for (int i = 0; i < CHANNEL_COUNT; ++i) {
            audio_channel_class_obj_t *ch = channels[i];
            if (!ch || !ch->source || ch->busy) continue;
            bool ended = false;
            float sample = 0;
            if (mp_obj_is_type(ch->source, &wave_sound_resource_class_type)) {
                sound_resource_base_class_obj_t *src = ch->source;
                /* Keep the byte offset integral even for long streamed sounds. */
                unsigned stride = src->bytes_per_sample;
                if (stride && src->sample_rate && ch->source_byte_offset / stride < src->total_sample_count) {
                    uint32_t index = ch->source_byte_offset / stride;
                    const uint8_t *data = ENGINE_BYTEARRAY_OBJ_TO_DATA(src->extra_data);
                    if (stride == 1) sample = ((int)data[index]-128) / 128.0f;
                    if (stride == 2) sample = (int16_t)(data[2*index] | (data[2*index+1] << 8)) / 32768.0f;
                    ch->time = (float)index / src->sample_rate;
                    remainder[i] += src->sample_rate;
                    ch->source_byte_offset += stride * (remainder[i] / (unsigned)ENGINE_AUDIO_SAMPLE_RATE);
                    remainder[i] %= (unsigned)ENGINE_AUDIO_SAMPLE_RATE;
                }
                if (!stride || ch->source_byte_offset >= src->total_data_size) {
                    if (ch->loop && src->total_data_size) ch->source_byte_offset = 0;
                    else ended = true;
                    remainder[i] = 0;
                }
            } else if (mp_obj_is_type(ch->source, &tone_sound_resource_class_type)) {
                sample = tone_sound_resource_get_sample(ch->source);
            } else if (mp_obj_is_type(ch->source, &rtttl_sound_resource_class_type)) {
                sample = rtttl_sound_resource_get_sample(ch->source, &ended);
            }
            ch->amplitude = sample;
            mix += sample * ch->gain;
            if (ended && !ch->loop) audio_channel_stop(ch);
        }
        pcm[n] = fminf(1, fmaxf(-1, mix * master_volume * game_volume));
    }
    return pcm;
}
