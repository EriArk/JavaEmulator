// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import ru.playsoftware.j2meloader.R;

/** Presentation only: manifests, package plans and component identities remain unchanged. */
final class SystemTransferText {
    private SystemTransferText() { }
    static String imported(Context context, SystemFileCatalog.ImportResult result) {
        int added=result.entries.size()-result.reused;
        return result.failures.isEmpty()
                ?context.getString(R.string.native_system_import_counts,added,result.reused)
                :context.getString(R.string.native_system_import_errors,added,result.reused,String.join("\n",result.failures));
    }
    static String backup(Context context, PhoneBackup.Preview preview) {
        int version=preview.manifest.optInt("version");
        int resource=version>=3?R.string.native_restore_preview_v3
                :version>=2?R.string.native_restore_preview_v2:R.string.native_restore_preview_v1;
        return context.getString(resource,preview.phone,preview.bytes/(1024*1024));
    }
    static String removal(Context context, GameRemoval.Plan plan, String phone) {
        StringBuilder apps=new StringBuilder();
        for(int i=5;i<5+plan.affected.size()*2;i+=2) {
            if(apps.length()>0)apps.append('\n');
            apps.append(plan.rows[i+1].isEmpty()?plan.rows[i]:plan.rows[i+1]);
        }
        return context.getString(R.string.native_removal_preview,phone,plan.rows[2],plan.rows[3],apps);
    }
    static String component(Context context, SisMetadata.Need need) {
        return need.to==null?context.getString(R.string.native_component_minimum,need.name,need.from)
                :context.getString(R.string.native_component_range,need.name,need.from,need.to);
    }
    static String progress(Context context, SystemProgress step, Object... arguments) {
        switch(step) {
            case BACKUP: return context.getString(R.string.native_progress_backup,
                    ((Number)arguments[0]).longValue(),((Number)arguments[1]).longValue());
            case RESTORE: return context.getString(R.string.native_progress_restore,
                    ((Number)arguments[0]).longValue(),((Number)arguments[1]).longValue());
            case CHECK_PHONE: return context.getString(R.string.native_progress_check_phone);
            case ADD_PHONE: return context.getString(R.string.native_progress_add_phone);
            case CHECK_FILES: return context.getString(R.string.native_progress_check_files,((Number)arguments[0]).longValue());
            case COPY_DATA: return context.getString(R.string.native_progress_copy_data,((Number)arguments[0]).longValue());
            default: throw new IllegalArgumentException("Unknown transfer step: "+step);
        }
    }
    static String archiveState(Context context, ArchiveBatch.State state) {
        switch(state) {
            case READY: return context.getString(R.string.native_queue_ready);
            case ACTIVE: return context.getString(R.string.native_queue_active);
            case WAITING: return context.getString(R.string.native_queue_waiting);
            case DONE: return context.getString(R.string.native_queue_done);
            case FAILED: return context.getString(R.string.native_queue_failed);
            case SKIPPED: return context.getString(R.string.native_queue_skipped);
            default: throw new IllegalArgumentException("Unknown queue state: "+state);
        }
    }
    static String archiveType(Context context, ArchiveBatch.Item item) {
        return context.getString(item.role==ArchiveBatch.Role.PLATFORM?R.string.native_type_platform
                :item.kind==null?R.string.native_type_unsupported
                :item.kind==GameImport.Kind.SIS_LEGACY?R.string.native_type_legacy
                :item.kind==GameImport.Kind.SIS_MODERN?R.string.native_type_modern
                :item.kind==GameImport.Kind.CARD?R.string.native_type_card:R.string.native_type_ngage2);
    }
}
