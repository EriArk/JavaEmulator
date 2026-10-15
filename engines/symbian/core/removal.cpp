// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <android/state.h>
#include <android/thread.h>
#include <android/launcher.h>
#include <common/fileutils.h>
#include <kernel/kernel.h>
#include <services/applist/applist.h>
#include <package/manager.h>
#include <loader/sis_fields.h>
#include <common/cvt.h>
#include <utils/system.h>
#include <vfs/vfs.h>
#include <filesystem>
#include <set>
#include <stdexcept>
#include <thread>
#include "card_identity.h"

extern std::unique_ptr<eka2l1::android::emulator> state;
extern std::unique_ptr<std::thread> os_thread_obj, gr_thread_obj;
namespace {
using namespace eka2l1;
namespace fs = std::filesystem;
std::u16string fold(std::u16string path) {
    for (auto &c : path) { if (c == u'/') c = u'\\'; c = std::towlower(c); }
    return path;
}
bool owns(const package::object &pkg, apa_app_registry &app) {
    const auto resource = fold(app.rsc_path), executable = fold(app.mandatory_info.app_path.to_std_string(nullptr));
    for (const auto &file : pkg.file_descriptions)
        if (fold(file.target) == resource || fold(file.target) == executable) return true;
    return false;
}
struct plan {
    package::object *owner = nullptr;
    std::vector<std::string> rows;
    std::set<std::u16string> files;
};
void require(bool condition, const char *message) { if (!condition) throw std::runtime_error(message); }
plan inspect(JNIEnv *env, jlong uid, const std::string &runtime) {
    require(state && state->symsys && !os_thread_obj && !gr_thread_obj, "Close the game before uninstalling");
    require(uid > 0 && uid <= 0xffffffffLL, "Invalid application identity");
    const auto root = fs::canonical(runtime);
    require(fs::canonical(fs::current_path()) == root, "The selected phone has changed. Open the game actions again");
    auto *sys = state->symsys.get(); auto *io = sys->get_io_system();
    auto *kernel = sys->get_kernel_system();
    auto *apps = kernel->get_by_name<applist_server>(get_app_list_server_name_by_epocver(kernel->get_epoc_version()));
    auto *app = apps ? apps->get_registration(static_cast<std::uint32_t>(uid)) : nullptr;
    require(app, "This game is no longer registered on this phone");
    require(uid != 0x20007b39 && !app->caps.is_hidden, "System and platform applications cannot be removed here");
    apa_app_registry card;
    const bool card_game = (sys->get_ngage_game_info_mounted(card) && card.mandatory_info.uid == uid)
        || abyssme::is_card_executable(app->mandatory_info.app_path.to_std_string(nullptr),
            [&](const auto &path) { return io->exist(path); });
    const auto game_dir = u"\\private\\20007b39\\game" + common::utf8_to_ucs2(std::to_string(uid));
    require(!io->exist(u"C:" + game_dir) && !io->exist(u"E:" + game_dir),
        "N-Gage 2.0 games must be managed by their platform. Nothing was removed");
    auto *packages = sys->get_packages(); require(packages, "Package registry is unavailable");
    plan result;
    for (auto &[key, pkg] : *packages) if (owns(pkg, *app)) {
        require(!result.owner, "More than one package owns this game. Removal is not supported safely");
        result.owner = &pkg;
    }
    require(result.owner, card_game ? "Card-installed N-Gage games without a SIS owner are not supported by this uninstaller. Nothing was removed"
        : "No owning SIS package was found. Card and standalone installations cannot be removed here");
    auto &pkg = *result.owner;
    require(!pkg.in_rom && pkg.is_removable && pkg.install_type == package::install_type_normal_install,
        "Built-in, protected and patch packages cannot be removed here");
    require(pkg.embedded_packages.empty(), "This package contains other packages. Removal is not supported safely");
    require(!pkg.file_descriptions.empty(), "The package has no reliable file inventory");
    std::set<epoc::uid> sids;
    std::set<fs::path> host_files;
    auto contained = [&](const std::u16string &guest) {
        const auto raw = io->get_raw_path(guest);
        require(raw.has_value(), "A package drive is unavailable");
        const auto resolved = fs::weakly_canonical(common::ucs2_to_utf8(*raw));
        const auto drives = fs::canonical(root / "data" / "drives");
        const auto relative = resolved.lexically_relative(drives);
        require(!relative.empty() && *relative.begin() != ".." && !relative.is_absolute(),
            "A package path points outside this phone. Removal was blocked");
        return resolved;
    };
    for (const auto &file : pkg.file_descriptions) {
        // Match the core's removal operations; SIS text prompts do not name files.
        if(file.operation!=static_cast<int>(loader::ss_op::install)
                && file.operation!=static_cast<int>(loader::ss_op::undefined)
                && file.operation!=static_cast<int>(loader::ss_op::null)) continue;
        const auto path = fold(file.target);
        require(package::is_valid_target_path(path) && path.size() > 3 && (path[0] == u'c' || path[0] == u'e')
                && path.find_first_of(u"*?") == std::u16string::npos && path.back() != u'\\',
            "The package contains unsupported removal paths");
        require(path.find(u"\\sys\\install\\") == std::u16string::npos
                && path.find(u"\\system\\install\\") == std::u16string::npos
                && path.find(u"\\system\\libs\\") == std::u16string::npos
                && !(path.find(u"\\sys\\bin\\") != std::u16string::npos && path.size() >= 4 && path.substr(path.size()-4) == u".dll"),
            "The package includes shared system components. Removal was blocked");
        auto rom = path; rom[0] = u'z';
        require(!io->exist(rom), "The package replaces a built-in file. Removal was blocked");
        auto resolved=contained(path);
        require(!fs::is_directory(resolved), "The package claims a whole directory. Removal was blocked");
        host_files.insert(resolved); result.files.insert(path);
        if (file.sid) sids.insert(file.sid);
    }
    std::vector<std::u16string> private_dirs;
    std::vector<fs::path> private_hosts;
    for (auto sid : sids) {
        char hex[9]; snprintf(hex, sizeof(hex), "%08x", sid);
        const auto suffix = u":\\private\\" + common::utf8_to_ucs2(hex) + u"\\";
        for (drive_number drive = drive_a; drive < drive_z; drive++) {
            auto entry = io->get_drive_entry(drive);
            if (!entry || (entry->attribute & io_attrib_write_protected)) continue;
            const auto path = fold(std::u16string(1, drive_to_char16(drive)) + suffix);
            private_hosts.push_back(contained(path)); private_dirs.push_back(path);
        }
    }
    for (auto &[key, other] : *packages) {
        if (&other == &pkg) continue;
        require(other.uid != pkg.uid, "This package has upgrades or add-ons. Removal is not supported safely");
        for (const auto &dep : other.dependencies) require(dep.uid != pkg.uid, "Another installed package needs this game package");
        // The pinned installer omits prerequisites from .reg objects. Read the
        // retained SIS controllers too, including packages installed before this UI.
        if(!other.in_rom) {
            require(!other.controller_infos.empty(), "Another legacy package has no dependency inventory. Safe removal cannot be verified on this phone");
            for(const auto &info:other.controller_infos) {
                char file[128]; snprintf(file,sizeof(file),"C:\\sys\\install\\sisregistry\\%08x\\%08x_%04x.ctl",other.uid,other.index,info.offset);
                auto raw=io->get_raw_path(common::utf8_to_ucs2(file));
                require(raw.has_value(), "An installed package's dependency inventory is unavailable");
                auto cls=env->FindClass("io/github/eriark/abyssme/symbian/PackageDependencies");
                if(!cls) { env->ExceptionClear(); throw std::runtime_error("Dependency reader is unavailable"); }
                auto method=env->GetStaticMethodID(cls,"needs","(Ljava/lang/String;JJ)Z");
                if(!method) { env->ExceptionClear(); env->DeleteLocalRef(cls); throw std::runtime_error("Dependency reader is unavailable"); }
                auto path=env->NewStringUTF(common::ucs2_to_utf8(*raw).c_str());
                if(!path) { env->ExceptionClear(); env->DeleteLocalRef(cls); throw std::runtime_error("Cannot read dependency path"); }
                bool needed=env->CallStaticBooleanMethod(cls,method,path,static_cast<jlong>(other.uid),static_cast<jlong>(pkg.uid));
                env->DeleteLocalRef(path); env->DeleteLocalRef(cls);
                if(env->ExceptionCheck()) {
                    env->ExceptionDescribe(); env->ExceptionClear();
                    throw std::runtime_error("Cannot verify dependencies of " + common::ucs2_to_utf8(other.package_name) + ". Nothing was removed");
                }
                require(!needed, "Another installed package needs this game package");
            }
        }
        for (const auto &embedded : other.embedded_packages) require(embedded.uid != pkg.uid, "Another package owns this embedded component");
        for (const auto &file : other.file_descriptions) {
            const auto path = fold(file.target);
            require(!result.files.count(path) && !(file.sid && sids.count(file.sid)), "Another package shares these files or save-data ownership");
            if(!path.empty()) {
                auto raw=io->get_raw_path(path);
                if(raw) {
                    const auto host=fs::weakly_canonical(common::ucs2_to_utf8(*raw));
                    require(!host_files.count(host), "Another package shares the same physical file");
                    for(const auto &dir:private_hosts) {
                        const auto relative=host.lexically_relative(dir);
                        require(relative.empty() || relative.is_absolute() || *relative.begin()=="..",
                            "Another package uses this application's private data");
                    }
                }
            }
            for (const auto &dir : private_dirs) require(path.compare(0, dir.size(), dir) != 0, "Another package uses this application's private data");
        }
    }
    result.rows = {std::to_string(pkg.uid), std::to_string(pkg.index), common::ucs2_to_utf8(pkg.package_name),
        std::to_string(pkg.version.major) + "." + std::to_string(pkg.version.minor) + "." + std::to_string(pkg.version.build), "0"};
    std::set<epoc::uid> app_ids;
    for (auto &reg : apps->get_registerations()) if (owns(pkg, reg)) {
        require(!reg.caps.is_hidden && reg.mandatory_info.uid != 0x20007b39, "This package also owns a system application");
        result.rows.push_back(std::to_string(reg.mandatory_info.uid));
        result.rows.push_back(common::ucs2_to_utf8(reg.mandatory_info.long_caption.to_std_string(nullptr)));
        app_ids.insert(reg.mandatory_info.uid);
    }
    require(!app_ids.empty(), "No applications were resolved for this package");
    for(auto sid:sids) require(app_ids.count(sid), "This package also owns private data for a non-game component");
    result.rows[4] = std::to_string(app_ids.size());
    for(const auto &path:result.files) result.rows.push_back(common::ucs2_to_utf8(path));
    for(const auto &path:private_dirs) result.rows.push_back(common::ucs2_to_utf8(path));
    return result;
}
std::string text(JNIEnv *env, jstring value) {
    require(value, "Missing phone path"); const char *raw = env->GetStringUTFChars(value, nullptr);
    require(raw, "Cannot read phone path"); std::string result(raw); env->ReleaseStringUTFChars(value, raw); return result;
}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_github_eka2l1_emu_Emulator_removalComplete(JNIEnv *env, jclass, jobjectArray rows, jstring runtime) {
    try {
        require(state && state->symsys && !os_thread_obj && !gr_thread_obj, "Close the game before checking removal");
        require(fs::canonical(text(env,runtime)) == fs::canonical(fs::current_path()), "The selected phone has changed");
        const int size=env->GetArrayLength(rows); require(size>=8 && size<=20000, "Invalid uninstall record");
        auto value=[&](int i) { auto item=static_cast<jstring>(env->GetObjectArrayElement(rows,i)); auto s=text(env,item); env->DeleteLocalRef(item); return s; };
        const auto owner=std::stoul(value(0)); const auto index=std::stoi(value(1)); const auto count=std::stoi(value(4));
        require(count>0 && count<=2048 && 5+count*2<size, "Invalid uninstall inventory");
        if(state->symsys->get_packages()->package(owner,index)) return false;
        for(int i=5+count*2;i<size;i++) if(state->symsys->get_io_system()->exist(common::utf8_to_ucs2(value(i)))) return false;
        return true;
    } catch(const std::exception &error) { env->ThrowNew(env->FindClass("java/io/IOException"),error.what()); return false; }
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_github_eka2l1_emu_Emulator_inspectRemoval(JNIEnv *env, jclass, jlong uid, jstring runtime) {
    try {
        auto result = inspect(env, uid, text(env, runtime));
        auto rows = env->NewObjectArray(result.rows.size(), env->FindClass("java/lang/String"), nullptr);
        for (std::size_t i=0; i<result.rows.size(); i++) {
            auto value = env->NewStringUTF(result.rows[i].c_str()); env->SetObjectArrayElement(rows, i, value); env->DeleteLocalRef(value);
        }
        return rows;
    } catch (const std::exception &error) { env->ThrowNew(env->FindClass("java/io/IOException"), error.what()); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_github_eka2l1_emu_Emulator_uninstallGame(JNIEnv *env, jclass, jlong uid, jlong owner, jint index, jstring runtime) {
    try {
        auto result = inspect(env, uid, text(env, runtime));
        require(result.owner->uid == owner && result.owner->index == index, "Package ownership changed. Review removal again");
        auto *sys = state->symsys.get();
        require(sys->get_packages()->uninstall_package(*result.owner), "Package removal did not complete. Restore a phone backup before retrying");
        for (const auto &path : result.files) require(!sys->get_io_system()->exist(path), "Some package files could not be removed. The library entry has been kept");
        require(!sys->get_packages()->package(owner, index), "The package is still registered");
    } catch (const std::exception &error) { env->ThrowNew(env->FindClass("java/io/IOException"), error.what()); }
}
