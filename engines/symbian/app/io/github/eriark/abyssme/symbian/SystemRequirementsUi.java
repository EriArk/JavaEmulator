// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import ru.playsoftware.j2meloader.R;

/** Render requirements from platform/readiness values, never from English labels. */
final class SystemRequirementsUi {
    static String title(Context c, SystemRequirements.Row row) {
        if (row.kind == null) return c.getString(R.string.system_java_mophun);
        switch (row.kind) {
            case CARD: return c.getString(R.string.system_original_ngage);
            case SIS_LEGACY: return c.getString(R.string.system_legacy_s60);
            case NGAGE2: return c.getString(R.string.system_ngage2);
            default: return c.getString(R.string.system_modern_s60);
        }
    }
    static String status(Context c, SystemRequirements.Row row, boolean platformSaved) {
        return c.getString(row.kind == null ? R.string.system_no_extra
                : !row.phoneReady ? R.string.system_phone_needed
                : !row.platform ? R.string.system_phone_added
                : platformSaved ? R.string.system_setup_added : R.string.system_checked_on_import);
    }
    static String description(Context c, SystemRequirements.Row row, boolean platformSaved) {
        if (row.kind == null) return c.getString(R.string.system_java_help);
        switch (row.kind) {
            case CARD: return c.getString(R.string.system_ngage_help);
            case SIS_LEGACY: return c.getString(R.string.system_legacy_help);
            case NGAGE2: return c.getString(R.string.system_ngage2_help,
                    c.getString(row.phoneReady ? R.string.system_dump_added : R.string.system_dump_needed),
                    c.getString(platformSaved ? R.string.system_platform_saved : R.string.system_platform_needed));
            default: return c.getString(R.string.system_modern_help);
        }
    }
    static String needed(Context c, GameImport.Kind kind) {
        switch (kind) {
            case CARD: return c.getString(R.string.system_needed_card);
            case NGAGE2: return c.getString(R.string.system_needed_ngage2);
            case SIS_LEGACY: return c.getString(R.string.system_needed_legacy);
            default: return c.getString(R.string.system_needed_modern);
        }
    }
    private SystemRequirementsUi() { }
}
