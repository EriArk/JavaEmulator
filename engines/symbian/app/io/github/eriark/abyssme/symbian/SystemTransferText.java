// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import ru.playsoftware.j2meloader.R;

/** Presentation only: manifests, package plans and component identities remain unchanged. */
final class SystemTransferText {
    private SystemTransferText() { }
    static String problem(Context context,ArchiveBatch.Item item) {
        return item.problemFailure==null?item.problem:SystemFailureText.message(context,item.problemFailure);
    }
    static String detail(Context context,ArchiveBatch.Item item) {
        if(item.detailFailure!=null)return SystemFailureText.message(context,item.detailFailure);
        if(item.notice==null)return item.detail;
        switch(item.notice) {
            case INSTALLED: return context.getString(R.string.native_notice_installed);
            case PHONE_FILES: return context.getString(R.string.native_notice_phone_files);
            case CHOOSE_PHONE: return context.getString(R.string.native_notice_choose_phone);
            case PLATFORM: return context.getString(R.string.native_notice_platform);
            case COMPONENT: return context.getString(R.string.native_notice_component,item.noticeArgument);
            case GUEST_INSTALLER: return context.getString(R.string.native_notice_guest_installer);
            case RETRY: return context.getString(R.string.native_notice_retry);
            case SAVED: return context.getString(R.string.native_notice_saved);
            case SKIPPED: return context.getString(R.string.native_notice_skipped);
            case DUPLICATE: return context.getString(R.string.native_notice_duplicate);
            case ALTERNATIVE: return context.getString(R.string.native_notice_alternative,item.noticeArgument);
            default: throw new IllegalArgumentException("Unknown archive notice: "+item.notice);
        }
    }
    static PreparedSystemArchive.Progress localize(Context context, PreparedSystemArchive.Progress sink) {
        return new PreparedSystemArchive.Progress() {
            public void update(String raw) throws java.io.IOException { sink.update(raw); }
            public void update(SystemProgress step,Object... arguments) throws java.io.IOException { sink.update(progress(context,step,arguments)); }
        };
    }
    static String imported(Context context, SystemFileCatalog.ImportResult result) {
        int added=result.entries.size()-result.reused;
        java.util.List<String> failures=new java.util.ArrayList<>();
        for(int i=0;i<result.failures.size();i++) {
            SystemFileCatalog.ImportFailure failure=result.failureDetails.get(i);
            failures.add(failure==null?result.failures.get(i):context.getString(R.string.native_import_source_error,failure.source,SystemFailureText.message(context,failure.error)));
        }
        return result.failures.isEmpty()
                ?context.getString(R.string.native_system_import_counts,added,result.reused)
                :context.getString(R.string.native_system_import_errors,added,result.reused,String.join("\n",failures));
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
            case READ_COMPRESSED: return context.getString(R.string.native_progress_read_compressed);
            case READ_ENTRY: return context.getString(R.string.native_progress_read_entry,arguments[0]);
            case READ_ENTRY_SIZE: return context.getString(R.string.native_progress_read_entry_size,arguments[0],((Number)arguments[1]).longValue());
            case INSPECT_SYSTEM: return context.getString(R.string.native_progress_inspect_system);
            case READ_SYSTEM: return context.getString(R.string.native_progress_read_system,((Number)arguments[0]).longValue());
            case CHECK_SYSTEM: return context.getString(R.string.native_progress_check_system,((Number)arguments[0]).longValue());
            case PREPARE_SYSTEM: return context.getString(R.string.native_progress_prepare_system,((Number)arguments[0]).longValue(),((Number)arguments[1]).longValue(),arguments[2]);
            case READ_INPUT: return context.getString(R.string.native_progress_read_input,((Number)arguments[0]).longValue());
            case INSPECT_ITEM: return context.getString(R.string.native_progress_inspect_item,((Number)arguments[0]).longValue(),((Number)arguments[1]).longValue(),arguments[2]);
            case IMPORT_ITEM: return context.getString(R.string.native_progress_import_item,((Number)arguments[0]).longValue(),((Number)arguments[1]).longValue(),arguments[2]);
            case CONTINUE_QUEUE: return context.getString(R.string.native_progress_continue_queue);
            case CHECK_COMPONENTS: return context.getString(R.string.native_progress_check_components);
            case INSTALL_COMPONENT: return context.getString(R.string.native_progress_install_component,arguments[0]);
            case INSTALL_GAME: return context.getString(R.string.native_progress_install_game,arguments[0]);
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
