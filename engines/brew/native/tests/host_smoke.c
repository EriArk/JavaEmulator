/* SPDX-License-Identifier: GPL-3.0-only */
#include "abyss_brew_host.h"

#include <assert.h>
#include <stdint.h>

typedef struct TestSink {
    int calls;
    uint32_t key_sequence[ABYSS_BREW_HOST_QUEUE_CAPACITY];
} TestSink;

static void dispatch(void *user, const AbyssBrewHostEvent *event) {
    TestSink *sink = (TestSink *)user;
    assert(event != NULL);
    assert((size_t)sink->calls < ABYSS_BREW_HOST_QUEUE_CAPACITY);
    sink->key_sequence[sink->calls++] = event->code;
}

int main(void) {
    TestSink sink = {0};
    AbyssBrewHostHooks hooks = {&sink, dispatch};
    AbyssBrewHost *host = abyss_brew_host_create(&hooks);
    assert(host != NULL);
    assert(abyss_brew_host_state(host) == ABYSS_BREW_HOST_STOPPED);
    assert(abyss_brew_host_pump(host, 1) == -1);
    assert(abyss_brew_host_start(host) == 0);

    for (uint32_t i = 0; i < ABYSS_BREW_HOST_QUEUE_CAPACITY; i++) {
        AbyssBrewHostEvent e = {ABYSS_BREW_HOST_KEY_DOWN, i, i * 10u};
        assert(abyss_brew_host_post(host, e) == 0);
    }
    AbyssBrewHostEvent extra = {ABYSS_BREW_HOST_KEY_DOWN, 999, 0};
    assert(abyss_brew_host_post(host, extra) == -2);
    assert(abyss_brew_host_pending(host) == ABYSS_BREW_HOST_QUEUE_CAPACITY);
    assert(abyss_brew_host_pump(host, 31) == 31);
    assert(sink.calls == 31);
    assert(sink.key_sequence[0] == 0);
    assert(sink.key_sequence[30] == 30);

    assert(abyss_brew_host_pause(host) == 0);
    assert(abyss_brew_host_pump(host, 2) == -1);
    assert(abyss_brew_host_post(host, extra) == -1);
    assert(abyss_brew_host_pending(host) == 33);
    assert(abyss_brew_host_resume(host) == 0);
    assert(abyss_brew_host_pump(host, 64) == 33);
    assert(sink.calls == 64);
    assert(sink.key_sequence[63] == 63);

    assert(abyss_brew_host_post(host, extra) == 0);
    assert(abyss_brew_host_stop(host) == 0);
    assert(abyss_brew_host_pending(host) == 0);
    assert(abyss_brew_host_start(host) == 0);
    assert(abyss_brew_host_pump(host, 1) == 0);
    assert(abyss_brew_host_stop(host) == 0);
    abyss_brew_host_destroy(host);
    return 0;
}
