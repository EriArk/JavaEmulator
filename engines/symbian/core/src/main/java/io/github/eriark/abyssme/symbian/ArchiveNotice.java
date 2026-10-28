// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

/** Display identity only; queue scheduling still uses State/Role and retained file identities. */
enum ArchiveNotice {
    INSTALLED("Installed"), PHONE_FILES("Needs phone files"), CHOOSE_PHONE("Choose a phone"),
    PLATFORM("Needs N-Gage 2.0"), COMPONENT("Needs %1$s"),
    GUEST_INSTALLER("Finish in the N-Gage installer"), RETRY("Interrupted; retry required"),
    SAVED("Installer saved"), SKIPPED("Skipped; installed files were not removed"),
    DUPLICATE("Duplicate file"), ALTERNATIVE("Alternative version of %1$s");

    private final String english;
    ArchiveNotice(String english) { this.english=english; }
    String fallback(String argument) { return String.format(java.util.Locale.ROOT,english,argument); }
    static ArchiveNotice read(String name) {
        try { return valueOf(name); } catch(IllegalArgumentException invalid) { return null; }
    }
}
