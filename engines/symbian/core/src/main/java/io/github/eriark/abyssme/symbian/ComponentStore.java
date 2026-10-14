package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Immutable, user-supplied SIS installers; never a copy of a guest drive. */
final class ComponentStore {
    private static final long FILE_LIMIT=256L*1024*1024, TOTAL_LIMIT=1024L*1024*1024;
    private final File root;
    ComponentStore(File root) { this.root=root; }
    static final class Item {
        final File file; final SisMetadata metadata; final String hash;
        Item(File file,SisMetadata metadata,String hash) { this.file=file; this.metadata=metadata; this.hash=hash; }
        void verify() throws IOException {
            if(!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS) || !hash.equals(SystemFileCatalog.hash(file)))
                throw new IOException("Saved component changed. Add its installer again.");
        }
    }
    SisMetadata save(GameImport input, SisMetadata.Need requested) throws IOException {
        if(input.kind!=GameImport.Kind.SIS_MODERN) throw new IOException("Select a Symbian SIS/SISX component installer or its ZIP.");
        SisMetadata metadata=SisMetadata.read(input.file);
        if(metadata.installType!=0) throw new IOException("Select the full component installer, not an update or patch.");
        if(requested!=null && (metadata.uid!=requested.uid || !requested.accepts(metadata.version)))
            throw new IOException("This installer does not provide "+requested.description()+". Your game is still waiting.");
        if(input.file.length()>FILE_LIMIT) throw new IOException("Component installer exceeds 256 MB");
        Files.createDirectories(root.toPath());
        File[] interrupted=root.listFiles(f->f.getName().startsWith("component-") && f.getName().endsWith(".pending"));
        if(interrupted!=null) for(File file:interrupted) Files.deleteIfExists(file.toPath());
        File target=new File(root,input.hash+".sis");
        if(Files.isRegularFile(target.toPath(),LinkOption.NOFOLLOW_LINKS) && input.hash.equals(SystemFileCatalog.hash(target))) return metadata;
        long total=input.file.length(); int count=0;
        for(File file:files()) { total+=file.length(); count++; }
        if(total>TOTAL_LIMIT || count>=128) throw new IOException("Saved components are full. Remove an unused saved installer first.");
        File temporary=File.createTempFile("component-",".pending",root);
        try {
            try(InputStream in=new FileInputStream(input.file); FileOutputStream out=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[65536]; long size=0;
                for(int n;(n=in.read(buffer))!=-1;) {
                    if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                    if((size+=n)>FILE_LIMIT) throw new IOException("Component installer exceeds 256 MB");
                    out.write(buffer,0,n);
                }
                out.getFD().sync();
            }
            if(!input.hash.equals(SystemFileCatalog.hash(temporary))) throw new IOException("Installer changed while copying");
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary.toPath()); }
        return metadata;
    }
    private File[] files() {
        File[] files=root.listFiles(f -> f.getName().matches("[0-9a-f]{64}\\.sis") && Files.isRegularFile(f.toPath(),LinkOption.NOFOLLOW_LINKS));
        if(files==null) return new File[0]; Arrays.sort(files,Comparator.comparing(File::getName)); return files;
    }
    List<Item> list() {
        List<Item> result=new ArrayList<>();
        for(File file:files()) {
            if(file.length()>FILE_LIMIT || result.size()>=128) continue;
            try {
                String hash=file.getName().substring(0,64);
                if(!hash.equals(SystemFileCatalog.hash(file))) continue;
                SisMetadata metadata=SisMetadata.read(file);
                if(metadata.installType==0) result.add(new Item(file,metadata,hash));
            } catch(IOException e) { android.util.Log.w("AbyssSymbian","Saved component unavailable: "+file.getName(),e); }
        }
        return result;
    }
    void remove(String hash) throws IOException {
        if(!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid component");
        Files.deleteIfExists(new File(root,hash+".sis").toPath());
    }
    void clear() throws IOException {
        for(File file:files()) Files.deleteIfExists(file.toPath());
    }
}
