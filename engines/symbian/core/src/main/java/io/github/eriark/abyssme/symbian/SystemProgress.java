// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import java.util.Locale;

enum SystemProgress {
    READ_COMPRESSED("Reading RAR / 7z archive"),
    READ_ENTRY("Reading: %1$s"),
    READ_ENTRY_SIZE("Reading: %1$s (%2$d MB)"),
    INSPECT_SYSTEM("Inspecting system archive"),
    READ_SYSTEM("Reading system files %1$d%%"),
    CHECK_SYSTEM("Checking system files %1$d%%"),
    PREPARE_SYSTEM("Preparing system %1$d/%2$d: %3$s"),
    READ_INPUT("Reading selected file: %1$d MB"),
    INSPECT_ITEM("Inspecting %1$d / %2$d: %3$s"),
    IMPORT_ITEM("Importing %1$d / %2$d: %3$s"),
    CONTINUE_QUEUE("Continuing archive queue"),
    CHECK_COMPONENTS("Checking required components"),
    INSTALL_COMPONENT("Installing component: %1$s"),
    INSTALL_GAME("Installing %1$s"),
    BACKUP("Backing up phone: %1$d/%2$d"),
    RESTORE("Restoring phone: %1$d/%2$d"),
    CHECK_PHONE("Checking restored phone"),
    ADD_PHONE("Adding restored phone to library"),
    CHECK_FILES("Checking phone files: %1$d"),
    COPY_DATA("Copying phone data: %1$d MB");

    private final String english;
    SystemProgress(String english) { this.english=english; }
    String fallback(Object... arguments) { return String.format(Locale.ROOT,english,arguments); }
}
