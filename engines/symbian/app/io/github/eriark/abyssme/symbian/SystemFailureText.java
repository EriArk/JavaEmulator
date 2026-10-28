// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import ru.playsoftware.j2meloader.R;

final class SystemFailureText {
    private SystemFailureText() { }
    static String message(Context context, Throwable error) {
        if (error instanceof SystemFailure) {
            SystemFailure failure=(SystemFailure)error;
            return context.getString(resource(failure.code),failure.arguments());
        }
        return error.getMessage();
    }
    static int resource(SystemFailure.Code code) {
        switch(code) {
            case BACKUP_UNSUPPORTED: return R.string.native_error_backup_unsupported;
            case BACKUP_LIMIT: return R.string.native_error_backup_limit;
            case BINDINGS_LIMIT: return R.string.native_error_bindings_limit;
            case BINDING_INVALID: return R.string.native_error_binding_invalid;
            case BACKUP_INCOMPLETE: return R.string.native_error_backup_incomplete;
            case INVENTORY_INVALID: return R.string.native_error_inventory_invalid;
            case SETTINGS_LIMIT: return R.string.native_error_settings_limit;
            case ARTWORK_LIMIT: return R.string.native_error_artwork_limit;
            case TITLES_SIZE: return R.string.native_error_titles_size;
            case PHONE_METADATA: return R.string.native_error_phone_metadata;
            case UNSAFE_PATH: return R.string.native_error_unsafe_path;
            case PATH_DEPTH: return R.string.native_error_path_depth;
            case INPUT_LIMIT: return R.string.native_error_input_limit;
            case SELECT_BACKUP: return R.string.native_error_select_backup;
            case TITLES_VERSION: return R.string.native_error_titles_version;
            case DUPLICATE_PATH: return R.string.native_error_duplicate_path;
            case EXPANDED_LIMIT: return R.string.native_error_expanded_limit;
            case INVENTORY_SIZE: return R.string.native_error_inventory_size;
            case MISSING_DIRECTORY: return R.string.native_error_missing_directory;
            case UNSUPPORTED_ENTRY: return R.string.native_error_unsupported_entry;
            case CONTENTS_MISMATCH: return R.string.native_error_contents_mismatch;
            case MISSING_FILES: return R.string.native_error_missing_files;
            case INVENTORY_LIMIT: return R.string.native_error_inventory_limit;
            case COMPRESSED_LIMIT: return R.string.native_error_compressed_limit;
            case ROM_MISMATCH: return R.string.native_error_rom_mismatch;
            case RPKG_MISMATCH: return R.string.native_error_rpkg_mismatch;
            case MISSING_REGISTRATION: return R.string.native_error_missing_registration;
            case RESTORED_INCOMPLETE: return R.string.native_error_restored_incomplete;
            case ARTWORK_SIZE: return R.string.native_error_artwork_size;
            case ARTWORK_INVALID: return R.string.native_error_artwork_invalid;
            case ARTWORK_UNREADABLE: return R.string.native_error_artwork_unreadable;
            case LINKED_FOLDERS: return R.string.native_error_linked_folders;
            case PHONE_FILE_LIMIT: return R.string.native_error_phone_file_limit;
            case LINKED_FILES: return R.string.native_error_linked_files;
            case FILE_SIZE_CHANGED: return R.string.native_error_file_size_changed;
            case METADATA_LIMIT: return R.string.native_error_metadata_limit;
            case METADATA_INVALID: return R.string.native_error_metadata_invalid;
            case REMOVAL_PLAN: return R.string.native_error_removal_plan;
            case REMOVAL_PHONE: return R.string.native_error_removal_phone;
            case REMOVAL_PENDING: return R.string.native_error_removal_pending;
            case PACKAGE_CHANGED: return R.string.native_error_package_changed;
            case LIBRARY_CHANGED: return R.string.native_error_library_changed;
            case REMOVAL_INCOMPLETE: return R.string.native_error_removal_incomplete;
            case OTHER_APPS_MISSING: return R.string.native_error_other_apps_missing;
            case RECOVERY_NOT_CLEARED: return R.string.native_error_recovery_not_cleared;
            case RECOVERY_UNSUPPORTED: return R.string.native_error_recovery_unsupported;
            case APPS_INVALID: return R.string.native_error_apps_invalid;
            case APP_ID_INVALID: return R.string.native_error_app_id_invalid;
            case RECOVERY_LIMIT: return R.string.native_error_recovery_limit;
            case RECOVERY_WRITE_LIMIT: return R.string.native_error_recovery_write_limit;
            case RECOVERY_SAVE_FAILED: return R.string.native_error_recovery_save_failed;
            case UNSUPPORTED_FILE: return R.string.native_error_unsupported_file;
            case DAMAGED_FILE: return R.string.native_error_damaged_file;
            case VERIFICATION_FAILED: return R.string.native_error_verification_failed;
            case SPACE_REQUIRED: return R.string.native_error_space_required;
            default: throw new IllegalArgumentException("Unknown system failure: " + code);
        }
    }
}