// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <atomic>

namespace abyssme {
// Wakeups are hints: a previous Resume can arrive before the worker enters wait.
// Recheck state after each wake instead of allowing a stale credit to unpause it.
template<class Wakeup>
void wait_for_resume(const std::atomic<bool> &paused, const std::atomic<bool> &quit,
        Wakeup &wakeup) {
    while (paused.load() && !quit.load()) wakeup.wait();
}

template<class Wakeup>
void resume_worker(std::atomic<bool> &paused, Wakeup &wakeup) {
    if (paused.exchange(false)) wakeup.notify();
}
}
