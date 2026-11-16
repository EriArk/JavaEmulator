/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 AbyssME contributors
 *
 * Original host integration contract. This is not Qualcomm's BREW API/ABI.
 */
#ifndef ABYSS_BREW_HOST_H
#define ABYSS_BREW_HOST_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ABYSS_BREW_HOST_API_VERSION 1u
#define ABYSS_BREW_HOST_QUEUE_CAPACITY 64u

typedef struct AbyssBrewHost AbyssBrewHost;

typedef enum AbyssBrewHostState {
    ABYSS_BREW_HOST_STOPPED = 0,
    ABYSS_BREW_HOST_RUNNING = 1,
    ABYSS_BREW_HOST_PAUSED = 2
} AbyssBrewHostState;

typedef enum AbyssBrewHostEventType {
    ABYSS_BREW_HOST_KEY_DOWN = 1,
    ABYSS_BREW_HOST_KEY_UP = 2,
    ABYSS_BREW_HOST_TIMER = 3
} AbyssBrewHostEventType;

typedef struct AbyssBrewHostEvent {
    AbyssBrewHostEventType type;
    uint32_t code;
    uint64_t timestamp_ms;
} AbyssBrewHostEvent;

typedef void (*AbyssBrewHostDispatch)(void *user, const AbyssBrewHostEvent *event);

typedef struct AbyssBrewHostHooks {
    void *user;
    AbyssBrewHostDispatch dispatch;
} AbyssBrewHostHooks;

/* Single-threaded staging API; callers synchronize across Android threads. */
AbyssBrewHost *abyss_brew_host_create(const AbyssBrewHostHooks *hooks);
void abyss_brew_host_destroy(AbyssBrewHost *host);
AbyssBrewHostState abyss_brew_host_state(const AbyssBrewHost *host);
int abyss_brew_host_start(AbyssBrewHost *host);
int abyss_brew_host_pause(AbyssBrewHost *host);
int abyss_brew_host_resume(AbyssBrewHost *host);
int abyss_brew_host_stop(AbyssBrewHost *host);

/* Returns 0 on success, -1 on invalid input/state, -2 when queue is full. */
int abyss_brew_host_post(AbyssBrewHost *host, AbyssBrewHostEvent event);
/* Dispatches up to max_events, returns count, or -1 when not running/invalid. */
int abyss_brew_host_pump(AbyssBrewHost *host, size_t max_events);
size_t abyss_brew_host_pending(const AbyssBrewHost *host);

#ifdef __cplusplus
}
#endif
#endif /* ABYSS_BREW_HOST_H */
