// SPDX-License-Identifier: GPL-3.0-only
// Lifecycle adapter for the pinned EKA2L1 Android bridge.
#include <jni.h>
#include <android/log.h>
#include <android/state.h>
#include <android/thread.h>
#include <android/launcher.h>
#include <common/android/jniutils.h>
#include <common/android/storage.h>
#include <common/fileutils.h>
#include <common/path.h>
#include <drivers/audio/audio.h>
#include <drivers/camera/backend/android/emulator_camera_jni_public.h>
#include <drivers/graphics/graphics.h>
#include <system/devices.h>
#include <system/software.h>
#include <filesystem>
#include <mutex>
#include <stdexcept>
#include <common/algorithm.h>
#include <common/archive.h>
#include <archive.h>
#include <archive_entry.h>
#include <vfs/vfs.h>
#include <services/applist/applist.h>
#include <kernel/kernel.h>
#include <kernel/process.h>
#include <package/manager.h>
#include <loader/mbm.h>
#include <services/fbs/fbs.h>
#include <services/fbs/bitmap.h>
#include <services/window/screen.h>
#include <cmath>
#include <utils/system.h>
#include <utils/locale.h>
#include <sstream>
#include <fstream>
#include <yaml-cpp/yaml.h>
#include "card_identity.h"
#include "frame_rate.h"

extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_setFrameRateLimit(JNIEnv *, jclass, jint fps) {
    abyss::frame_rate::set(fps);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_github_eka2l1_emu_Emulator_getFrameRateLimit(JNIEnv *, jclass) {
    return abyss::frame_rate::limit.load(std::memory_order_relaxed);
}

extern std::unique_ptr<eka2l1::android::emulator> state;
extern std::unique_ptr<std::thread> os_thread_obj;
extern std::unique_ptr<std::thread> gr_thread_obj;
extern bool inited;
static bool callbacks_ready = false;
// Stream only regular files/directories. Java writes a private ZIP, never archive-selected host paths.
extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_decodeGameArchive(JNIEnv *env, jclass, jstring source, jobject sink) {
    struct archive *reader = archive_read_new();
    if (!reader) { env->ThrowNew(env->FindClass("java/io/IOException"), "Cannot open archive reader"); return; }
    jbyteArray bytes = nullptr;
    try {
        archive_read_support_format_7zip(reader);
        archive_read_support_format_rar(reader);
        archive_read_support_format_rar5(reader);
        const char *raw = env->GetStringUTFChars(source, nullptr);
        if (!raw) { archive_read_free(reader); return; }
        const int opened = archive_read_open_filename(reader, raw, 65536);
        env->ReleaseStringUTFChars(source, raw);
        if (opened != ARCHIVE_OK) throw std::runtime_error("Cannot read this RAR / 7z archive; encrypted or incomplete archives are not supported");
        jclass cls = env->GetObjectClass(sink);
        auto begin = env->GetMethodID(cls, "begin", "(Ljava/lang/String;JZ)V");
        auto write = env->GetMethodID(cls, "write", "([BI)V");
        auto end = env->GetMethodID(cls, "end", "()V");
        env->DeleteLocalRef(cls);
        if (env->ExceptionCheck()) { archive_read_free(reader); return; }
        bytes = env->NewByteArray(65536);
        std::uint64_t total = 0;
        std::size_t count = 0;
        struct archive_entry *entry = nullptr;
        int result = ARCHIVE_OK;
        while (!env->ExceptionCheck() && (result = archive_read_next_header(reader, &entry)) == ARCHIVE_OK) {
            if (++count > 20000) throw std::runtime_error("Too many game archive entries");
            auto type = archive_entry_filetype(entry);
            if ((type != AE_IFREG && type != AE_IFDIR) || archive_entry_hardlink(entry) || archive_entry_symlink(entry)
                    || archive_entry_is_encrypted(entry)) throw std::runtime_error("Links and encrypted entries are not supported");
            auto size = archive_entry_size(entry);
            if (!archive_entry_size_is_set(entry) || size < 0 || static_cast<std::uint64_t>(size) > (1ULL << 30) - total)
                throw std::runtime_error("Expanded game archive exceeds 1 GB or has an unknown size");
            total += size;
            const char *path = archive_entry_pathname_utf8(entry);
            if (!path) path = archive_entry_pathname(entry);
            if (!path) throw std::runtime_error("Archive filename is missing");
            jstring name = env->NewStringUTF(path);
            if (!name) break;
            env->CallVoidMethod(sink, begin, name, static_cast<jlong>(size), static_cast<jboolean>(type == AE_IFDIR));
            env->DeleteLocalRef(name);
            std::uint64_t written = 0;
            char buffer[65536];
            while (!env->ExceptionCheck()) {
                la_ssize_t n = archive_read_data(reader, buffer, sizeof(buffer));
                if (n == 0) break;
                if (n < 0 || static_cast<std::uint64_t>(n) > static_cast<std::uint64_t>(size) - written)
                    throw std::runtime_error("Damaged, encrypted or unsupported archive entry");
                written += n;
                env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(n), reinterpret_cast<jbyte *>(buffer));
                if (!env->ExceptionCheck()) env->CallVoidMethod(sink, write, bytes, static_cast<jint>(n));
            }
            if (env->ExceptionCheck()) break;
            if (written != static_cast<std::uint64_t>(size)) throw std::runtime_error("Incomplete archive entry");
            env->CallVoidMethod(sink, end);
        }
        if (!env->ExceptionCheck() && (result != ARCHIVE_EOF || count == 0))
            throw std::runtime_error("Incomplete or unsupported RAR / 7z archive");
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
    }
    if (bytes) env->DeleteLocalRef(bytes);
    archive_read_free(reader);
}
// Package UIDs are distinct from application UIDs. Query the selected phone's registry.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_github_eka2l1_emu_Emulator_installedPackageVersion(JNIEnv *env, jclass, jlong uid) {
    if (!state || !state->symsys || os_thread_obj || gr_thread_obj) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "Close the game before checking components");
        return nullptr;
    }
    auto *packages = state->symsys->get_packages();
    if (!packages) return nullptr;
    std::lock_guard<std::mutex> lock(packages->lockdown);
    auto *package = packages->package(static_cast<std::uint32_t>(uid));
    // Match packages::installed(); this core does not populate completely_present on host installs.
    if (!package || package->install_type != eka2l1::package::install_type_normal_install) return nullptr;
    jint version[] = {package->version.major, package->version.minor, package->version.build};
    auto result = env->NewIntArray(3);
    if (result) env->SetIntArrayRegion(result,0,3,version);
    return result;
}
namespace eka2l1::loader { bool abyss_detect_prepared_version(const std::string &, epocver &); }

