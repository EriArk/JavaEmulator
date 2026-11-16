/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 AbyssME contributors
 *
 * Standalone staging event loop; no vendor headers, binaries or game code.
 */
#include "abyss_brew_host.h"

#include <stdlib.h>

struct AbyssBrewHost {
    AbyssBrewHostHooks hooks;
    AbyssBrewHostState state;
    AbyssBrewHostEvent events[ABYSS_BREW_HOST_QUEUE_CAPACITY];
    size_t head;
    size_t count;
};

AbyssBrewHost *abyss_brew_host_create(const AbyssBrewHostHooks *hooks) {
    if (hooks == NULL || hooks->dispatch == NULL) {
        return NULL;
    }
    AbyssBrewHost *host = (AbyssBrewHost *)calloc(1, sizeof(*host));
    if (host != NULL) {
        host->hooks = *hooks;
        host->state = ABYSS_BREW_HOST_STOPPED;
    }
    return host;
}

void abyss_brew_host_destroy(AbyssBrewHost *host) {
    free(host);
}

AbyssBrewHostState abyss_brew_host_state(const AbyssBrewHost *host) {
    return host == NULL ? ABYSS_BREW_HOST_STOPPED : host->state;
}

int abyss_brew_host_start(AbyssBrewHost *host) {
    if (host == NULL || host->state != ABYSS_BREW_HOST_STOPPED) {
        return -1;
    }
    host->state = ABYSS_BREW_HOST_RUNNING;
    return 0;
}

int abyss_brew_host_pause(AbyssBrewHost *host) {
    if (host == NULL || host->state != ABYSS_BREW_HOST_RUNNING) {
        return -1;
    }
    host->state = ABYSS_BREW_HOST_PAUSED;
    return 0;
}

int abyss_brew_host_resume(AbyssBrewHost *host) {
    if (host == NULL || host->state != ABYSS_BREW_HOST_PAUSED) {
        return -1;
    }
    host->state = ABYSS_BREW_HOST_RUNNING;
    return 0;
}

int abyss_brew_host_stop(AbyssBrewHost *host) {
    if (host == NULL || host->state == ABYSS_BREW_HOST_STOPPED) {
        return -1;
    }
    host->state = ABYSS_BREW_HOST_STOPPED;
    host->head = 0;
    host->count = 0;
    return 0;
}

int abyss_brew_host_post(AbyssBrewHost *host, AbyssBrewHostEvent event) {
    if (host == NULL || host->state != ABYSS_BREW_HOST_RUNNING ||
        (event.type != ABYSS_BREW_HOST_KEY_DOWN &&
         event.type != ABYSS_BREW_HOST_KEY_UP &&
         event.type != ABYSS_BREW_HOST_TIMER)) {
        return -1;
    }
    if (host->count >= ABYSS_BREW_HOST_QUEUE_CAPACITY) {
        return -2;
    }
    size_t index = (host->head + host->count) % ABYSS_BREW_HOST_QUEUE_CAPACITY;
    host->events[index] = event;
    host->count++;
    return 0;
}

int abyss_brew_host_pump(AbyssBrewHost *host, size_t max_events) {
    if (host == NULL || host->state != ABYSS_BREW_HOST_RUNNING ||
        max_events == 0) {
        return -1;
    }
    int dispatched = 0;
    while (host->state == ABYSS_BREW_HOST_RUNNING && host->count > 0 &&
           (size_t)dispatched < max_events) {
        AbyssBrewHostEvent event = host->events[host->head];
        host->head = (host->head + 1) % ABYSS_BREW_HOST_QUEUE_CAPACITY;
        host->count--;
        host->hooks.dispatch(host->hooks.user, &event);
        dispatched++;
    }
    return dispatched;
}

size_t abyss_brew_host_pending(const AbyssBrewHost *host) {
    return host == NULL ? 0 : host->count;
}
