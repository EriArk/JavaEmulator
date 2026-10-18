# Generate narrowly patched translation units without modifying the pinned checkout.
function(abyss_replace_once variable before after)
    set(original "${${variable}}")
    string(FIND "${original}" "${before}" found)
    if(found LESS 0)
        message(FATAL_ERROR "Pinned source no longer matches patch: ${before}")
    endif()
    string(LENGTH "${before}" before_length)
    math(EXPR remaining_start "${found} + ${before_length}")
    string(SUBSTRING "${original}" ${remaining_start} -1 remaining)
    string(FIND "${remaining}" "${before}" repeated)
    if(NOT repeated LESS 0)
        message(FATAL_ERROR "Ambiguous source patch: ${before}")
    endif()
    string(REPLACE "${before}" "${after}" patched "${original}")
    set(${variable} "${patched}" PARENT_SCOPE)
endfunction()

function(abyss_patch_source target relative before after)
    get_target_property(source_dir ${target} SOURCE_DIR)
    file(READ "${source_dir}/${relative}" patched)
    abyss_replace_once(patched "${before}" "${after}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${source_dir}/${relative}")
    string(REPLACE "/" "_" output_name "${relative}")
    set(output "${CMAKE_CURRENT_BINARY_DIR}/patched/${target}_${output_name}")
    file(CONFIGURE OUTPUT "${output}" CONTENT "${patched}" @ONLY)
    get_target_property(sources ${target} SOURCES)
    list(FIND sources "${relative}" index)
    if(index LESS 0)
        message(FATAL_ERROR "Missing source in ${target}: ${relative}")
    endif()
    list(REMOVE_AT sources ${index})
    list(APPEND sources "${output}")
    set_property(TARGET ${target} PROPERTY SOURCES "${sources}")
endfunction()

# A resize acquires another native-window reference, even for the same Java Surface.
abyss_patch_source(native-lib "src/native-lib.cpp" [=[    s_surf = ANativeWindow_fromSurface(env, surface);
    state->window->surface_changed(s_surf, width, height);]=] [=[    auto *previous_surface = s_surf;
    s_surf = ANativeWindow_fromSurface(env, surface);
    state->window->surface_changed(s_surf, width, height);
    if (previous_surface) ANativeWindow_release(previous_surface);]=])

# Correct supported legacy language tags only in installed N-Gage achievement metadata.
# Bound native archive writes by their preflight size, including malformed streams.
get_target_property(archive_source_dir common SOURCE_DIR)
file(READ "${archive_source_dir}/src/archive.cpp" archive_original)
set(archive_patched "${archive_original}")
abyss_replace_once(archive_patched "static bool write_entry_to_file(struct archive *reader, const std::string &dest_path) {"
    "static bool write_entry_to_file(struct archive *reader, const std::string &dest_path, const std::uint64_t expected_size) {\n        std::uint64_t written = 0;")
abyss_replace_once(archive_patched [=[            const la_ssize_t read = archive_read_data(reader, buffer.data(), buffer.size());

            if (read == 0) {
                return true;
            }]=] [=[            const la_ssize_t read = archive_read_data(reader, buffer.data(), buffer.size());

            if (read == 0) {
                return written == expected_size;
            }
            if (read > 0) {
                if (static_cast<std::uint64_t>(read) > expected_size - written) return false;
                written += static_cast<std::uint64_t>(read);
            }]=])
abyss_replace_once(archive_patched "write_entry_to_file(reader.get(), dest)" "write_entry_to_file(reader.get(), dest, info.size)")
abyss_patch_source(common "src/archive.cpp" "${archive_original}" "${archive_patched}")
unset(archive_original)
unset(archive_patched)

