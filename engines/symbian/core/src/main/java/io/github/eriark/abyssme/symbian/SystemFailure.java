// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import java.io.IOException;
import java.util.Locale;

/** Stable app-owned error identity; raw file paths and causes are never translated. */
final class SystemFailure extends IOException {
    enum Code {
        BACKUP_UNSUPPORTED("This is not a supported AbyssME phone backup"),
        BACKUP_LIMIT("Phone backup exceeds supported limits"),
        BINDINGS_LIMIT("Too many game bindings"),
        BINDING_INVALID("Invalid game binding"),
        BACKUP_INCOMPLETE("Incomplete phone backup"),
        INVENTORY_INVALID("Invalid backup inventory"),
        SETTINGS_LIMIT("Backup settings are too large"),
        ARTWORK_LIMIT("Backup artwork is too large or empty"),
        TITLES_SIZE("Invalid library titles size"),
        PHONE_METADATA("Invalid phone metadata"),
        UNSAFE_PATH("Unsafe backup path"),
        PATH_DEPTH("Backup paths are too deep"),
        INPUT_LIMIT("Phone backup exceeds 4 GB"),
        SELECT_BACKUP("Select an AbyssME phone backup ZIP, not a game or phone dump"),
        TITLES_VERSION("Library titles require backup version 3"),
        DUPLICATE_PATH("Duplicate backup path"),
        EXPANDED_LIMIT("Phone backup is too large"),
        INVENTORY_SIZE("Backup size does not match its inventory"),
        MISSING_DIRECTORY("Missing backup directory"),
        UNSUPPORTED_ENTRY("Unsupported or duplicate backup entry"),
        CONTENTS_MISMATCH("Backup contents do not match the inventory"),
        MISSING_FILES("Backup files are missing"),
        INVENTORY_LIMIT("Backup inventory is too large"),
        COMPRESSED_LIMIT("Compressed backup exceeds 4 GB"),
        ROM_MISMATCH("Phone ROM identity does not match the backup"),
        RPKG_MISMATCH("Phone RPKG identity does not match the backup"),
        MISSING_REGISTRATION("Phone registration is missing"),
        RESTORED_INCOMPLETE("Restored system files are incomplete"),
        ARTWORK_SIZE("Invalid backup artwork size"),
        ARTWORK_INVALID("Invalid or oversized backup image"),
        ARTWORK_UNREADABLE("Unreadable backup image"),
        LINKED_FOLDERS("Linked phone folders cannot be backed up"),
        PHONE_FILE_LIMIT("Too many phone files"),
        LINKED_FILES("Linked phone files cannot be backed up"),
        FILE_SIZE_CHANGED("Backup file size changed"),
        METADATA_LIMIT("Backup metadata is too large"),
        METADATA_INVALID("Invalid backup metadata"),
        REMOVAL_PLAN("Invalid package removal plan"),
        REMOVAL_PHONE("Invalid removal phone identity"),
        REMOVAL_PENDING("An earlier uninstall is incomplete on this phone. Restore a phone backup before making further changes"),
        PACKAGE_CHANGED("The package changed. Review removal again"),
        LIBRARY_CHANGED("The game's library entries have changed. Nothing was removed"),
        REMOVAL_INCOMPLETE("Removal was incomplete. The library reference has been kept; restore a phone backup or retry removal"),
        OTHER_APPS_MISSING("Other phone applications are missing after removal. The previous library has been kept; restore a phone backup"),
        RECOVERY_NOT_CLEARED("Removal finished but its recovery record could not be cleared"),
        RECOVERY_UNSUPPORTED("Unsupported uninstall recovery record"),
        APPS_INVALID("Invalid installed application list"),
        APP_ID_INVALID("Invalid installed application identity"),
        RECOVERY_LIMIT("Uninstall recovery record is too large"),
        RECOVERY_WRITE_LIMIT("Uninstall recovery record is too large. Nothing was removed"),
        RECOVERY_SAVE_FAILED("Cannot save uninstall recovery record"),
        UNSUPPORTED_FILE("Unsupported file in phone backup: %1$s"),
        DAMAGED_FILE("Damaged backup file: %1$s"),
        VERIFICATION_FAILED("Backup verification failed: %1$s"),
        SPACE_REQUIRED("Not enough free space. Free at least %1$d MB and try again. Existing phones are unchanged.");
        final String english;
        Code(String english) { this.english=english; }
    }
    final Code code;
    private final Object[] arguments;
    SystemFailure(Code code, Object... arguments) { this(code, null, arguments); }
    SystemFailure(Code code, Throwable cause, Object... arguments) {
        super(String.format(Locale.ROOT, code.english, arguments), cause);
        this.code=code; this.arguments=arguments.clone();
    }
    Object[] arguments() { return arguments.clone(); }
}