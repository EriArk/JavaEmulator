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
                } else if (card.engine.equals("arduboy") || card.engine.equals("gamebuino")) {
                    int edition = card.engine.equals("arduboy") ? 25 : 26;
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
        if (ru.playsoftware.j2meloader.BuildConfig.GIZMONDO_PROBE) {
            File card = new File(work, "wrapped.zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(card))) {
                zip.putNextEntry(new ZipEntry("card/game.exe"));
                zip.write(pe.array());
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("card/GZGA200036/GZGA200036"));
                zip.closeEntry();
            }
            assertEquals(Collections.singleton(23), HandheldFormat.detect(c,Uri.fromFile(card),card.getName()));
            assertEquals(23, HandheldFormat.indexedPlatform("pending_23_abcd"));
            assertEquals(24, HandheldFormat.indexedPlatform("pending_24_abcd"));
        }
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

    @Test public void arduboyFxKeepsFlashAndIdentityAcrossZipImports() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.ARDUBOY_PROBE);
        File fx = new File(work, "Dazzle.arduboy");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(fx))) {
            for (String[] entry : new String[][]{
                    {"info.json", "{\"title\":\"FX fixture\",\"binaries\":[{\"filename\":\"game.hex\",\"flashdata\":\"data.bin\"}]}"},
                    {"game.hex", ":0100000001FE\n:00000001FF\n"}, {"data.bin", "flash fixture"}}) {
                out.putNextEntry(new ZipEntry(entry[0]));
                out.write(entry[1].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.closeEntry();
            }
        }
        checkZipIdentity(fx, 25);
        File renamed = new File(work, "Dazzle.zip");
        Files.copy(fx.toPath(), renamed.toPath());
        Context target = isolated(new File(work, "renamed"));
        ExternalLibrary.Item card = ExternalLibrary.packageProvider(25).install(target, Uri.fromFile(renamed));
        assertEquals("FX fixture", card.getTitle());
        assertEquals("Arduboy FX", card.getVersion());
        assertEquals("arduboy/" + SourceIdentity.sha256(fx), card.getPath());
        assertEquals(Collections.singleton(25), HandheldFormat.detect(c, Uri.fromFile(renamed), renamed.getName()));
    }

    @Test public void classicHexSharesChooserAndKeepsPackageArt() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.GAMEBUINO_PROBE &&
                ru.playsoftware.j2meloader.BuildConfig.ARDUBOY_PROBE);
        File hex = new File(work, "test.hex");
        Files.write(hex.toPath(), ":0100000001FE\n:00000001FF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Set<Integer> both = new HashSet<>(Arrays.asList(25, 26));
        assertEquals(both, HandheldFormat.detect(c, Uri.fromFile(hex), hex.getName()));
        File zip = new File(work, "classic.zip");
        byte[] inf = new byte[81];
        System.arraycopy("gbinf".getBytes("US-ASCII"), 0, inf, 0, 5);
        inf[6] = 1;
        System.arraycopy("Pocket".getBytes("US-ASCII"), 0, inf, 7, 6);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("test.hex")); Files.copy(hex.toPath(), out);
            out.putNextEntry(new ZipEntry("test.inf")); out.write(inf);
            out.putNextEntry(new ZipEntry("disk.img")); out.write(new byte[512]);
            out.putNextEntry(new ZipEntry("info.json")); out.write("{}".getBytes("US-ASCII"));
        }
        assertEquals(both, HandheldFormat.detect(c, Uri.fromFile(zip), zip.getName()));
        File dest = new File(work, "classic");
        Context isolated = isolated(dest);
        ExternalLibrary.Item item = ExternalLibrary.packageProvider(26).install(isolated, Uri.fromFile(zip));
        assertEquals("Pocket", item.getTitle());
        String id = SourceIdentity.sha256(hex);
        assertEquals("gamebuino/" + id, item.getPath());
        File dir = new File(dest, "gamebuino/packages/" + id);
        assertTrue(new File(dir, "icon.png").length() > 0);
        assertEquals(512, new File(dir, "initial-sd.img").length());
        assertEquals(item.getPath(), ExternalLibrary.packageProvider(26).install(isolated, Uri.fromFile(hex)).getPath());
    }

    @Test public void thumbyRawAndNestedPackagesShareIdentityAndFlatHelpersArePreserved() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.THUMBY_PROBE);
        Context sandbox = isolated(new File(work, "thumby-library"));
        File raw = new File(work, "Puzzle.py");
        byte[] program = "import thumby\n".getBytes("UTF-8");
        Files.write(raw.toPath(), program);
        ExternalLibrary.PackageProvider provider = ExternalLibrary.packageProvider(30);
        String identity = provider.install(sandbox, Uri.fromFile(raw)).getPath();
        File game = new File(work, "game.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(game))) {
            out.putNextEntry(new ZipEntry("archive/Games/Puzzle/Puzzle.py"));
            out.write(program);
        }
        assertEquals(Collections.singleton(30), HandheldFormat.detect(c, Uri.fromFile(game), game.getName()));
        assertEquals(identity, provider.install(sandbox, Uri.fromFile(game)).getPath());
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(game))) {
            out.putNextEntry(new ZipEntry("Puzzle.py"));
            out.write("import helper\nimport thumby\n".getBytes("UTF-8"));
            out.putNextEntry(new ZipEntry("helper.py"));
            out.write("value = 1".getBytes("UTF-8"));
        }
        try (ZipFile zip = new ZipFile(game)) {
            ThumbyPackage parsed = ThumbyPackage.read(zip);
            assertEquals("Puzzle.py", parsed.main);
            assertEquals(2, parsed.files.size());
        }
        assertNotEquals(identity, provider.install(sandbox, Uri.fromFile(game)).getPath());
    }

    @Test public void espboyPackedAndRawGamesShareAProgramAndPreserveIcon() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.ESPBOY_PROBE);
        Context sandbox = isolated(new File(work, "espboy-library"));
        ExternalLibrary.PackageProvider provider = ExternalLibrary.packageProvider(32);
        File raw = new File(work, "Demo.bin"), archive = new File(work, "Demo.zip");
        Files.write(raw.toPath(), new byte[]{1, 2, 1, 2, 1, 2});
        String card = provider.install(sandbox, Uri.fromFile(raw)).getPath();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            out.putNextEntry(new ZipEntry("Games/Demo.lge"));
            out.write(new byte[]{'l','g','e',3,(byte)197});
            out.write(new byte[192]);
            out.write(new byte[]{0,2,1,2,(byte)136,2});
        }
        assertEquals(Collections.singleton(32), HandheldFormat.detect(c, Uri.fromFile(archive), archive.getName()));
        assertEquals(card, provider.install(sandbox, Uri.fromFile(archive)).getPath());
        File[] cards=new File(StoragePaths.library(sandbox),"espboy/packages").listFiles();
        assertNotNull(cards);
        assertEquals(1,cards.length);
        assertTrue(new File(cards[0],"icon.png").length()>0);
        File installed=new File(cards[0],"program.bin");
        assertEquals(Collections.singleton(32), HandheldFormat.detect(c, Uri.fromFile(installed), installed.getName()));
        assertEquals(card,provider.install(sandbox,Uri.fromFile(installed)).getPath());
    }

    @Test public void colorImportKeepsGameFolderAndRoutesEngineHelpers() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.THUMBY_COLOR_PROBE);
        Context sandbox = isolated(new File(work, "color-library"));
        File raw = new File(work, "Puzzle.py");
        byte[] program = "import math, engine_io\n".getBytes("UTF-8");
        Files.write(raw.toPath(), program);
        assertEquals(Collections.singleton(31), HandheldFormat.detect(c, Uri.fromFile(raw), raw.getName()));
        File archive = new File(work, "Puzzle.zip");
        ExternalLibrary.PackageProvider provider = ExternalLibrary.packageProvider(31);
        String identity = null;
        for (String prefix : Arrays.asList("", "Arcade/Puzzle/")) {
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
                out.putNextEntry(new ZipEntry(prefix + "main.py"));
                out.write("import helper\n".getBytes("UTF-8"));
                out.putNextEntry(new ZipEntry(prefix + "helper.py"));
                out.write(program);
            }
            assertEquals(Collections.singleton(31), HandheldFormat.detect(c, Uri.fromFile(archive), archive.getName()));
            String installed = provider.install(sandbox, Uri.fromFile(archive)).getPath();
            if (identity != null) assertEquals(identity, installed);
            identity = installed;
        }
    }

    @Test public void epochRecognizesRenamedFirmwareAndCartridges() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.GAMEKING_PROBE);
        File library = new File(work, "epoch-library");
        Context sandbox = isolated(library);
        byte[] firmware = new byte[4096];
        byte[] vector = {0, 0x48, 0x24, (byte) 0xcf, 0x54, 0x0c, 0x40};
        System.arraycopy(vector, 0, firmware, 0, vector.length);
        File bios = new File(work, "renamed.rom");
        Files.write(bios.toPath(), firmware);
        assertEquals(Collections.singleton(29), HandheldFormat.detect(c, Uri.fromFile(bios), bios.getName()));
        ExternalLibrary.packageProvider(29).install(sandbox, Uri.fromFile(bios));
        File system = new File(library, "epoch/system/gamepock/egpcboot.bin");
        assertArrayEquals(firmware, Files.readAllBytes(system.toPath()));

        byte[] cartridge = new byte[8192];
        cartridge[0] = 0x55;
        cartridge[1] = 18;
        cartridge[2] = 0x40;
        File raw = new File(work, "cart.bin");
        Files.write(raw.toPath(), cartridge);
        File zip = new File(work, "cart.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("nested/cart.bin"));
            out.write(cartridge);
        }
        assertEquals(Collections.singleton(29), HandheldFormat.detect(c, Uri.fromFile(zip), zip.getName()));
        ExternalLibrary.Item item = ExternalLibrary.packageProvider(29).install(sandbox, Uri.fromFile(zip));
        assertEquals(item.getPath(), ExternalLibrary.packageProvider(29).install(sandbox, Uri.fromFile(raw)).getPath());
        assertArrayEquals(firmware, Files.readAllBytes(system.toPath()));
    }

    @Test public void hartungRoutesCartridgesAndInstallsFirmwareSeparately() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.GAMEKING_PROBE);
        File library = new File(work, "game-master");
        Context sandbox = isolated(library);
        byte[] rom = new byte[8192];
        rom[0] = (byte) 0xaa;
        File game = new File(work, "Demo.bin");
        Files.write(game.toPath(), rom);
        assertEquals(Collections.singleton(28), HandheldFormat.detect(c, Uri.fromFile(game), game.getName()));
        ExternalLibrary.Item cart = ExternalLibrary.packageProvider(28).install(sandbox, Uri.fromFile(game));
        File renamed = new File(work, "Demo.rom");
        Files.copy(game.toPath(), renamed.toPath());
        assertEquals(Collections.singleton(28), HandheldFormat.detect(c, Uri.fromFile(renamed), renamed.getName()));
        assertEquals(cart.getPath(), ExternalLibrary.packageProvider(28).install(sandbox, Uri.fromFile(renamed)).getPath());
        File zip = new File(work, "Firmware.zip");
        byte[] bios = new byte[4096];
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("ROM/d78c11agf_e19.u1"));
            out.write(bios);
        }
        assertEquals(Collections.singleton(28), HandheldFormat.detect(c, Uri.fromFile(zip), zip.getName()));
        ExternalLibrary.Item firmware = ExternalLibrary.packageProvider(28).install(sandbox, Uri.fromFile(zip));
        assertNotEquals(cart.getPath(), firmware.getPath());
        assertArrayEquals(bios, Files.readAllBytes(new File(library, "hartung/system/gmaster/d78c11agf_e19.u1").toPath()));
        assertEquals(cart.getPath(), ExternalLibrary.packageProvider(28).install(sandbox, Uri.fromFile(game)).getPath());
    }

    @Test public void pokittoAddsSdCompanionsOnReimportAndRecognizesTheProgram() throws Exception {
        Assume.assumeTrue(ru.playsoftware.j2meloader.BuildConfig.POKITTO_PROBE);
        File game = new File(work, "Town.bin");
        byte[] bytes = new byte[32];
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x10008000).putInt(17);
        Files.write(game.toPath(), bytes);
        assertEquals(Collections.singleton(27), HandheldFormat.detect(c, Uri.fromFile(game), game.getName()));
        File destination = new File(work, "pokitto");
        Context sandbox = isolated(destination);
        ExternalLibrary.Item loose = ExternalLibrary.packageProvider(27).install(sandbox, Uri.fromFile(game));
        File bundle = new File(work, "Town.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(bundle))) {
            out.putNextEntry(new ZipEntry("Town/Town.bin")); out.write(bytes);
            out.putNextEntry(new ZipEntry("Town/music/track.bin")); out.write(new byte[]{1, 2, 3});
        }
        assertEquals(Collections.singleton(27), HandheldFormat.detect(c, Uri.fromFile(bundle), bundle.getName()));
        ExternalLibrary.Item complete = ExternalLibrary.packageProvider(27).install(sandbox, Uri.fromFile(bundle));
        assertEquals(loose.getPath(), complete.getPath());
        File pkg = new File(destination, "pokitto/packages/" + SourceIdentity.sha256(game));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(new File(pkg, "sd-files/music/track.bin").toPath()));
        assertEquals(64, new File(pkg, "sd-version").length());
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
        if (ru.playsoftware.j2meloader.BuildConfig.GIZMONDO_PROBE && new File(source,"gizmondo/packages").isDirectory()) ids.add(23);
        if (ru.playsoftware.j2meloader.BuildConfig.POCKETPC_PROBE && new File(source,"pocketpc/packages").isDirectory()) ids.add(22);
        if (ru.playsoftware.j2meloader.BuildConfig.PORTFOLIO_PROBE && new File(source,"portfolio/packages").isDirectory()) ids.add(21);
        if (ru.playsoftware.j2meloader.BuildConfig.ARDUBOY_PROBE && new File(source,"arduboy/packages").isDirectory()) ids.add(25);
        if (ru.playsoftware.j2meloader.BuildConfig.GAMEBUINO_PROBE && new File(source,"gamebuino/packages").isDirectory()) ids.add(26);
        if (ru.playsoftware.j2meloader.BuildConfig.POKITTO_PROBE && new File(source,"pokitto/packages").isDirectory()) ids.add(27);
        if (ru.playsoftware.j2meloader.BuildConfig.THUMBY_PROBE && new File(source,"thumby/packages").isDirectory()) ids.add(30);
        if (ru.playsoftware.j2meloader.BuildConfig.ESPBOY_PROBE && new File(source,"espboy/packages").isDirectory()) ids.add(32);
        if (ru.playsoftware.j2meloader.BuildConfig.THUMBY_COLOR_PROBE && new File(source,"thumby_color/packages").isDirectory()) ids.add(31);
        if (ru.playsoftware.j2meloader.BuildConfig.GAMEKING_PROBE && new File(source,"hartung/packages").isDirectory()) ids.add(28);
        if (ru.playsoftware.j2meloader.BuildConfig.GAMEKING_PROBE && new File(source,"epoch/packages").isDirectory()) ids.add(29);
        if (ru.playsoftware.j2meloader.BuildConfig.IPHONE_PROBE && new File(source,"iphone/packages").isDirectory()) ids.add(24);
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
