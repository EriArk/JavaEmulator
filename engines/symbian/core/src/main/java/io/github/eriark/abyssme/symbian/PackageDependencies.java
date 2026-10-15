package io.github.eriark.abyssme.symbian;

import java.io.*;

/** Bounded reader for the core's size-prefixed controller bodies, not complete SIS files. */
public final class PackageDependencies {
    public static boolean needs(String path,long owner,long dependency) throws IOException {
        SisMetadata metadata=SisMetadata.storedController(new File(path));
        if(metadata.uid!=owner) throw new IOException("Stored package identity does not match its registry");
        return needs(metadata,dependency);
    }
    private static boolean needs(SisMetadata metadata,long dependency) {
        for(SisMetadata.Need need:metadata.needs) if(need.uid==dependency) return true;
        for(SisMetadata child:metadata.embedded) if(needs(child,dependency)) return true;
        return false;
    }
}
