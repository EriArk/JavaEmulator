package io.github.eriark.abyssme.symbian;

import java.io.*;

/** Serial queue over the existing installer; completion receipts close the crash window between journals. */
final class ArchiveQueue {
    private final ArchiveBatch store;
    private final PendingGameImport pending;
    private final GameInstallFlow flow;
    private final ComponentStore components;
    private final PlatformStore platforms;
    ArchiveQueue(ArchiveBatch store,PendingGameImport pending,GameInstallFlow flow,ComponentStore components,PlatformStore platforms) {
        this.store=store; this.pending=pending; this.flow=flow; this.components=components; this.platforms=platforms;
    }
    boolean ownsPending() throws IOException {
        PendingGameImport.Entry job=pending.read(); ArchiveBatch.Batch batch=store.read();
        return job!=null && batch!=null && batch.owned(job.owner)!=null;
    }
    void rememberPhone(String selected,boolean reuse) throws IOException {
        if(!ownsPending())throw new IOException("No queued game is waiting");
        if(!selected.matches("[0-9a-f]{64}"))throw new IOException("Invalid phone selection");
        ArchiveBatch.Batch batch=store.read(); GameImport.Kind kind=pending.read().kind;
        if(reuse)batch.phones.put(kind,selected);else batch.phones.remove(kind);
        store.save(batch);
    }
    void accept(GameInstallFlow.Result result) throws IOException {
        PendingGameImport.Entry job=pending.read(); ArchiveBatch.Batch batch=store.read();
        if(job==null || batch==null) return;
        ArchiveBatch.Item item=batch.owned(job.owner); if(item==null) return;
        item.system=job.system;
        if(result.action==GameInstallFlow.Action.DONE) {
            item.state=ArchiveBatch.State.DONE; item.detail="Installed";
            store.save(batch); pending.discard();
        } else {
            item.state=ArchiveBatch.State.WAITING;
            switch(result.action) {
                case SYSTEM: item.detail="Needs phone files"; break;
                case CHOOSE: item.detail="Choose a phone"; break;
                case PLATFORM: item.detail="Needs N-Gage 2.0"; break;
                case COMPONENT: item.detail="Needs "+result.component.name; break;
                case OPEN_PLATFORM: item.detail="Finish in the N-Gage installer"; break;
                default: item.detail="Interrupted; retry required"; break;
            }
            store.save(batch);
        }
    }
    GameInstallFlow.Result run(String selected,boolean retry,PreparedSystemArchive.Progress progress) throws Exception {
        ArchiveBatch.Batch batch=store.read(); if(batch==null) return null;
        PendingGameImport.Entry existing=pending.read();
        if(existing!=null && batch.owned(existing.owner)==null) throw new IOException("Finish the separate pending import before continuing this queue");
        if(!batch.started) {
            boolean any=false;
            for(ArchiveBatch.Item item:batch.items) {
                if(item.selected && item.problem.isEmpty()) any=true;
                else item.state=ArchiveBatch.State.SKIPPED;
            }
            if(!any) throw new IOException("Select at least one valid file");
            batch.started=true; store.save(batch);
        }
        while(true) {
            progress.update("Continuing archive queue");
            batch=store.read(); PendingGameImport.Entry job=pending.read();
            ArchiveBatch.Item item=null;
            if(job!=null) {
                item=batch.owned(job.owner);
                if(item==null) throw new IOException("Finish the separate pending import before continuing this queue");
                // Queue commit won the race before pending cleanup. Never reinstall it.
                if(item.state==ArchiveBatch.State.DONE || item.state==ArchiveBatch.State.FAILED || item.state==ArchiveBatch.State.SKIPPED) {
                    pending.discard(); continue;
                }
            } else {
                for(ArchiveBatch.Item candidate:batch.items) if(candidate.state==ArchiveBatch.State.ACTIVE) { item=candidate; break; }
                if(item==null) for(ArchiveBatch.Item candidate:batch.items)
                    if(candidate.state==ArchiveBatch.State.READY && candidate.role!=ArchiveBatch.Role.GAME) { item=candidate; break; }
                if(item==null) for(ArchiveBatch.Item candidate:batch.items)
                    if(candidate.state==ArchiveBatch.State.READY) { item=candidate; break; }
                if(item==null) return null;
            }
            try {
                progress.update("Importing "+(item.id+1)+" / "+batch.items.size()+": "+item.label());
                if(job==null) {
                    store.verify(item); item.state=ArchiveBatch.State.ACTIVE; store.save(batch);
                    try(GameImport game=GameImport.inspect(store.payload(item))) {
                        if(item.role!=ArchiveBatch.Role.GAME) {
                            if(item.role==ArchiveBatch.Role.PLATFORM) platforms.save(game); else components.save(game,null);
                            item.state=ArchiveBatch.State.DONE; item.detail="Installer saved"; store.save(batch); continue;
                        }
                        pending.begin(game,item.label(),batch.owner(item));
                        if(!item.system.isEmpty()) pending.update(pending.read(),item.system,PendingGameImport.Step.READY);
                    }
                }
                GameInstallFlow.Result result=flow.resume(selected,retry,batch.phones.get(item.kind)); selected=null; retry=false;
                accept(result);
                if(result.action!=GameInstallFlow.Action.DONE) return result;
            } catch(Exception error) {
                if(error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) throw error;
                // Installation errors belong to this row; unrelated rows can continue.
                PendingGameImport.Entry actual=pending.read();
                if(actual!=null && actual.step==PendingGameImport.Step.COMPLETE && batch.owner(item).equals(actual.owner)) throw error;
                if(actual!=null && batch.owner(item).equals(actual.owner)) item.system=actual.system;
                item.state=ArchiveBatch.State.FAILED; item.detail=ArchiveBatch.clean(error.getMessage(),400);
                store.save(batch);
                if(actual!=null && batch.owner(item).equals(actual.owner)) pending.discard();
                selected=null; retry=false;
            }
        }
    }
    void defer() throws IOException {
        if(!ownsPending()) return;
        PendingGameImport.Entry job=pending.read(); ArchiveBatch.Batch batch=store.read(); ArchiveBatch.Item item=batch.owned(job.owner);
        if(job.step==PendingGameImport.Step.GUEST) throw new IOException("Finish or skip the current N-Gage installer before continuing other games.");
        item.system=job.system; item.state=ArchiveBatch.State.WAITING; store.save(batch); pending.discard();
    }
    void retry(int id) throws IOException {
        ArchiveBatch.Batch batch=store.read(); ArchiveBatch.Item item=batch.items.get(id);
        if(!item.problem.isEmpty()) throw new IOException(item.problem);
        if(item.state!=ArchiveBatch.State.FAILED && item.state!=ArchiveBatch.State.WAITING) throw new IOException("This file does not need retrying");
        if(pending.exists() && !batch.owner(item).equals(pending.read().owner)) throw new IOException("Finish or defer the current game first");
        item.state=ArchiveBatch.State.READY; item.detail=""; store.save(batch);
    }
    void skip(int id) throws IOException {
        ArchiveBatch.Batch batch=store.read(); ArchiveBatch.Item item=batch.items.get(id);
        if(item.state==ArchiveBatch.State.DONE) return;
        item.state=ArchiveBatch.State.SKIPPED; item.detail="Skipped; installed files were not removed"; store.save(batch);
        PendingGameImport.Entry job=pending.read(); if(job!=null && batch.owner(item).equals(job.owner)) pending.discard();
    }
    void discard() throws IOException {
        if(ownsPending()) pending.discard(); store.discard();
    }
}
