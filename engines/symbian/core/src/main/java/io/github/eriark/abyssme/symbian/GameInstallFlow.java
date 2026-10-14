package io.github.eriark.abyssme.symbian;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Dependency decisions are separate from native installation and Android dialogs. */
final class GameInstallFlow {
    static final int PLATFORM_UID = 536902457;
    enum Action { SYSTEM, CHOOSE, PLATFORM, COMPONENT, OPEN_PLATFORM, RETRY, DONE }
    static final class Result {
        final Action action;
        final List<SystemFileCatalog.Entry> choices;
        SisMetadata.Need component;
        Result(Action action) { this(action, Collections.emptyList()); }
        Result(Action action, List<SystemFileCatalog.Entry> choices) { this.action = action; this.choices = choices; }
    }
    interface Backend {
        void open(SystemFileCatalog.Entry entry) throws Exception;
        boolean hasPlatform() throws Exception;
        void install(File file, GameImport.Kind kind) throws Exception;
        void stage(File file, String hash) throws Exception;
        default SisMetadata.Version installedVersion(long uid) throws Exception { return null; }
        default void status(String message) throws Exception { }
    }
    private final PendingGameImport pending;
    private final SystemFileCatalog catalog;
    private final Backend backend;
    private final PlatformStore platforms;
    private final ComponentStore components;
    GameInstallFlow(PendingGameImport pending, SystemFileCatalog catalog, Backend backend) {
        this(pending, catalog, backend, null);
    }
    GameInstallFlow(PendingGameImport pending, SystemFileCatalog catalog, Backend backend, PlatformStore platforms) {
        this(pending,catalog,backend,platforms,null);
    }
    GameInstallFlow(PendingGameImport pending, SystemFileCatalog catalog, Backend backend, PlatformStore platforms, ComponentStore components) {
        this.pending = pending; this.catalog = catalog; this.backend = backend; this.platforms = platforms; this.components=components;
    }
    Result resume(String selected, boolean retry) throws Exception {
        return resume(selected,retry,null);
    }
    Result resume(String selected, boolean retry,String preferred) throws Exception {
        PendingGameImport.Entry job = pending.read();
        if (job == null) return new Result(Action.DONE);
        if (job.step == PendingGameImport.Step.COMPLETE) { if(job.owner.isEmpty()) pending.discard(); return new Result(Action.DONE); }
        if (job.step == PendingGameImport.Step.INSTALLING && !retry) return new Result(Action.RETRY);
        pending.verify(job);
        SystemFileCatalog.Entry system;
        if (!job.system.isEmpty()) {
            if (selected != null && !selected.equals(job.system)) throw new IOException("This import already has a selected system");
            system = catalog.read(job.system);
            if (!system.supports(job.kind)) throw new IOException("The selected system is incompatible with this package");
        } else {
            List<SystemFileCatalog.Entry> choices = catalog.candidates(job.kind, job.hash);
            if (choices.isEmpty()) return new Result(Action.SYSTEM);
            if(selected==null && choices.size()>1 && preferred!=null)
                for(SystemFileCatalog.Entry choice:choices)if(choice.id.equals(preferred)) {selected=preferred;break;}
            if (selected == null && choices.size() > 1) return new Result(Action.CHOOSE, choices);
            system = null;
            for (SystemFileCatalog.Entry choice : choices)
                if (selected == null || selected.equals(choice.id)) { system = choice; break; }
            if (system == null) throw new IOException("The selected system is no longer available");
            job = pending.update(job, system.id, PendingGameImport.Step.READY);
        }
        backend.open(system);
        if (job.kind == GameImport.Kind.NGAGE2) {
            if (!backend.hasPlatform()) {
                pending.update(job, system.id, PendingGameImport.Step.PLATFORM);
                if (platforms != null) {
                    GameImport cached;
                    try { cached = platforms.open(); }
                    catch (IOException error) {
                        android.util.Log.w("AbyssSymbian", "Saved platform unavailable", error);
                        return new Result(Action.PLATFORM);
                    }
                    if (cached != null) try (GameImport platform = cached) { return addPlatform(platform); }
                }
                return new Result(Action.PLATFORM);
            }
            // Reopening the guest installer must not requeue a consumed package.
            if (job.step != PendingGameImport.Step.GUEST) backend.stage(pending.payload(), job.hash);
            catalog.bind(job.hash, system);
            pending.update(job, system.id, PendingGameImport.Step.GUEST);
            return new Result(Action.OPEN_PLATFORM);
        }
        if(job.kind==GameImport.Kind.SIS_MODERN && components!=null) {
            Result needed=prepareComponents(job,pending.payload());
            if(needed!=null) return needed;
        }
        pending.update(job, system.id, PendingGameImport.Step.INSTALLING);
        backend.install(pending.payload(), job.kind);
        catalog.bind(job.hash, system);
        pending.update(job, system.id, PendingGameImport.Step.COMPLETE);
        if(job.owner.isEmpty()) pending.discard();
        return new Result(Action.DONE);
    }
    private Result prepareComponents(PendingGameImport.Entry job,File installer) throws Exception {
        backend.status("Checking required components");
        ComponentPlan plan;
        try { plan=new ComponentPlan(SisMetadata.read(installer),components,backend); }
        catch(ComponentPlan.Missing missing) {
            pending.update(job,job.system,PendingGameImport.Step.COMPONENT);
            Result result=new Result(Action.COMPONENT); result.component=missing.need; return result;
        }
        for(ComponentStore.Item item:plan.installs) {
            item.verify();
            backend.status("Installing component: "+item.metadata.name);
            pending.update(job,job.system,PendingGameImport.Step.INSTALLING);
            backend.install(item.file,GameImport.Kind.SIS_MODERN);
            SisMetadata.Version installed=backend.installedVersion(item.metadata.uid);
            if(installed==null || installed.compareTo(item.metadata.version)!=0)
                throw new IOException(item.metadata.name+" was not registered with the expected version. Your game is still waiting.");
        }
        backend.status("Installing "+job.name);
        return null;
    }
    Result addComponent(GameImport input) throws Exception {
        PendingGameImport.Entry job=pending.read();
        if(components==null || job==null || job.step!=PendingGameImport.Step.COMPONENT)
            throw new IOException("No component is currently requested. Continue the pending import first.");
        pending.verify(job); backend.open(catalog.read(job.system));
        GameImport platform=null;
        try {
            File installer=pending.payload();
            if(job.kind==GameImport.Kind.NGAGE2) {
                platform=platforms.open();
                if(platform==null) throw new IOException("Add the N-Gage 2.0 installer again.");
                installer=platform.file;
            }
            try { new ComponentPlan(SisMetadata.read(installer),components,backend); }
            catch(ComponentPlan.Missing missing) {
                components.save(input,missing.need);
                return resume(null,true);
            }
            throw new IOException("This component is no longer needed. Continue the pending import.");
        } finally { if(platform!=null) platform.close(); }
    }
    Result addPlatform(GameImport platform) throws Exception {
        PendingGameImport.Entry job = pending.read();
        if (job == null || job.kind != GameImport.Kind.NGAGE2 || job.system.isEmpty()
                || (job.step != PendingGameImport.Step.PLATFORM && job.step != PendingGameImport.Step.INSTALLING))
            throw new IOException("No N-Gage platform is requested");
        if (!platform.isPlatform()) throw new IOException("Select the N-Gage 2.0 platform SIS or its ZIP, not a game");
        pending.verify(job);
        backend.open(catalog.read(job.system));
        if (!backend.hasPlatform()) {
            if(components!=null) {
                if(platforms!=null) platforms.save(platform);
                Result needed=prepareComponents(job,platform.file);
                if(needed!=null) return needed;
            }
            pending.update(job, job.system, PendingGameImport.Step.INSTALLING);
            backend.install(platform.file, platform.kind);
            if (!backend.hasPlatform()) throw new IOException("This installer did not add N-Gage 2.0. The game is still waiting.");
        }
        if (platforms != null) platforms.save(platform);
        return resume(null, true);
    }
}
