// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import java.util.Locale;

enum SystemProgress {
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
