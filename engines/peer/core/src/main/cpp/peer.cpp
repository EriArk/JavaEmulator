// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <rtc/rtc.hpp>
#include <condition_variable>
#include <deque>
#include <map>
#include <mutex>
#include <stdexcept>

namespace {
constexpr size_t MAX_MESSAGE = 16384, MAX_QUEUE_BYTES = 1024 * 1024;
constexpr char LABEL[] = "abyssme-game-v1";
constexpr char PROTOCOL[] = "org.abyssme.peer.v1";
struct Event {
    int kind;
    std::string text, extra;
    rtc::binary data;
    size_t size() const { return text.size() + extra.size() + data.size(); }
};
struct Peer : std::enable_shared_from_this<Peer> {
    std::mutex mutex, api;
    std::condition_variable wake;
    std::deque<Event> events;
    size_t queued = 0;
    bool stopped = false, failed = false, started = false, remote = false, host;
    unsigned candidates = 0;
    std::shared_ptr<rtc::PeerConnection> pc;
    std::shared_ptr<rtc::DataChannel> dc;

    explicit Peer(bool h) : host(h) {}
    void push(Event e) {
        std::lock_guard<std::mutex> lock(mutex);
        if (stopped || failed) return;
        if (events.size() >= 256 || queued + e.size() > MAX_QUEUE_BYTES) {
            failed = true;
            events.clear(); queued = 0;
            events.push_back({6, "Peer receive queue exceeded", {}, {}});
        } else {
            queued += e.size();
            events.push_back(std::move(e));
        }
        wake.notify_all();
    }
    void fail() {
        std::lock_guard<std::mutex> lock(mutex);
        if (failed || stopped) return;
        failed = true; events.clear(); queued = 0;
        events.push_back({6, "Peer protocol or transport failure", {}, {}});
        wake.notify_all();
    }
    void check() {
        std::lock_guard<std::mutex> lock(mutex);
        if (stopped || failed) throw std::runtime_error("Peer closed or failed");
    }
    void attach(std::shared_ptr<rtc::DataChannel> channel) {
        const auto reliability = channel->reliability();
        bool accept;
        {
            std::lock_guard<std::mutex> lock(mutex);
            accept = !stopped && !failed && !dc && channel->label() == LABEL
                && channel->protocol() == PROTOCOL && !reliability.unordered
                && !reliability.maxPacketLifeTime && !reliability.maxRetransmits;
            if (accept) dc = channel;
        }
        if (!accept) { fail(); return; }
        std::weak_ptr<Peer> weak = shared_from_this();
        channel->onOpen([weak] { if (auto p = weak.lock()) p->push({3, {}, {}, {}}); });
        channel->onClosed([weak] { if (auto p = weak.lock()) p->push({5, {}, {}, {}}); });
        channel->onError([weak](std::string) { if (auto p = weak.lock()) p->fail(); });
        channel->onMessage([weak](rtc::message_variant message) {
            if (auto p = weak.lock()) {
                if (!std::holds_alternative<rtc::binary>(message)) { p->fail(); return; }
                auto data = std::get<rtc::binary>(std::move(message));
                if (data.empty() || data.size() > MAX_MESSAGE) { p->fail(); return; }
                p->push({4, {}, {}, std::move(data)});
            }
        });
    }
    void setup(const std::string &stun) {
        rtc::Configuration config;
        config.disableAutoNegotiation = true;
        config.maxMessageSize = MAX_MESSAGE;
        if (!stun.empty()) config.iceServers.emplace_back(stun);
        pc = std::make_shared<rtc::PeerConnection>(config);
        std::weak_ptr<Peer> weak = shared_from_this();
        pc->onLocalDescription([weak](rtc::Description desc) {
            if (auto p = weak.lock()) p->push({1, std::string(desc), desc.typeString(), {}});
        });
        pc->onLocalCandidate([weak](rtc::Candidate c) {
            if (auto p = weak.lock()) p->push({2, std::string(c), c.mid(), {}});
        });
        pc->onDataChannel([weak](std::shared_ptr<rtc::DataChannel> c) {
            if (auto p = weak.lock()) {
                if (p->host) p->fail(); else p->attach(std::move(c));
            }
        });
        pc->onStateChange([weak](rtc::PeerConnection::State state) {
            if (auto p = weak.lock()) {
                if (state == rtc::PeerConnection::State::Connecting) p->push({7, "Peer connecting", {}, {}});
                if (state == rtc::PeerConnection::State::Connected) p->push({7, "Peer connected", {}, {}});
                if (state == rtc::PeerConnection::State::Failed) p->fail();
                if (state == rtc::PeerConnection::State::Closed
                        || state == rtc::PeerConnection::State::Disconnected) p->push({5, {}, {}, {}});
            }
        });
        pc->onIceStateChange([weak](rtc::PeerConnection::IceState state) {
            if (auto p = weak.lock()) {
                const char *phase = nullptr;
                switch (state) {
                    case rtc::PeerConnection::IceState::Checking: phase = "ICE checking"; break;
                    case rtc::PeerConnection::IceState::Connected: phase = "ICE connected"; break;
                    case rtc::PeerConnection::IceState::Completed: phase = "ICE completed"; break;
                    default: break;
                }
                if (phase) p->push({7, phase, {}, {}});
            }
        });
    }
    void stop() {
        std::lock_guard<std::mutex> commands(api);
        std::shared_ptr<rtc::DataChannel> channel;
        {
            std::lock_guard<std::mutex> lock(mutex);
            stopped = true; events.clear(); queued = 0; channel = dc;
            wake.notify_all();
        }
        if (channel) { channel->resetCallbacks(); channel->close(); }
        if (pc) { pc->resetCallbacks(); pc->close(); }
    }
};
std::mutex registryMutex;
std::map<jlong, std::shared_ptr<Peer>> registry;
jlong nextId = 1;
std::shared_ptr<Peer> get(jlong id) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto it = registry.find(id);
    if (it == registry.end()) throw std::runtime_error("Peer closed");
    return it->second;
}
std::string string(JNIEnv *env, jstring value, size_t max) {
    if (!value || env->GetStringUTFLength(value) > max) throw std::runtime_error("Invalid signaling length");
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("Cannot read signaling");
    std::string result(chars); env->ReleaseStringUTFChars(value, chars);
    for (unsigned char c : result) if (c > 127 || c == 0) throw std::runtime_error("Non-ASCII signaling");
    return result;
}
void error(JNIEnv *env) {
    // Never expose SDP, IP addresses, credentials or upstream exception text.
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), "Secure peer operation failed");
}
}

