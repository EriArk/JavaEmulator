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
            throw new IOException("These installers have circular or excessive dependencies. No installation was started.");
        Map<Long,SisMetadata> bundled=new LinkedHashMap<>(); collect(pkg,bundled);
        Map<Long,SisMetadata.Need> requirements=new LinkedHashMap<>();
        for(SisMetadata child:bundled.values()) for(SisMetadata.Need need:child.needs) {
            SisMetadata.Need previous=requirements.get(need.uid);
            if(previous==null) requirements.put(need.uid,need);
            else {
                SisMetadata.Version from=previous.from.compareTo(need.from)>0 ? previous.from : need.from;
                SisMetadata.Version to=previous.to==null ? need.to : need.to==null ? previous.to
                        : previous.to.compareTo(need.to)<0 ? previous.to : need.to;
                if(to!=null && from.compareTo(to)>0) throw new IOException("Conflicting component versions for "+need.name+". No installation was started.");
                requirements.put(need.uid,new SisMetadata.Need(need.uid,need.name,from,to));
            }
        }
        for(SisMetadata.Need need:requirements.values()) {
            SisMetadata provided=bundled.get(need.uid);
            if(provided!=null && provided.installType==0 && need.accepts(provided.version)) continue;
            SisMetadata.Version installed=backend.installedVersion(need.uid);
            if(need.accepts(installed)) continue;
            if(installed!=null && need.to!=null && installed.compareTo(need.to)>0)
                throw new IOException(need.name+" needs an older version. Choose another phone for this game; existing components were not downgraded.");
            ComponentStore.Item selected=chosen.get(need.uid);
            if(selected!=null) {
                if(visiting.contains(need.uid)) throw new IOException("Circular component dependency: "+need.name);
                if(!need.accepts(selected.metadata.version)) throw new IOException("Conflicting component versions for "+need.name+". No installation was started.");
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
    private static void collect(SisMetadata pkg,Map<Long,SisMetadata> result) throws IOException {
        if(result.put(pkg.uid,pkg)!=null) throw new IOException("Duplicate embedded component UID");
        for(SisMetadata child:pkg.embedded) collect(child,result);
    }
}