# Prepared archives can contain incomplete metadata. Never dereference missing INI nodes.
get_target_property(software_source_dir epoc SOURCE_DIR)
file(READ "${software_source_dir}/src/software.cpp" software_original)
set(software_patched "${software_original}")
abyss_replace_once(software_patched "namespace eka2l1::loader {" [=[namespace eka2l1::loader {
    static common::ini_value *abyss_ini_value(common::ini_file &file, const char *name) {
        auto node = file.find(name);
        if (!node || node->get_node_type() != common::INI_NODE_PAIR) return nullptr;
        auto pair = node->get_as<common::ini_pair>();
        if (pair->get_value_count() == 0 || (*pair)[0]->get_node_type() != common::INI_NODE_VALUE) return nullptr;
        return (*pair)[0]->get_as<common::ini_value>();
    }
]=])
abyss_replace_once(software_patched [=[        auto major_node = platform_ini.find("SymbianOSMajorVersion");
        auto minor_node = platform_ini.find("SymbianOSMinorVersion");]=] [=[        auto major_node = abyss_ini_value(platform_ini, "SymbianOSMajorVersion");
        auto minor_node = abyss_ini_value(platform_ini, "SymbianOSMinorVersion");]=])
abyss_replace_once(software_patched "major_node->get_as<common::ini_pair>()->get<common::ini_value>(0)->get_as_native<std::uint8_t>()" "major_node->get_as_native<std::uint8_t>()")
abyss_replace_once(software_patched "minor_node->get_as<common::ini_pair>()->get<common::ini_value>(0)->get_as_native<std::uint8_t>()" "minor_node->get_as_native<std::uint8_t>()")
abyss_replace_once(software_patched [=[                case 3: {
                    // Note: This should be detected through SIS!]=] [=[                case 2: {
                    ver = epocver::epoc93fp1;
                    break;
                }
                case 3: {
                    // Note: This should be detected through SIS!]=])
abyss_replace_once(software_patched [=[                    ver = epocver::epoc94;
                    break;
                }
                }
]=] [=[                    ver = epocver::epoc94;
                    break;
                }
                default: return false;
                }
]=])
abyss_replace_once(software_patched [=[                LOG_WARN(SYSTEM, "Unsupport Symbian version {}, will default to 9.4", major, minor);
                break;]=] [=[                LOG_WARN(SYSTEM, "Unsupported Symbian version {}.{}", major, minor);
                return false;]=])
abyss_replace_once(software_patched [=[            LOG_ERROR(SYSTEM, "SymbianOSMajorVersion and SymbianOSMinorVersion. Report to developers for investigation.");]=]
    [=[            LOG_ERROR(SYSTEM, "SymbianOSMajorVersion and SymbianOSMinorVersion. Report to developers for investigation.");
            return false;]=])
abyss_replace_once(software_patched "    epocver determine_rpkg_symbian_version(const std::string &extracted_path) {" [=[    bool abyss_detect_prepared_version(const std::string &path, epocver &version) {
        if (common::exists(add_path(path, "system/install/uiq21platform.sis"))) {
            version = epocver::epoc70; return true;
        }
        if (common::exists(add_path(path, "system/install/series90v10.sis"))
                || common::exists(add_path(path, "system/install/series90v11.sis"))) {
            version = epocver::epoc7; return true;
        }
        if (determine_rpkg_symbian_version_through_platform_file(path, version)) {
            if (version == epocver::epoc93fp2) {
                epocver from_sis;
                if (determine_rpkg_symbian_version_through_series60_sis(path, from_sis))
                    version = std::min(version, from_sis);
            }
            return true;
        }
        return determine_rpkg_symbian_version_through_series60_sis(path, version);
    }
    epocver determine_rpkg_symbian_version(const std::string &extracted_path) {]=])
abyss_replace_once(software_patched [=[        manufacturer = product_ini.find("Manufacturer")->get_as<common::ini_pair>()->get<common::ini_value>(0)->get_value();
        firmcode = product_ini.find("Product")->get_as<common::ini_pair>()->get<common::ini_value>(0)->get_value();
        model = product_ini.find("Model")->get_as<common::ini_pair>()->get<common::ini_value>(0)->get_value();]=] [=[        auto maker = abyss_ini_value(product_ini, "Manufacturer");
        auto product = abyss_ini_value(product_ini, "Product");
        auto name = abyss_ini_value(product_ini, "Model");
        if (!maker || !product || !name) return false;
        manufacturer = maker->get_value();
        firmcode = product->get_value();
        model = name->get_value();]=])
abyss_replace_once(software_patched "        firmcode = sw_infos[2].strip_reserverd().std_str();"
    "        if (sw_infos.size() < 3) return false;\n        firmcode = sw_infos[2].strip_reserverd().std_str();")
abyss_patch_source(epoc "src/software.cpp" "${software_original}" "${software_patched}")
unset(software_original)
unset(software_patched)

get_target_property(fs_source_dir epocservs SOURCE_DIR)
file(READ "${fs_source_dir}/src/fs/files.cpp" fs_original)
set(fs_patched "${fs_original}")
abyss_replace_once(fs_patched "#include <services/fs/fs.h>"
    "#include <services/fs/fs.h>\n#include \"file_transfer.h\"")
abyss_replace_once(fs_patched
    "new_node->vfs_node = io->open_file(name, access_mode);"
    "extern void abyss_prepare_ngage_metadata(io_system *, const std::u16string &);\n        if (!(access_mode & WRITE_MODE) && !temporary) abyss_prepare_ngage_metadata(io, name);\n        new_node->vfs_node = io->open_file(name, access_mode);")
abyss_replace_once(fs_patched [=[        std::int32_t write_pos_provided = *ctx->get_argument_value<std::int32_t>(2);]=]
    [=[        const bool wide_position = ctx->msg->function & epoc::fs::ipc_arg_slot2_des;
        const auto position = wide_position ? ctx->get_argument_data_from_descriptor<std::int64_t>(2)
            : std::optional<std::int64_t>(ctx->get_argument_value<std::int32_t>(2));
        if (!position) { ctx->complete(epoc::error_argument); return; }
        const std::int64_t write_pos_provided = *position;]=])
abyss_replace_once(fs_patched [=[        // Low MaxUint64
        if ((write_pos_provided != static_cast<int>(0x80000000)) && (write_pos_provided != -1)) {
            write_pos = write_pos_provided;
        }

        if (write_pos > size_of_file) {
            // Fill the file with temporary 0
            vfs_file->seek(0, file_seek_mode::end);
            static char ZERO_BYTE = 0;

            if (vfs_file->write_file(&ZERO_BYTE, 1, static_cast<std::uint32_t>(write_pos - size_of_file)) != write_pos - size_of_file) {
                LOG_WARN(SERVICE_EFSRV, "Unable to supply stubbed bytes for beyond file size write operation!");
            }
        }

        // If this write pos is beyond the current end of file, use last pos
        vfs_file->seek(write_pos, file_seek_mode::beg);
        size_t wrote_size = vfs_file->write_file(write_data.value().data(), 1, write_len);]=]
    [=[        const auto transfer = abyssme::plan_file_transfer(true, size_of_file, write_pos,
            write_pos_provided, write_len, write_data->size(), !wide_position);
        if (!transfer) { ctx->complete(epoc::error_argument); return; }
        write_pos = transfer->position;
        if (vfs_file->seek(write_pos, file_seek_mode::beg) == UINT64_MAX) {
            ctx->complete(epoc::error_argument); return;
        }
        const size_t wrote_size = vfs_file->write_file(write_data->data(), 1, transfer->length);
        if (wrote_size != transfer->length) { ctx->complete(epoc::error_general); return; }]=])
abyss_replace_once(fs_patched [=[        vfs_file->seek(read_pos, file_seek_mode::beg);

        uint64_t size = vfs_file->size();

        if (size - read_pos < read_len) {
            read_len = static_cast<int>(size - read_pos);
        }]=]
    [=[        const auto transfer = abyssme::plan_file_transfer(false, vfs_file->size(), last_pos,
            static_cast<std::int64_t>(read_pos), read_len, ctx->get_argument_max_data_size(0), false);
        if (!transfer) { ctx->complete(epoc::error_argument); return; }
        read_len = transfer->length;
        if (vfs_file->seek(transfer->position, file_seek_mode::beg) == UINT64_MAX) {
            ctx->complete(epoc::error_argument); return;
        }]=])
abyss_replace_once(fs_patched [=[        ctx->write_data_to_descriptor_argument(0, reinterpret_cast<uint8_t *>(read_data.data()), static_cast<std::uint32_t>(read_finish_len));]=]
    [=[        int descriptor_error = epoc::error_bad_descriptor;
        if (!ctx->write_data_to_descriptor_argument(0, reinterpret_cast<uint8_t *>(read_data.data()),
                static_cast<std::uint32_t>(read_finish_len), &descriptor_error)) {
            ctx->complete(descriptor_error); return;
        }]=])
abyss_replace_once(fs_patched "position = ctx->get_argument_value<std::uint32_t>(1).value();"
    "position = ctx->get_argument_value<std::int32_t>(1).value();")
abyss_replace_once(fs_patched "position = ctx->get_argument_value<std::uint32_t>(2).value();"
    "position = ctx->get_argument_value<std::int32_t>(2).value();")
abyss_replace_once(fs_patched [=[        std::uint8_t *buffer = ctx->get_descriptor_argument_ptr(slot_to_set_length);]=]
    [=[        if (position > INT64_MAX || buffer_length > INT32_MAX) {
            target_file->close(); ctx->complete(epoc::error_argument); return;
        }
        const auto transfer = abyssme::plan_file_transfer(false, target_file->size(), 0,
            static_cast<std::int64_t>(position), static_cast<std::int32_t>(buffer_length),
            ctx->get_argument_max_data_size(slot_to_set_length), false);
        if (!transfer) {
            target_file->close(); ctx->complete(epoc::error_argument); return;
        }
        std::uint8_t *buffer = ctx->get_descriptor_argument_ptr(slot_to_set_length);]=])
abyss_replace_once(fs_patched [=[        target_file->seek(position, eka2l1::file_seek_mode::beg);
        const std::size_t readed_size = target_file->read_file(buffer, buffer_length, 1);]=]
    [=[        if (target_file->seek(transfer->position, eka2l1::file_seek_mode::beg) == UINT64_MAX) {
            target_file->close(); ctx->complete(epoc::error_argument); return;
        }
        const std::size_t readed_size = transfer->length
            ? target_file->read_file(buffer, 1, transfer->length) : 0;]=])
abyss_patch_source(epocservs "src/fs/files.cpp" "${fs_original}" "${fs_patched}")
unset(fs_original)
unset(fs_patched)

# HAL/SCDV expose the current logical mode. Do not rotate its presented pixels
# again based on the physical panel. Keep upstream read-back handling separate.
get_target_property(screen_source_dir epocservs SOURCE_DIR)
file(READ "${screen_source_dir}/src/window/screen.cpp" screen_original)
set(screen_patched "${screen_original}")
abyss_replace_once(screen_patched [=[        // 270 rotation clock-wise makes screen content comes from the top where camera lies, to down where the ports reside.
        // That makes it a standard, non-flip landscape. 0 is obviously standard too. Therefore mode 90 and 180 needs flip.
        const float rotation_draw = ((mode_info.rotation == 90) || (mode_info.rotation == 180)) ? 180.0f : 0.0f;
]=] "")
abyss_replace_once(screen_patched [=[            if (rotation_draw != 0.0f) {
                dest_rect.top = screen_size - source_rect.top;
            }
]=] "")
abyss_replace_once(screen_patched
    "builder.draw_bitmap(dsa_texture, 0, dest_rect, source_rect, eka2l1::vec2(0, 0), rotation_draw, 0);"
    "builder.draw_bitmap(dsa_texture, 0, dest_rect, source_rect, eka2l1::vec2(0, 0), 0.0f, 0);")
abyss_replace_once(screen_patched "delete pitcher;" "delete[] pitcher;")
abyss_patch_source(epocservs "src/window/screen.cpp" "${screen_original}" "${screen_patched}")
unset(screen_original)
unset(screen_patched)

get_target_property(group_source_dir epocservs SOURCE_DIR)
file(READ "${group_source_dir}/include/services/window/window.h" window_header)
abyss_replace_once(window_header
    "ws::uid add_capture_key_notifier_to_server(epoc::event_capture_key_notifier &notifier);"
    "ws::uid add_capture_key_notifier_to_server(epoc::event_capture_key_notifier &notifier);\n        void remove_capture_keys(epoc::window *owner, ws::uid id, epoc::event_key_capture_type type, bool all = false);")
abyss_replace_once(window_header "        std::uint32_t pri_;" "        std::int32_t pri_;")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/patched/include/services/window/window.h" CONTENT "${window_header}" @ONLY)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${group_source_dir}/include/services/window/window.h")
target_include_directories(epocservs BEFORE PUBLIC "${CMAKE_CURRENT_BINARY_DIR}/patched/include")
target_include_directories(epocservs PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
file(READ "${group_source_dir}/src/window/window.cpp" window_original)
set(window_patched "${window_original}")
abyss_replace_once(window_patched "#include <services/window/window.h>"
    "#include <services/window/window.h>\n#include \"capture_cleanup.h\"\n#include \"capture_routing.h\"")
abyss_replace_once(window_patched [=[        if (!rqueue.empty() && notifier.pri_ == 0) {
            notifier.pri_ = rqueue.top().pri_ + 1;
        }
]=] "")
abyss_replace_once(window_patched "        return lhs.pri_ < rhs.pri_;"
    "        return lhs.pri_ == rhs.pri_ ? lhs.id < rhs.id : lhs.pri_ < rhs.pri_;")
# Repeat events follow the same capture rules as the initial translated key.
abyss_replace_once(window_patched [=[                if (get_focus()) {
                    repeatable_evt.type]=] [=[                if (cancel_repeatable_list.erase(data)) {
                    kern->unlock();
                    return;
                }
                auto *target = abyssme::capture_target(key_capture_requests, code,
                    epoc::event_modifier_repeatable | key_shipper.translator_.modifiers(),
                    epoc::event_key_capture_type::normal, static_cast<epoc::window *>(get_focus()));
                if (target) {
                    repeatable_evt.type]=])
abyss_replace_once(window_patched "repeatable_evt.handle = get_focus()->client_handle;"
    "repeatable_evt.handle = target->get_client_handle();")
abyss_replace_once(window_patched "get_focus()->queue_event(repeatable_evt);"
    "target->queue_event(repeatable_evt);")
abyss_replace_once(window_patched
    "    ws::uid window_server_client::add_capture_key_notifier_to_server"
    [=[    void window_server_client::remove_capture_keys(epoc::window *owner, ws::uid id,
        epoc::event_key_capture_type type, bool all) {
        abyssme::remove_captures(get_ws().key_capture_requests, owner, id, type, all);
    }

    ws::uid window_server_client::add_capture_key_notifier_to_server]=])
abyss_patch_source(epocservs "src/window/window.cpp" "${window_original}" "${window_patched}")
file(READ "${group_source_dir}/src/window/classes/wingroup.cpp" group_original)
set(group_patched "${group_original}")
abyss_replace_once(group_patched "#include <services/window/classes/wingroup.h>"
    "#include <services/window/classes/wingroup.h>\n#include \"capture_routing.h\"")
abyss_replace_once(group_patched "            ws_cmd_capture_key *capture_key_cmd = reinterpret_cast<decltype(capture_key_cmd)>(cmd.data_ptr);"
    [=[            if (cmd.header.cmd_len < sizeof(ws_cmd_capture_key)) {
                ctx.complete(epoc::error_argument);
                break;
            }
            ws_cmd_capture_key *capture_key_cmd = reinterpret_cast<decltype(capture_key_cmd)>(cmd.data_ptr);
            if (!abyssme::valid_capture(capture_key_cmd->modifier_mask, capture_key_cmd->modifiers,
                static_cast<std::int32_t>(capture_key_cmd->priority))) {
                ctx.complete(epoc::error_argument);
                break;
            }]=])
abyss_replace_once(group_patched "window_group::~window_group() {"
    "window_group::~window_group() {\n        if (client) client->remove_capture_keys(this, 0, event_key_capture_type::normal, true);")
abyss_replace_once(group_patched "        case EWsWinOpCaptureKey:\n"
    [=[        case EWsWinOpCancelCaptureKey:
        case EWsWinOpCancelCaptureKeyUpsAndDowns: {
            if (cmd.header.cmd_len < sizeof(std::uint32_t)) {
                ctx.complete(epoc::error_argument);
                break;
            }
            const auto id = *reinterpret_cast<const std::uint32_t *>(cmd.data_ptr);
            client->remove_capture_keys(this, id, op == EWsWinOpCancelCaptureKey
                ? event_key_capture_type::normal : event_key_capture_type::up_and_downs);
            ctx.complete(epoc::error_none);
            break;
        }

        case EWsWinOpCaptureKey:
]=])
abyss_patch_source(epocservs "src/window/classes/wingroup.cpp" "${group_original}" "${group_patched}")

file(READ "${group_source_dir}/src/window/io.cpp" io_original)
set(io_patched "${io_original}")
abyss_replace_once(io_patched "#include <services/window/io.h>"
    "#include <services/window/io.h>\n#include \"capture_routing.h\"")
abyss_replace_once(io_patched [=[            if (!focus) {
                continue;
            }

            evt.handle = focus->get_client_handle();
            extra_event.handle = focus->get_client_handle();

            focus->queue_event(evt);
]=] [=[            auto *raw_target = abyssme::capture_target(serv_->key_capture_requests,
                evt.key_evt_.scancode, evt.key_evt_.modifiers, event_key_capture_type::up_and_downs,
                static_cast<epoc::window *>(focus));
            if (raw_target) {
                evt.handle = raw_target->get_client_handle();
                raw_target->queue_event(evt);
            }
]=])
abyss_replace_once(io_patched [=[                // Give it a single key event also
                focus->queue_event(extra_event);]=] [=[                auto *key_target = abyssme::capture_target(serv_->key_capture_requests,
                    extra_event.key_evt_.code, extra_event.key_evt_.modifiers, event_key_capture_type::normal,
                    static_cast<epoc::window *>(focus));
                if (key_target) {
                    extra_event.handle = key_target->get_client_handle();
                    key_target->queue_event(extra_event);
                }]=])
abyss_replace_once(io_patched [=[            // Iterates through key capture requests and deliver those in needs.key_capture_request_queue &rqueue = key_capture_requests[extra_key_evt.key_evt_.code];
            window_server::key_capture_request_queue &rqueue = serv_->key_capture_requests[evt.key_evt_.code];

            for (auto ite = rqueue.end(); ite != rqueue.begin();) {
                --ite;
                // No need to deliver twice.
                if (ite->user->id == focus->id) {
                    break;
                }

                switch (ite->type_) {
                case epoc::event_key_capture_type::normal:
                    if (dont_send_extra_key_event) {
                        break;
                    }
                    extra_event.handle = ite->user->get_client_handle();

                    ite->user->queue_event(extra_event);

                    break;

                case epoc::event_key_capture_type::up_and_downs:
                    evt.handle = ite->user->get_client_handle();

                    ite->user->queue_event(evt);

                    break;

                default:
                    break;
                }
            }
]=] "")
abyss_patch_source(epocservs "src/window/io.cpp" "${io_original}" "${io_patched}")
