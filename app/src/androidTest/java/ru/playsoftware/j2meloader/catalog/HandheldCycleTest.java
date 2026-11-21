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
    @Test public void indexedAndInstalledDocumentCompareByProviderAndId() {
        String indexed = "content://test.documents/tree/primary%3AROMs/document/primary%3AROMs%2FGame%20(1991).zip";
        String direct = "content://test.documents/document/primary%3AROMs%2FGame%20%281991%29.zip";
        assertEquals(SourceIdentity.libraryKey(indexed), SourceIdentity.libraryKey(direct));
        assertNotEquals(SourceIdentity.libraryKey(indexed), SourceIdentity.libraryKey(direct.replace("1991","1992")));
        assertNotEquals(SourceIdentity.libraryKey(indexed), SourceIdentity.libraryKey(direct.replace("test.documents","other.documents")));
        assertNotEquals(SourceIdentity.libraryKey(indexed), SourceIdentity.libraryKey(direct + "?variant=2"));
    }
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
        Set<Integer> cardIds = new HashSet<>();
        Context isolated=isolated(dest);
        for (int id : expectedPlatforms()) {
            if (id == 7) continue; // Dingoo and Gemei share their package provider.
            for (AppItem item : ExternalLibrary.packageProvider(id).read(isolated)) {
                platforms.add(((ExternalLibrary.Item)item).platformId());
                assertTrue("Duplicate library ID for " + item.getTitle(), cardIds.add(item.getId()));
            }
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
                Set<Integer> matches = HandheldFormat.detect(c, Uri.fromFile(file), name);
                if (card.engine.equals("kaios") || card.engine.equals("firefox")) {
                    int edition = card.engine.equals("kaios") ? B2gManifest.KAIOS : B2gManifest.FIREFOX;
                    assertTrue(matches.contains(edition));
                    detected.add(edition);
                } else detected.addAll(matches);
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

    @Test public void pocketPcRoutesArmCeSeparatelyFromDesktopAndDos() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.POCKETPC_PROBE);
        java.nio.ByteBuffer pe=java.nio.ByteBuffer.allocate(512).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        pe.putShort(0,(short)0x5a4d).putShort(4,(short)1).putInt(60,128);
        pe.putInt(128,0x4550).putShort(132,(short)0x1c0).putShort(152,(short)0x10b).putShort(220,(short)9);
        File exe=new File(work,"game.exe");
        Files.write(exe.toPath(),pe.array());
        assertEquals(Collections.singleton(22),HandheldFormat.detect(c,Uri.fromFile(exe),exe.getName()));
        assertEquals(22,HandheldFormat.indexedPlatform("pending_22_abcd"));
        pe.putShort(132,(short)0x14c).putShort(220,(short)2);
        Files.write(exe.toPath(),pe.array());
        assertTrue(HandheldFormat.detect(c,Uri.fromFile(exe),exe.getName()).isEmpty());
        File cab=new File(work,"game.cab");
        Files.write(cab.toPath(),new byte[]{0,1});
        assertTrue(HandheldFormat.detect(c,Uri.fromFile(cab),cab.getName()).isEmpty());
    }

    @Test public void mreZipUsesSameInstalledIdentityAsLooseGame() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.MRE_PROBE);
        File game = new File(c.getExternalFilesDir(null), "mre-check/FlappyBird.vxp");
        checkZipIdentity(game, 17);
    }

    @Test public void webOsZipUsesSameInstalledIdentityAsLooseGame() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.WEBOS_PROBE);
        checkZipIdentity(new File(c.getExternalFilesDir(null), "webos-check/2048.ipk"), 18);
    }

    private void checkZipIdentity(File game, int platform) throws Exception {
        Assume.assumeTrue(game.isFile());
        File zip = new File(work, "collection.zip");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("Games/" + game.getName()));
            Files.copy(game.toPath(), out);
            out.closeEntry();
        }
        assertEquals(Collections.singleton(platform), HandheldFormat.detect(c, Uri.fromFile(zip), zip.getName()));
        Context target = isolated(new File(work, "library"));
        AppItem loose = ExternalLibrary.packageProvider(platform).install(target, Uri.fromFile(game));
        AppItem zipped = ExternalLibrary.packageProvider(platform).install(target, Uri.fromFile(zip));
        assertEquals(loose.getPath(), zipped.getPath());
        assertEquals(1, ExternalLibrary.packageProvider(platform).read(target).size());
        assertEquals(platform, HandheldFormat.indexedPlatform("pending_" + platform + "_abcd"));
    }

    @Test public void packagedPhoneGameRequiresLocalEntryAndPlatformManifest() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.B2G_PROBE);
        assertTrue(phoneManifest("manifest.webmanifest", "{'name':'Generic','start_url':'/index.html'}").isEmpty());
        assertTrue(phoneManifest("manifest.webapp", "{'name':'Remote','launch_path':'https://example.org/'}").isEmpty());
        assertTrue(phoneManifest("manifest.webapp", "{'name':'Missing','launch_path':'/missing.html'}").isEmpty());
        assertEquals(new HashSet<>(Arrays.asList(19, 20)), phoneManifest("manifest.webapp",
                "{'name':'Phone','launch_path':'/index.html?play=1#board'}"));
        assertEquals(Collections.singleton(20), phoneManifest("manifest.webmanifest",
                "{'name':'Phone','start_url':'/','b2g_features':{'version':'1.0'}}"));
        assertEquals(20, HandheldFormat.indexedPlatform("pending_20_abcd"));
    }

    private Set<Integer> phoneManifest(String manifest, String json) throws IOException {
        File file = new File(work, "phone.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry("game/" + manifest));
            out.write(json.replace((char)39, (char)34).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("game/index.html"));
            out.closeEntry();
        }
        return HandheldFormat.detect(c, Uri.fromFile(file), file.getName());
    }

    private Set<Integer> expectedPlatforms() {
        Set<Integer> ids = new HashSet<>(Arrays.asList(5, 6, 7, 8, 9));
        if (ru.playsoftware.j2meloader.BuildConfig.SUPERVISION_PROBE &&
                new File(source, "supervision/packages").isDirectory()) ids.add(10);
        if (ru.playsoftware.j2meloader.BuildConfig.MEGADUCK_PROBE &&
                new File(source, "megaduck/packages").isDirectory()) ids.add(11);
        if (ru.playsoftware.j2meloader.BuildConfig.CYBIKO_PROBE &&
                new File(source, "cybiko/packages").isDirectory()) ids.add(12);
        if (ru.playsoftware.j2meloader.BuildConfig.FLASH_PROBE && new File(source,"flash/packages").isDirectory()) ids.add(13);
        if (ru.playsoftware.j2meloader.BuildConfig.GAMATE_PROBE && new File(source,"gamate/packages").isDirectory()) ids.add(14);
        if (ru.playsoftware.j2meloader.BuildConfig.GAMEKING_PROBE && new File(source,"gameking/packages").isDirectory()) ids.add(15);
        if (ru.playsoftware.j2meloader.BuildConfig.PALM_PROBE && new File(source,"palm/packages").isDirectory()) ids.add(16);
        if (ru.playsoftware.j2meloader.BuildConfig.MRE_PROBE && new File(source,"mre/packages").isDirectory()) ids.add(17);
        if (ru.playsoftware.j2meloader.BuildConfig.WEBOS_PROBE && new File(source,"webos/packages").isDirectory()) ids.add(18);
        if (ru.playsoftware.j2meloader.BuildConfig.B2G_PROBE) {
            if (new File(source,"firefox/packages").isDirectory()) ids.add(19);
            if (new File(source,"kaios/packages").isDirectory()) ids.add(20);
        }
        if (ru.playsoftware.j2meloader.BuildConfig.POCKETPC_PROBE && new File(source,"pocketpc/packages").isDirectory()) ids.add(22);
        if (ru.playsoftware.j2meloader.BuildConfig.PORTFOLIO_PROBE && new File(source,"portfolio/packages").isDirectory()) ids.add(21);
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