// Read the same viewport used by window-server input conversion. The Java host
// only undoes its TextureView transform; native scaling must not be applied twice.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_github_eka2l1_emu_Emulator_touchBounds(JNIEnv *env, jclass) {
    if (!state || !state->symsys || !os_thread_obj || !state->winserv) return nullptr;
    auto *kernel = state->symsys->get_kernel_system();
    std::lock_guard<eka2l1::kernel_system> kernel_lock(*kernel);
    auto *screen = state->winserv->get_current_focus_screen();
    if (!screen) return nullptr;
    std::lock_guard<std::mutex> screen_lock(screen->screen_mutex);
    int width = static_cast<int>(std::round(screen->current_mode().size.x * screen->logic_scale_factor_x));
    int height = static_cast<int>(std::round(screen->current_mode().size.y * screen->logic_scale_factor_y));
    if (screen->ui_rotation % 180 != 0) std::swap(width,height);
    if (width <= 0 || height <= 0) return nullptr;
    jint bounds[] = {screen->absolute_pos.x, screen->absolute_pos.y,
        screen->absolute_pos.x + width, screen->absolute_pos.y + height};
    auto result = env->NewIntArray(4);
    if (result) env->SetIntArrayRegion(result,0,4,bounds);
    return result;
}

// Portable snapshots retain guest identity/settings, never host paths, network secrets or scripts.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_github_eka2l1_emu_Emulator_portablePhoneSettings(JNIEnv *env, jclass, jstring directory) {
    try {
        if (os_thread_obj || gr_thread_obj) throw std::runtime_error("Close the game before transferring its phone");
        const char *raw = env->GetStringUTFChars(directory, nullptr);
        const std::filesystem::path root(raw);
        env->ReleaseStringUTFChars(directory, raw);
        const auto read = [](const std::filesystem::path &file) {
            if (std::filesystem::file_size(file) > 131072) throw std::runtime_error("Phone settings are too large");
            return YAML::LoadFile(file.string());
        };
        // Imported phones have no config until their first boot. Export defaults
        // without booting the source or creating files in its writable drives.
        YAML::Node source = std::filesystem::exists(root / "config.yml")
            ? read(root / "config.yml") : YAML::Node(YAML::NodeType::Map);
        YAML::Node config(YAML::NodeType::Map);
        if (!source.IsMap()) throw std::runtime_error("Invalid phone settings");
        for (const auto *key : {"language", "imei", "mmc-id", "audio-master-volume", "enable-hw-gles1",
                "enable-srv-rights", "enable-srv-sa", "enable-srv-drm", "fbs-enable-compression-queue",
                "cpu-load-save", "screen-buffer-sync", "report-mmfdev-underflow"}) {
            if (source[key]) {
                if (!source[key].IsScalar() || source[key].Scalar().size() > 128)
                    throw std::runtime_error("Invalid portable phone setting");
                config[key] = source[key].Scalar();
            }
        }
        for (const auto *key : {"enable-hw-gles1", "enable-srv-rights", "enable-srv-sa", "enable-srv-drm",
                "fbs-enable-compression-queue", "cpu-load-save", "report-mmfdev-underflow"})
            if (source[key]) config[key] = source[key].as<bool>();
        if (source["language"]) {
            int language = source["language"].as<int>();
            if (language < -1 || language > 1024) throw std::runtime_error("Invalid phone language");
            config["language"] = language;
        }
        if (source["audio-master-volume"]) {
            int volume = source["audio-master-volume"].as<int>();
            if (volume < 0 || volume > 100) throw std::runtime_error("Invalid phone volume");
            config["audio-master-volume"] = volume;
        }
        config["data-storage"] = "data";
        config["device"] = 0;
        config["enable-gdb-stub"] = false;
        config["enable-upnp"] = false;
        YAML::Node devices = read(root / "data/devices.yml"), clean(YAML::NodeType::Map);
        if (!devices.IsMap() || devices.size() != 1) throw std::runtime_error("A phone backup must contain exactly one phone");
        auto pair = *devices.begin();
        const std::string firmware = pair.first.as<std::string>();
        if (firmware.empty() || firmware.size() > 64 || firmware.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_") != std::string::npos)
            throw std::runtime_error("Invalid phone firmware identity");
        auto device = pair.second;
        if (!device.IsMap() || device["pending-deletion"].as<bool>(false)) throw std::runtime_error("Phone is pending removal");
        for (const auto *key : {"platver", "manufacturer", "model", "machine-uid"}) {
            if (!device[key].IsScalar() || device[key].Scalar().size() > 256) throw std::runtime_error("Invalid phone registration");
            clean[firmware][key] = device[key].Scalar();
        }
        clean[firmware]["firmcode"] = firmware;
        clean[firmware]["machine-uid"] = device["machine-uid"].as<std::uint32_t>();
        if (!device["isolated-drives"].as<bool>(false)) throw std::runtime_error("Only isolated phones can be transferred");
        clean[firmware]["isolated-drives"] = true;
        YAML::Emitter settings_out, devices_out;
        settings_out << config; devices_out << clean;
        auto result = env->NewObjectArray(2, env->FindClass("java/lang/String"), nullptr);
        env->SetObjectArrayElement(result, 0, env->NewStringUTF(settings_out.c_str()));
        env->SetObjectArrayElement(result, 1, env->NewStringUTF(devices_out.c_str()));
        return result;
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_extractSystemArchive(JNIEnv *env, jclass, jstring source, jobject plan) {
    try {
        const char *raw = env->GetStringUTFChars(source, nullptr);
        if (!raw) return;
        std::string archive(raw); env->ReleaseStringUTFChars(source, raw);
        jclass cls = env->GetObjectClass(plan);
        const auto destination = env->GetMethodID(cls, "destination", "(Ljava/lang/String;JZ)Ljava/lang/String;");
        const auto progress = env->GetMethodID(cls, "progress", "(JJ)V");
        env->DeleteLocalRef(cls);
        if (env->ExceptionCheck()) return;
        bool ok = eka2l1::common::extract_archive(archive,
            [&](const eka2l1::common::archive_entry_info &entry) -> std::string {
                if (env->ExceptionCheck()) return {};
                jstring name = env->NewStringUTF(entry.path.c_str());
                if (!name) return {};
                auto target = static_cast<jstring>(env->CallObjectMethod(plan, destination, name,
                    static_cast<jlong>(entry.size), static_cast<jboolean>(entry.is_directory)));
                env->DeleteLocalRef(name);
                if (env->ExceptionCheck() || !target) return {};
                const char *path = env->GetStringUTFChars(target, nullptr);
                std::string result = path ? path : "";
                if (path) env->ReleaseStringUTFChars(target, path);
                env->DeleteLocalRef(target);
                return result;
            }, [&](std::size_t done, std::size_t total) {
                if (!env->ExceptionCheck()) env->CallVoidMethod(plan, progress, static_cast<jlong>(done), static_cast<jlong>(total));
            }, [&] { return env->ExceptionCheck(); });
        if (!ok && !env->ExceptionCheck()) throw std::runtime_error("Cannot decode system archive or write its files");
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_installPreparedDevice(JNIEnv *env, jclass, jstring rom_arg, jstring z_arg, jstring) {
    try {
        if (!state || !state->symsys || os_thread_obj || gr_thread_obj)
            throw std::runtime_error("Stop the game before importing system files");
        auto text = [env](jstring value) {
            const char *raw = env->GetStringUTFChars(value, nullptr);
            if (!raw) throw std::runtime_error("Cannot read system path");
            std::string result(raw); env->ReleaseStringUTFChars(value, raw); return result;
        };
        const auto rom = text(rom_arg), z = text(z_arg);
        auto *manager = state->symsys->get_device_manager();
        if (manager->total() != 0) throw std::runtime_error("Prepared systems require a new environment");
        std::string manufacturer, firmware, model;
        if (!eka2l1::loader::determine_rpkg_product_info(z + "/", manufacturer, firmware, model))
            throw std::runtime_error("Cannot identify this phone's system files");
        const auto lower = eka2l1::common::lowercase_string(firmware);
        // Collection folder labels are aliases; the phone identifies itself through Z.
        if (lower.empty() || lower.size() > 128 || lower == "." || lower == ".."
                || lower.find_first_not_of("abcdefghijklmnopqrstuvwxyz0123456789 ._-") != std::string::npos)
            throw std::runtime_error("Invalid firmware identity in system files");
        if (model.empty()) model = firmware;
        epocver version;
        if (!eka2l1::loader::abyss_detect_prepared_version(z + "/", version))
            throw std::runtime_error("Cannot identify the Symbian version; this system was not installed");
        const auto uid = eka2l1::loader::determine_rpkg_machine_uid(z + "/");
        const std::filesystem::path data(state->conf.storage);
        const auto target_z = data / "drives" / "z" / lower;
        const auto target_rom = data / "roms" / lower / "SYM.ROM";
        std::filesystem::create_directories(target_z.parent_path());
        std::filesystem::create_directories(target_rom.parent_path());
        std::filesystem::rename(z, target_z);
        std::filesystem::copy_file(rom, target_rom);
        if (manager->add_new_device(firmware, model, manufacturer, version, uid, true) != eka2l1::add_device_none)
            throw std::runtime_error("Cannot register this phone");
        manager->set_current(firmware);
        manager->save_devices();
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
    }
}

namespace eka2l1 {
void abyss_prepare_ngage_metadata(io_system *io, const std::u16string &guest_path) {
    std::u16string normalized = guest_path;
    for (auto &c : normalized) {
        if (c >= u'A' && c <= u'Z') c += u'a' - u'A';
        if (c == u'/') c = u'\\';
    }
    if (normalized != u"c:\\private\\20007b38\\import\\achievements.xml") return;
    const auto raw = io->get_raw_path(guest_path);
    if (!raw) return;
    auto *env = common::jni::environment();
    const auto cls = common::jni::find_class("com/github/eka2l1/emu/Emulator");
    const auto method = env->GetStaticMethodID(cls, "prepareNgageMetadata", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (!method) { env->ExceptionClear(); return; }
    std::string runtime;
    if (!common::get_current_directory(runtime)) return;
    const auto path = env->NewStringUTF(common::ucs2_to_utf8(*raw).c_str());
    const auto backup = env->NewStringUTF((runtime + "/compatibility-backups").c_str());
    env->CallStaticVoidMethod(cls, method, path, backup);
    env->DeleteLocalRef(path);
    env->DeleteLocalRef(backup);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_WARN, "AbyssSymbian", "Metadata compatibility callback failed");
    }
}
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_github_eka2l1_emu_Emulator_getDeviceInfo(JNIEnv *env, jclass) {
    std::vector<std::string> values;
    if (state && state->symsys) {
        const auto *device = state->symsys->get_device_manager()->get_current();
        if (device) values = {device->firmware_code, device->model,
                epocver_to_string(device->ver)};
    }
    auto result = env->NewObjectArray(values.size(), env->FindClass("java/lang/String"), nullptr);
    for (std::size_t i = 0; i < values.size(); ++i) {
        auto value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    return result;
}

struct ngage_library_app {
    std::uint32_t uid;
    std::u16string executable;
    std::string title;
};

static std::vector<ngage_library_app> ngage_library_apps() {
    std::vector<ngage_library_app> result;
    if (!state || !state->symsys) return result;
    auto *sys = state->symsys.get();
    auto *io = sys->get_io_system();
    for (const auto &[key, package] : *sys->get_packages()) {
        const auto game_dir = u"\\private\\20007b39\\game" + eka2l1::common::utf8_to_ucs2(std::to_string(package.uid));
        if (!io->exist(u"C:" + game_dir) && !io->exist(u"E:" + game_dir)) continue;
        std::u16string executable;
        for (const auto &file : package.file_descriptions) {
            const auto path = eka2l1::common::lowercase_ucs2_string(file.target);
            if (!eka2l1::package::is_valid_target_path(file.target) || path.size() < 5
                || (path[0] != u'c' && path[0] != u'e') || eka2l1::path_extension(path) != u".exe") continue;
            auto input = io->open_file(file.target, READ_MODE | BIN_MODE);
            std::uint32_t header[3]{};
            if (!input || input->read_file(0, header, 1, sizeof(header)) != sizeof(header)
                || header[0] != 0x1000007a || header[2] != package.uid) continue;
            if (!executable.empty() && executable != file.target) { executable.clear(); break; }
            executable = file.target;
        }
        if (!executable.empty()) result.push_back({package.uid, executable, eka2l1::common::ucs2_to_utf8(package.package_name)});
    }
    return result;
}

static std::vector<jint> read_ngage_artwork(std::uint32_t uid, const char *suffix) {
    auto *sys = state->symsys.get();
    auto *kernel = sys->get_kernel_system();
    auto *fbs = kernel->get_by_name<eka2l1::fbs_server>(eka2l1::epoc::get_fbs_server_name_by_epocver(kernel->get_epoc_version()));
    if (!fbs) return {};
    std::ostringstream name;
    name << "\\private\\20007b39\\images\\game\\" << std::hex << uid << suffix;
    const auto path = eka2l1::common::utf8_to_ucs2(name.str());
    auto input = sys->get_io_system()->open_file(u"C:" + path, READ_MODE | BIN_MODE);
    if (!input) input = sys->get_io_system()->open_file(u"E:" + path, READ_MODE | BIN_MODE);
    if (!input || input->size() > 8 * 1024 * 1024) return {};
    eka2l1::ro_file_stream stream(input.get());
    eka2l1::loader::mbm_file mbm(&stream);
    mbm.index_to_loads = {0};
    if (!mbm.do_read_headers() || !mbm.is_header_loaded(0)) return {};
    const auto &header = mbm.sbm_headers[0];
    const auto size = header.size_pixels;
    if (size.x <= 0 || size.y <= 0 || size.x > 1024 || size.y > 1024) return {};
    if (header.header_len < 40 || header.bitmap_size < header.header_len || header.bitmap_size > input->size()
        || header.bit_per_pixels > 32 || header.palette_size != 0) return {};
    std::vector<std::uint8_t> pixels(size.x * size.y * 4);
    eka2l1::common::wo_buf_stream output(pixels.data(), pixels.size());
    if (!eka2l1::epoc::convert_to_rgba8888(fbs, mbm, 0, output)) return {};
    std::vector<jint> values(2 + size.x * size.y);
    values[0] = size.x; values[1] = size.y;
    for (std::size_t i = 0; i < pixels.size() / 4; ++i)
        values[i + 2] = (static_cast<std::uint32_t>(pixels[i * 4 + 3]) << 24)
            | (pixels[i * 4] << 16) | (pixels[i * 4 + 1] << 8) | pixels[i * 4 + 2];
    return values;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_github_eka2l1_emu_Emulator_getNgageArtwork(JNIEnv *env, jclass, jlong uid, jboolean cover) {
    bool installed = false;
    for (const auto &game : ngage_library_apps()) installed |= game.uid == static_cast<std::uint32_t>(uid);
    if (!installed) return nullptr;
    std::vector<jint> values;
    if (cover) values = read_ngage_artwork(static_cast<std::uint32_t>(uid), "_full.mbm");
    else {
        // N-Gage's "large" art is a banner. Prefer square resources for grid/list icons.
        for (const char *suffix : {"_small.mbm", "_verysmall.mbm", "_large.mbm"}) {
            auto candidate = read_ngage_artwork(static_cast<std::uint32_t>(uid), suffix);
            if (candidate.empty()) continue;
            const double shape = std::min(candidate[0], candidate[1]) / static_cast<double>(std::max(candidate[0], candidate[1]));
            const double best = values.empty() ? 0 : std::min(values[0], values[1]) / static_cast<double>(std::max(values[0], values[1]));
            if (shape > best || (shape == best && candidate.size() > values.size())) values = std::move(candidate);
        }
    }
    if (values.empty()) return nullptr;
    auto result = env->NewIntArray(static_cast<jsize>(values.size()));
    if (result) env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_eka2l1_emu_Emulator_launchGame(JNIEnv *, jclass, jint uid) {
    if (!state || !state->symsys || !os_thread_obj) return false;
    auto *kernel = state->symsys->get_kernel_system();
    // The running guest may rebuild the application registry during startup.
    const std::lock_guard<eka2l1::kernel_system> guard(*kernel);
    auto *apps = kernel->get_by_name<eka2l1::applist_server>(eka2l1::get_app_list_server_name_by_epocver(kernel->get_epoc_version()));
    auto *reg = apps ? apps->get_registration(static_cast<std::uint32_t>(uid)) : nullptr;
    eka2l1::apa_app_registry package_reg{eka2l1::apa_app_info(), eka2l1::apa_capability()};
    if (apps && !reg) for (const auto &game : ngage_library_apps()) {
        if (game.uid != static_cast<std::uint32_t>(uid)) continue;
        package_reg.mandatory_info.uid = game.uid;
        package_reg.mandatory_info.app_path = game.executable;
        reg = &package_reg;
        break;
    }
    if (!reg) return false;
    eka2l1::epoc::apa::command_line command;
    command.launch_cmd_ = eka2l1::epoc::apa::command_create;
    const bool started = apps->launch_app(*reg, command, nullptr, [](eka2l1::kernel::process *process) {
        const auto reason = eka2l1::common::ucs2_to_utf8(process->get_exit_category()) + " (" + std::to_string(process->get_exit_reason()) + ")";
        __android_log_print(ANDROID_LOG_INFO, "AbyssSymbian", "Guest exit: %s", reason.c_str());
        if (eka2l1::log::spd_logger) eka2l1::log::spd_logger->flush();
        auto *env = eka2l1::common::jni::environment();
        auto cls = eka2l1::common::jni::find_class("com/github/eka2l1/emu/Emulator");
        auto method = env->GetStaticMethodID(cls, "guestExited", "(Ljava/lang/String;)V");
        auto text = env->NewStringUTF(reason.c_str());
        env->CallStaticVoidMethod(cls, method, text);
        env->DeleteLocalRef(text);
    });
    return started;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_github_eka2l1_emu_Emulator_getCatalogApps(JNIEnv *env, jclass) {
    std::vector<std::string> info;
    if (state && state->launcher && state->symsys) {
        info = state->launcher->get_apps();
        // Card editions can deliberately hide their runnable alternate build from Avkon.
        // Only expose the positively identified card, not arbitrary hidden system apps.
        auto *sys = state->symsys.get();
        eka2l1::apa_app_registry card;
        const bool mounted = sys->get_ngage_game_info_mounted(card);
        auto *kernel = sys->get_kernel_system();
        auto *apps = kernel->get_by_name<eka2l1::applist_server>(eka2l1::get_app_list_server_name_by_epocver(kernel->get_epoc_version()));
        if (apps) for (auto &reg : apps->get_registerations()) {
            const bool identified = (mounted && card.mandatory_info.uid == reg.mandatory_info.uid)
                || abyssme::is_card_executable(reg.mandatory_info.app_path.to_std_string(nullptr),
                    [&](const auto &path) { return sys->get_io_system()->exist(path); });
            if (!identified) continue;
            const auto uid = std::to_string(reg.mandatory_info.uid);
            bool present = false;
            for (std::size_t i = 0; i + 1 < info.size(); i += 2) present |= info[i] == uid;
            if (!present) {
                info.push_back(uid);
                auto name = reg.mandatory_info.long_caption.to_std_string(nullptr);
                info.push_back(name.empty() ? "N-Gage game" : eka2l1::common::ucs2_to_utf8(name));
            }
        }
    }
    auto result = env->NewObjectArray(static_cast<jsize>(info.size()), env->FindClass("java/lang/String"), nullptr);
    for (std::size_t i = 0; i < info.size(); ++i) {
        auto value = env->NewStringUTF(info[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

// Return only writable-drive applications, identified cards and installed N-Gage 2 games.
// ROM utilities and arbitrary hidden services are not launcher entries.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_github_eka2l1_emu_Emulator_getLibraryApps(JNIEnv *env, jclass) {
    std::vector<std::string> info;
    if (state && state->symsys) {
        auto *sys = state->symsys.get();
        auto *kernel = sys->get_kernel_system();
        auto *apps = kernel->get_by_name<eka2l1::applist_server>(eka2l1::get_app_list_server_name_by_epocver(kernel->get_epoc_version()));
        eka2l1::apa_app_registry card;
        const bool has_card = sys->get_ngage_game_info_mounted(card);
        if (apps) for (auto &reg : apps->get_registerations()) {
            const auto uid = reg.mandatory_info.uid;
            if (uid == 536902457) continue; // Platform installer is available through system management.
            const bool is_card = (has_card && card.mandatory_info.uid == uid)
                || abyssme::is_card_executable(reg.mandatory_info.app_path.to_std_string(nullptr),
                    [&](const auto &path) { return sys->get_io_system()->exist(path); });
            const auto game_dir = u"\\private\\20007b39\\game" + eka2l1::common::utf8_to_ucs2(std::to_string(uid));
            const bool ngage2 = sys->get_io_system()->exist(u"C:" + game_dir)
                || sys->get_io_system()->exist(u"E:" + game_dir);
            const auto path = reg.rsc_path;
            const bool writable = path.size() > 1 && path[1] == u':'
                && (path[0] == u'C' || path[0] == u'c' || path[0] == u'E' || path[0] == u'e');
            if (!is_card && !ngage2 && (!writable || reg.caps.is_hidden)) continue;
            auto title = reg.mandatory_info.long_caption.to_std_string(nullptr);
            if (title.empty()) title = reg.mandatory_info.short_caption.to_std_string(nullptr);
            info.push_back(std::to_string(uid));
            info.push_back(title.empty() ? "Symbian app" : eka2l1::common::ucs2_to_utf8(title));
            info.push_back(is_card ? "N-Gage" : ngage2 ? "N-Gage 2.0" : "Symbian");
        }
        for (const auto &game : ngage_library_apps()) {
            const auto uid = std::to_string(game.uid);
            bool present = false;
            for (std::size_t i = 0; i + 2 < info.size(); i += 3) present |= info[i] == uid;
            if (!present) { info.push_back(uid); info.push_back(game.title); info.push_back("N-Gage 2.0"); }
        }
    }
    auto result = env->NewObjectArray(static_cast<jsize>(info.size()), env->FindClass("java/lang/String"), nullptr);
    for (std::size_t i = 0; i < info.size(); ++i) {
        auto value = env->NewStringUTF(info[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_eka2l1_emu_Emulator_environmentReport(JNIEnv *env, jclass) {
    std::ostringstream report;
    if (!state || !state->symsys) return env->NewStringUTF("Not prepared");
    auto *sys = state->symsys.get();
    report << "Language: " << state->conf.language << "\n";
    auto *io = sys->get_io_system();
    for (auto drv : {drive_c, drive_e, drive_z}) {
        if (auto drive = io->get_drive_entry(drv)) report << static_cast<int>(drv) << ": " << drive->real_path << "\n";
    }
    auto *kernel = sys->get_kernel_system();
    if (kernel) {
        auto *apps = kernel->get_by_name<eka2l1::applist_server>(eka2l1::get_app_list_server_name_by_epocver(kernel->get_epoc_version()));
        if (apps) for (auto &reg : apps->get_registerations()) {
            report << reg.mandatory_info.uid << " hidden=" << reg.caps.is_hidden << " "
                   << eka2l1::common::ucs2_to_utf8(reg.mandatory_info.long_caption.to_std_string(nullptr)) << " "
                   << eka2l1::common::ucs2_to_utf8(reg.rsc_path) << "\n";
        }
    }
    return env->NewStringUTF(report.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_eka2l1_emu_Emulator_driveE(JNIEnv *env, jclass) {
    if (!state || !state->symsys || !state->symsys->get_io_system()) return nullptr;
    auto path = state->symsys->get_io_system()->get_raw_path(u"E:\\");
    if (!path) return nullptr;
    // Java File resolves relative paths against user.dir, not native chdir().
    std::string directory;
    eka2l1::common::get_current_directory(directory);
    auto absolute = eka2l1::absolute_path(eka2l1::common::ucs2_to_utf8(*path), directory);
    return env->NewStringUTF(absolute.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_prepare(JNIEnv *env, jclass, jstring directory) {
    if (os_thread_obj || gr_thread_obj) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Stop the active Symbian session first");
        return;
    }
    if (!callbacks_ready) {
        eka2l1::common::jni::init_classloader();
        eka2l1::common::android::register_storage_callbacks(env);
        eka2l1::drivers::android::register_camera_callbacks(env);
        if (env->ExceptionCheck()) return;
        callbacks_ready = true;
    }
    if (state) {
        state->launcher.reset();
        state->symsys.reset();
        state.reset();
    }
    const char *path = env->GetStringUTFChars(directory, nullptr);
    eka2l1::common::set_current_directory(path);
    env->ReleaseStringUTFChars(directory, path);
    state = std::make_unique<eka2l1::android::emulator>();
    state->stage_one();
    // The distributed host uses the separately attributed GeneralUser GS bank.
    state->conf.midi_backend = eka2l1::config::MIDI_BACKEND_TSF;
    state->conf.sf2_bank_path = "resources/generaluser-gs.sf2";
    // Registry icons initialize FBS/font services. Z: must already be mounted,
    // otherwise their first initialization permanently sees an empty font store.
    if (state->symsys->get_device_manager()->total() > 0) {
        state->stage_two();
        // The prototype uses English when the supplied phone supports it.
        // Some regional defaults request app resources absent from international packages.
        const auto *device = state->symsys->get_device_manager()->get_current();
        if (device) {
            using namespace eka2l1::epoc;
            for (auto language : {lang_english, lang_american, lang_australian,
                    lang_english_apac, lang_english_taiwan, lang_english_hongKong,
                    lang_english_prc, lang_english_japan, lang_english_thailand,
                    lang_english_india}) {
                if (std::find(device->languages.begin(), device->languages.end(), language) != device->languages.end()) {
                    state->launcher->set_language(language);
                    break;
                }
            }
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_eka2l1_emu_Emulator_startSession(JNIEnv *, jclass) {
    if (!state || os_thread_obj || gr_thread_obj || !state->stage_two()) return false;
    state->graphics_init_done.reset();
    gr_thread_obj = std::make_unique<std::thread>(eka2l1::android::graphics_driver_thread, std::ref(*state));
    state->graphics_init_done.wait();
    // Retain the system until the host has woken and joined the OS thread.
    // The upstream loop destroys it before the host can safely wake an idle CPU.
    os_thread_obj = std::make_unique<std::thread>([] {
        while (!state->should_emu_quit) {
            state->symsys->loop();
            if (state->should_emu_pause && !state->should_emu_quit) {
                state->symsys->pause();
                state->pause_sema.wait();
                state->symsys->unpause();
            }
        }
    });
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_pauseSession(JNIEnv *, jclass, jboolean paused) {
    if (!state || !os_thread_obj) return;
    if (paused) eka2l1::android::pause_threads(*state);
    else eka2l1::android::start_threads(*state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_stopSession(JNIEnv *, jclass) {
    if (!state) return;
    if (os_thread_obj) {
        state->symsys->request_exit();
        state->should_emu_quit = true;
        eka2l1::android::start_threads(*state);
        state->symsys->get_kernel_system()->stop_cores_idling();
        os_thread_obj->join();
        os_thread_obj.reset();
    }
    // Guest teardown can submit final graphics work; keep the graphics worker alive.
    state->symsys.reset();
    if (gr_thread_obj) {
        state->graphics_sema.notify();
        state->graphics_driver->abort();
        gr_thread_obj->join();
        gr_thread_obj.reset();
    }
    state->launcher.reset();
    state->symsys.reset();
    state.reset();
    inited = false;
}
