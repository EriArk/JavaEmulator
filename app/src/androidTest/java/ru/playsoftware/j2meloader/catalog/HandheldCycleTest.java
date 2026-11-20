/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.junit.*;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.applist.AppItem;
import io.github.eriark.abyssme.storage.StoragePaths;

/** Runs against maintainer-supplied files; never restores into the user's library. */
public class HandheldCycleTest {
    private Context c;
    private File source, work, files;
    private final AtomicBoolean cancel=new AtomicBoolean();
    @Before public void setup() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("handheldCycle")));
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        source=StoragePaths.library(c);
        work=Files.createTempDirectory(c.getCacheDir().toPath(),"handheld-cycle").toFile();
        files=new File(c.getExternalFilesDir(null),"cycle-fixtures");
    }
    @After public void cleanup() throws Exception { if(work!=null)LibraryArchive.remove(work); }
    @Test public void populatedLibraryRoundTrip() throws Exception {
        File zip=new File(work,"backup.zip"),dest=new File(work,"restored");
        List<HandheldBackup> cards=HandheldBackup.list(source);
        assertTrue(cards.size()>5);
        HandheldBackup.migratePieceBase(c,source);
        LibraryArchive archive=new LibraryArchive(cancel);
        try(OutputStream out=new FileOutputStream(zip)){assertEquals(cards.size(),archive.write(source,Collections.emptyList(),out));}
        File stage;
        try(InputStream in=new FileInputStream(zip)){stage=archive.unpack(in,work);}
        LibraryImporter importer=new LibraryImporter(c,dest,null,cancel);
        LibraryImporter.Preview preview=importer.scanBackup(stage);
        assertEquals(cards.size(),preview.entries.size());
        for(LibraryImporter.Entry e:preview.entries) importer.importEntry(e);
        Set<Integer> platforms=new HashSet<>();
        Context isolated=isolated(dest);
        for (int id : expectedPlatforms()) {
            if (id == 7) continue; // Dingoo and Gemei share their package provider.
            for (AppItem item : ExternalLibrary.packageProvider(id).read(isolated))
                platforms.add(((ExternalLibrary.Item)item).platformId());
        }
        assertEquals(expectedPlatforms(), platforms);
        for(HandheldBackup card:cards) {
            for(String rel:new String[]{card.card(),card.data(),card.engine+"/system"}) verify(new File(source,rel),new File(dest,rel));
            if(new File(source,card.config()+"/config.json").exists())assertTrue(new File(dest,card.config()+"/config.json").isFile());
        }
        File newer=new File(dest,"gp32/system/gp32166m.bin"); Files.write(newer.toPath(),new byte[]{7});
        for(LibraryImporter.Entry e:preview.entries) importer.importEntry(e);
        assertArrayEquals(new byte[]{7},Files.readAllBytes(newer.toPath()));
    }
    @Test public void carrierFirmwareAndAmbiguousArchiveRouting() throws Exception {
        Set<Integer> detected = new HashSet<>();
        for (HandheldBackup card : HandheldBackup.list(source)) {
            File dir = new File(source, card.card());
            for (File file : dir.listFiles()) {
                String name = file.getName();
                if (!file.isFile() || !HandheldFormat.candidate(name)) continue;
                detected.addAll(HandheldFormat.detect(c, Uri.fromFile(file), name));
            }
        }
        assertEquals(expectedPlatforms(), detected);
        File mixed = new File(work, "mixed.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(mixed))) {
            for (String name : Arrays.asList("documentation.cpp", "Game.pex", "Game.smc", "Other.jar")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.closeEntry();
            }
        }
        assertEquals(new HashSet<>(Arrays.asList(0, 8, 9)), HandheldFormat.detect(c, Uri.fromFile(mixed), mixed.getName()));
    }

    private Set<Integer> expectedPlatforms() {
        Set<Integer> ids = new HashSet<>(Arrays.asList(5, 6, 7, 8, 9));
        if (ru.playsoftware.j2meloader.BuildConfig.SUPERVISION_PROBE &&
                new File(source, "supervision/packages").isDirectory()) ids.add(10);
        return ids;
    }

    private static void verify(File a,File b) throws Exception {
        if(!a.exists())return;
        if(a.isDirectory()){if(a.list().length>0)assertTrue(b.toString(),b.isDirectory());for(File f:a.listFiles())verify(f,new File(b,f.getName()));}
        else assertEquals(a.toString(),SourceIdentity.sha256(a),SourceIdentity.sha256(b));
    }
    private Context isolated(File dest) {
        return new android.content.ContextWrapper(c) {
            @Override public File getFilesDir(){return dest;}
            @Override public File getExternalFilesDir(String type){return dest;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) {
                return c.getSharedPreferences("handheld-cycle-"+name,mode);
            }
        };
    }
    @Test public void actualUpdaterAndPackagedGames() throws Exception {
        Context target=isolated(work);
        File updater=new File(files,"update120.exe");
        assertEquals(Collections.singleton(8),HandheldFormat.detect(c,Uri.fromFile(updater),updater.getName()));
        ExternalLibrary.Item system=ExternalLibrary.packageProvider(8).install(target,Uri.fromFile(updater));
        String hash=system.getPath().substring(6);
        assertEquals("ddc99cec66679f23a335e65c45c99ff8a621fe4f7c85db5bf378c391c0d58cc2",hash);
        assertEquals(hash,SourceIdentity.sha256(new File(work,"piece/packages/"+hash+"/image.pfi")));
        assertEquals("PEX",ExternalLibrary.packageProvider(8).install(target,Uri.fromFile(new File(files,"pko.zip"))).getVersion());
        for(int id:new int[]{6,7}){
            File bundle=new File(files,"game"+id+".zip");
            assertEquals(Collections.singleton(id),HandheldFormat.detect(c,Uri.fromFile(bundle),"game.zip"));
            ExternalLibrary.Item card=ExternalLibrary.packageProvider(id).install(target,Uri.fromFile(bundle));
            assertEquals(id,card.platformId());
            String key=card.getPath().substring(7);
            File dir=new File(work,"dingoo/packages/"+key+"/content/Dist");
            assertTrue(new File(dir,"assets/levels/level.dat").isFile());
            assertTrue(new File(dir,"empty").isDirectory());
            assertTrue(new File(dir,id==6?"Digger.app":"Seven.c3s").isFile());
            assertEquals(card.getPath(),ExternalLibrary.packageProvider(id).install(target,Uri.fromFile(bundle)).getPath());
            card.removePackage();assertFalse(dir.exists());
        }
    }
}
