// SPDX-License-Identifier: GPL-3.0-only
#include "pause_wait.h"
#include <cassert>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <future>
#include <mutex>

using namespace std::chrono_literals;
struct wakeup {
    std::mutex mutex;
    std::condition_variable changed;
    int credits = 0;
    void notify() {
        std::lock_guard<std::mutex> lock(mutex);
        ++credits;
        changed.notify_one();
    }
    void wait() {
        std::unique_lock<std::mutex> lock(mutex);
        changed.wait(lock, [&] { return credits > 0; });
        --credits;
    }
};

int main() {
    std::atomic<bool> paused{false}, quit{false};
    wakeup signal;
    for (int i = 0; i < 100; ++i) abyssme::resume_worker(paused, signal);
    assert(signal.credits == 0); // Surface resize and onResume must not bank permits.
    paused = true;
    abyssme::resume_worker(paused, signal); // Resume before the worker enters wait.
    paused = true; // A newer pause owns the state even with that old signal pending.
    assert(signal.credits == 1);
    // The former single wait returns here while still paused: deterministic reproduction.
    signal.wait();
    assert(paused);
    signal.notify();
    auto worker = std::async(std::launch::async, [&] { abyssme::wait_for_resume(paused, quit, signal); });
    assert(worker.wait_for(50ms) == std::future_status::timeout);
    signal.notify(); // Extra/spurious wake must not resume guest execution.
    assert(worker.wait_for(50ms) == std::future_status::timeout);
    abyssme::resume_worker(paused, signal);
    assert(worker.wait_for(2s) == std::future_status::ready);
    worker.get();
    for (int i = 0; i < 200; ++i) {
        paused = true;
        auto next = std::async(std::launch::async, [&] { abyssme::wait_for_resume(paused, quit, signal); });
        abyssme::resume_worker(paused, signal);
        assert(next.wait_for(2s) == std::future_status::ready);
        next.get();
    }
    paused = true;
    auto stopping = std::async(std::launch::async, [&] { abyssme::wait_for_resume(paused, quit, signal); });
    assert(stopping.wait_for(50ms) == std::future_status::timeout);
    quit = true;
    abyssme::resume_worker(paused, signal);
    assert(stopping.wait_for(2s) == std::future_status::ready);
    stopping.get();
    std::puts("Pause wait: old credits, redundant resumes, extra wake, 200 races and stop while paused pass");
}
