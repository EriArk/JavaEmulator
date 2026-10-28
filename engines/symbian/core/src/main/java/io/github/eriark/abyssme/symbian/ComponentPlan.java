package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.util.*;

/** Resolve the whole dependency graph before making any guest changes. */
final class ComponentPlan {
    static final class Missing extends IOException {
        final SisMetadata.Need need;
        Missing(SisMetadata.Need need) { super(need.description()); this.need=need; }
    }
    final List<ComponentStore.Item> installs=new ArrayList<>();
    private final List<ComponentStore.Item> available;
    private final GameInstallFlow.Backend backend;
    private final Map<Long,ComponentStore.Item> chosen=new HashMap<>();
    private final Set<Long> visiting=new HashSet<>();
    ComponentPlan(SisMetadata game, ComponentStore store, GameInstallFlow.Backend backend) throws Exception {
        this.available=store.list(); this.backend=backend;
        visit(game);
    }
    private void visit(SisMetadata pkg) throws Exception {
        if(visiting.size()>=32 || chosen.size()>=128 || !visiting.add(pkg.uid))
            throw new SystemFailure(SystemFailure.Code.IMPORT_THESE_INSTALLERS_HAVE_CIRCULAR_OR_EXCESSIVE_DEPENDENCIES_NO_INSTALLATION);
        Map<Long,List<SisMetadata>> bundled=new LinkedHashMap<>(); collect(pkg,pkg.name,bundled);
        Map<Long,SisMetadata.Need> requirements=new LinkedHashMap<>();
        for(List<SisMetadata> group:bundled.values()) for(SisMetadata child:group) for(SisMetadata.Need need:child.needs) {
            SisMetadata.Need previous=requirements.get(need.uid);
            if(previous==null) requirements.put(need.uid,need);
            else {
                SisMetadata.Version from=previous.from.compareTo(need.from)>0 ? previous.from : need.from;
                SisMetadata.Version to=previous.to==null ? need.to : need.to==null ? previous.to
                        : previous.to.compareTo(need.to)<0 ? previous.to : need.to;
                if(to!=null && from.compareTo(to)>0) throw new SystemFailure(SystemFailure.Code.COMPONENT_VERSION_CONFLICT, need.name);
                requirements.put(need.uid,new SisMetadata.Need(need.uid,need.name,from,to));
            }
        }
        for(SisMetadata.Need need:requirements.values()) {
            List<SisMetadata> provided=bundled.get(need.uid);
            if(provided!=null && provided.stream().anyMatch(p->p.installType==0 && need.accepts(p.version))) continue;
            SisMetadata.Version installed=backend.installedVersion(need.uid);
            if(need.accepts(installed)) continue;
            if(installed!=null && need.to!=null && installed.compareTo(need.to)>0)
                throw new SystemFailure(SystemFailure.Code.COMPONENT_OLDER_REQUIRED, need.name);
            ComponentStore.Item selected=chosen.get(need.uid);
            if(selected!=null) {
                if(visiting.contains(need.uid)) throw new SystemFailure(SystemFailure.Code.COMPONENT_CYCLE, need.name);
                if(!need.accepts(selected.metadata.version)) throw new SystemFailure(SystemFailure.Code.COMPONENT_VERSION_CONFLICT, need.name);
                continue;
            }
            for(ComponentStore.Item candidate:available) {
                if(candidate.metadata.uid==need.uid && need.accepts(candidate.metadata.version)
                        && (selected==null || candidate.metadata.version.compareTo(selected.metadata.version)>0)) selected=candidate;
            }
            if(selected==null) throw new Missing(need);
            chosen.put(need.uid,selected); visit(selected.metadata); installs.add(selected);
        }
        visiting.remove(pkg.uid);
    }
    private static void collect(SisMetadata pkg,String installer,Map<Long,List<SisMetadata>> result) throws IOException {
        List<SisMetadata> group=result.computeIfAbsent(pkg.uid,uid->new ArrayList<>());
        for(SisMetadata previous:group) {
            // Partial updates share the base UID. Keep every controller and its requirements;
            // never substitute an update's version for a complete dependency provider.
            boolean update=(pkg.installType==2 && (previous.installType==0 || previous.installType==2))
                    || (previous.installType==2 && pkg.installType==0);
            if(!update || Arrays.equals(previous.controllerDigest,pkg.controllerDigest))
                throw new SystemFailure(SystemFailure.Code.COMPONENT_BUNDLE_CONFLICT,installer,
                        String.format(Locale.ROOT,"%08X",pkg.uid),
                        previous.name+" "+previous.version+" ["+previous.installType+"]",
                        pkg.name+" "+pkg.version+" ["+pkg.installType+"]");
        }
        group.add(pkg);
        for(SisMetadata child:pkg.embedded) collect(child,installer,result);
    }
}