#define JNI_METHOD(name) Java_io_github_eriark_abyssme_peer_NativePeer_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(create)(JNIEnv *env, jclass, jboolean host, jstring server) {
    try {
        std::string stun = string(env, server, 256);
        if (!stun.empty() && stun.rfind("stun:", 0) != 0) throw std::runtime_error("STUN only");
        std::lock_guard<std::mutex> lock(registryMutex);
        if (registry.size() >= 8) throw std::runtime_error("Peer limit");
        auto p = std::make_shared<Peer>(host);
        p->setup(stun);
        jlong id = nextId++;
        registry.emplace(id, std::move(p));
        return id;
    } catch (...) { error(env); return 0; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(start)(JNIEnv *env, jclass, jlong id) {
    try {
        auto p = get(id); std::lock_guard<std::mutex> lock(p->api); p->check();
        if (p->started) throw std::runtime_error("Already started");
        p->started = true;
        if (p->host) {
            rtc::DataChannelInit init; init.protocol = PROTOCOL;
            p->attach(p->pc->createDataChannel(LABEL, init));
            p->pc->setLocalDescription(rtc::Description::Type::Offer);
        }
    } catch (...) { error(env); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(description)(JNIEnv *env, jclass, jlong id, jstring sdp, jstring type) {
    try {
        auto p = get(id); std::lock_guard<std::mutex> lock(p->api); p->check();
        auto kind = string(env, type, 6);
        if (!p->started || p->remote || kind != (p->host ? "answer" : "offer")) throw std::runtime_error("Unexpected SDP");
        rtc::Description desc(string(env, sdp, 24576), kind);
        auto fingerprint = desc.fingerprint();
        if (!fingerprint || !fingerprint->isValid()
                || fingerprint->algorithm != rtc::CertificateFingerprint::Algorithm::Sha256
                || desc.candidates().size() > 32) throw std::runtime_error("Invalid SDP identity");
        p->remote = true;
        p->pc->setRemoteDescription(desc);
        if (!p->host) p->pc->setLocalDescription(rtc::Description::Type::Answer);
    } catch (...) { error(env); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(candidate)(JNIEnv *env, jclass, jlong id, jstring candidate, jstring mid) {
    try {
        auto p = get(id); std::lock_guard<std::mutex> lock(p->api); p->check();
        if (!p->remote || ++p->candidates > 64) throw std::runtime_error("Unexpected candidate");
        p->pc->addRemoteCandidate(rtc::Candidate(string(env, candidate, 512), string(env, mid, 32)));
    } catch (...) { error(env); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(send)(JNIEnv *env, jclass, jlong id, jbyteArray input) {
    try {
        auto p = get(id); std::lock_guard<std::mutex> lock(p->api); p->check();
        auto size = env->GetArrayLength(input);
        if (size < 1 || size > MAX_MESSAGE) throw std::runtime_error("Invalid frame");
        std::shared_ptr<rtc::DataChannel> channel;
        { std::lock_guard<std::mutex> guard(p->mutex); channel = p->dc; }
        if (!channel || !channel->isOpen() || channel->bufferedAmount() + size > 256 * 1024)
            throw std::runtime_error("Peer not writable");
        rtc::binary data(size);
        env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte *>(data.data()));
        if (env->ExceptionCheck()) return;
        // false means accepted into SCTP's buffer, not rejected; never resend it.
        channel->send(std::move(data));
    } catch (...) { error(env); }
}
extern "C" JNIEXPORT jobject JNICALL JNI_METHOD(poll)(JNIEnv *env, jclass, jlong id, jint timeout) {
    try {
        auto p = get(id);
        Event e;
        {
            std::unique_lock<std::mutex> lock(p->mutex);
            p->wake.wait_for(lock, std::chrono::milliseconds(std::max(0, std::min(250, timeout))),
                [&] { return p->stopped || !p->events.empty(); });
            if (p->stopped) return nullptr;
            if (p->events.empty()) return nullptr;
            e = std::move(p->events.front()); p->events.pop_front();
            p->queued = p->queued >= e.size() ? p->queued - e.size() : 0;
        }
        auto cls = env->FindClass("io/github/eriark/abyssme/peer/NativePeer$Event");
        auto ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;Ljava/lang/String;[B)V");
        auto data = env->NewByteArray(e.data.size());
        if (!data) return nullptr;
        if (!e.data.empty()) env->SetByteArrayRegion(data, 0, e.data.size(), reinterpret_cast<jbyte *>(e.data.data()));
        return env->NewObject(cls, ctor, e.kind, env->NewStringUTF(e.text.c_str()), env->NewStringUTF(e.extra.c_str()), data);
    } catch (...) { error(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(destroy)(JNIEnv *, jclass, jlong id) {
    std::shared_ptr<Peer> p;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        auto it = registry.find(id);
        if (it == registry.end()) return;
        p = it->second; registry.erase(it);
    }
    try { p->stop(); } catch (...) { /* Closing remains idempotent. */ }
}
