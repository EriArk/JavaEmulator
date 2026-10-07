package ru.playsoftware.j2meloader.catalog;

import android.content.Context;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibraryImporterTest {
	private Context context;
	private File source, destination;
	private AppDatabase db;
	private LibraryImporter importer;
	private LibraryImporter.Catalog catalog;
	private final AtomicBoolean cancel = new AtomicBoolean();

	@Before public void setup() throws Exception {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		source = Files.createTempDirectory(context.getCacheDir().toPath(), "source-").toFile();
		destination = Files.createTempDirectory(context.getCacheDir().toPath(), "destination-").toFile();
		db = AppDatabase.open(context, destination.getPath());
		catalog = new LibraryImporter.Catalog() {
			public AppItem bySource(String key) { return db.appItemDao().getBySourceKey(key); }
			public AppItem byPath(String path) { return db.appItemDao().getByPath(path); }
			public AppItem byTitle(String title, String vendor) { return db.appItemDao().get(title, vendor); }
			public void insert(AppItem item) { db.appItemDao().insertImported(item); }
		};
		importer = new LibraryImporter(context, destination, catalog, cancel);
		File app = new File(source, "converted/old-folder"); assertTrue(app.mkdirs());
		try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("SudokuJ2ME-0.1.jar")) {
			Files.copy(in, new File(app, "res.jar").toPath());
		}
		try (ZipFile jar = new ZipFile(new File(app, "res.jar"));
			 InputStream in = jar.getInputStream(jar.getEntry("META-INF/MANIFEST.MF"))) {
			Files.copy(in, new File(app, "converted.dex.conf").toPath());
		}
		// Deliberately invalid old DEX: importing must recompile res.jar instead of copying it.
		write(source, "converted/old-folder/converted.dex", "not executable");
		write(source, "data/old-folder/sudoku.rms", "saved progress");
		write(source, "data/old-folder/private/file.txt", "private data");
		write(source, "configs/old-folder/config.json", "{\"Version\":3,\"ScreenWidth\":176,\"ScreenHeight\":220,"
				+ "\"ScreenScaleRatio\":100,\"KeyMappings\":{\"96\":53},\"ShowKeyboard\":true,"
				+ "\"SystemProperties\":\"microedition.platform: Nokia\\nfileconn.dir.photos: file:///old/path\\n\"}");
	}

	@After public void close() { if (db != null) db.close(); }

	private static void write(File root, String path, String value) throws Exception {
		File file = new File(root, path); file.getParentFile().mkdirs();
		Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
	}
	private LibraryImporter.Entry entry() throws Exception {
		return importer.scan(DocumentFile.fromFile(source)).entries.get(0);
	}

	@Test public void copiesSavesAndSettingsAndRecompilesWithoutSourceWrites() throws Exception {
		LibraryImporter.Entry entry = entry();
		String original = SourceIdentity.sha256(new File(source, "configs/old-folder/config.json"));
		assertTrue(entry.selected);
		assertTrue(importer.importEntry(entry).startsWith("Imported"));
		AppItem item = catalog.bySource(entry.sourceKey); assertNotNull(item);
		String id = item.getPath();
		assertEquals("saved progress", new String(Files.readAllBytes(new File(destination, "data/" + id + "/sudoku.rms").toPath()), StandardCharsets.UTF_8));
		assertTrue(new File(destination, "data/" + id + "/private/file.txt").isFile());
		byte[] dex = Files.readAllBytes(new File(destination, "converted/" + id + "/converted.dex").toPath());
		assertEquals("dex\n", new String(dex, 0, 4, StandardCharsets.US_ASCII));
		ProfileModel profile = ProfilesManager.loadConfig(new File(destination, "configs/" + id));
		assertNotNull(profile); assertEquals(176, profile.screenWidth); assertEquals(220, profile.screenHeight);
		assertEquals(53, profile.getActiveKeyMappings().get(96));
		assertFalse(profile.systemProperties.contains("/old/path"));
		assertTrue(profile.systemProperties.contains("Nokia"));
		assertEquals(!BuildConfig.HANDHELD_MODE, profile.showKeyboard);
		assertEquals(original, SourceIdentity.sha256(new File(source, "configs/old-folder/config.json")));
		assertEquals("not executable", new String(Files.readAllBytes(new File(source, "converted/old-folder/converted.dex").toPath()), StandardCharsets.UTF_8));
		assertNotNull(entry().problem);
	}

	@Test public void sameTitleIsSeparateCopyAndExistingProgressIsPreserved() throws Exception {
		LibraryImporter.Entry entry = entry();
		AppItem existing = new AppItem("existing", entry.title, entry.vendor, "another version");
		existing.setFavorite(true); existing.setPlayCount(9); catalog.insert(existing);
		write(destination, "data/existing/save", "keep me");
		entry = entry(); assertTrue(entry.duplicateTitle); assertFalse(entry.selected);
		importer.importEntry(entry);
		assertNotEquals("existing", catalog.bySource(entry.sourceKey).getPath());
		assertTrue(catalog.byPath("existing").isFavorite());
		assertEquals(9, catalog.byPath("existing").getPlayCount());
		assertEquals("keep me", new String(Files.readAllBytes(new File(destination, "data/existing/save").toPath()), StandardCharsets.UTF_8));
	}

	@Test public void cancellationAndCorruptInputDoNotPublish() throws Exception {
		LibraryImporter.Entry entry = entry();
		cancel.set(true);
		try { importer.importEntry(entry); fail(); } catch (CancellationException expected) { }
		cancel.set(false);
		write(source, "converted/old-folder/res.jar", "broken");
		try { importer.importEntry(entry); fail(); } catch (java.io.IOException expected) { }
		assertNull(catalog.bySource(entry.sourceKey));
		assertFalse(new File(destination, "converted").exists());
	}

	@Test public void missingJarIsReportedAndSharedFsIsNotSilentlyImported() throws Exception {
		assertTrue(new File(source, "converted/old-folder/res.jar").delete());
		write(source, "fs/c/shared.txt", "shared");
		LibraryImporter.Preview preview = importer.scan(DocumentFile.fromFile(source));
		assertTrue(preview.sharedFiles);
		assertTrue(preview.entries.get(0).problem.contains("Original JAR required"));
		assertFalse(preview.entries.get(0).selected);
		assertFalse(new File(destination, "fs").exists());
	}

	@Test public void databaseFailureRollsBackOnlyNewFiles() throws Exception {
		LibraryImporter.Entry entry = entry();
		write(destination, "data/existing/save", "keep");
		LibraryImporter failing = new LibraryImporter(context, destination, new LibraryImporter.Catalog() {
			public AppItem bySource(String key) { return null; }
			public AppItem byPath(String path) { return null; }
			public AppItem byTitle(String title, String vendor) { return null; }
			public void insert(AppItem item) { throw new IllegalStateException("disk full"); }
		}, cancel);
		try { failing.importEntry(entry); fail(); } catch (IllegalStateException expected) { }
		assertTrue(new File(destination, "data/existing/save").isFile());
		assertEquals(0, new File(destination, "converted").list().length);
		assertEquals(0, new File(destination, ".library-import").list().length);
	}

	@Test public void recoveryRemovesUncommittedButRetainsCommittedTransactions() throws Exception {
		String pending = "import-00000000-0000-0000-0000-000000000001";
		String committed = "import-00000000-0000-0000-0000-000000000002";
		for (String id : new String[]{pending, committed}) {
			write(destination, ".library-import/" + id + "/pending", "1");
			write(destination, "converted/" + id + "/res.jar", "jar");
			write(destination, "data/" + id + "/save", "save");
		}
		catalog.insert(new AppItem(committed, "Committed", "Vendor", "1"));
		write(destination, "data/untouched/save", "keep");
		LibraryImporter.recover(destination, catalog);
		assertFalse(new File(destination, "converted/" + pending).exists());
		assertFalse(new File(destination, "data/" + pending).exists());
		assertTrue(new File(destination, "converted/" + committed + "/res.jar").isFile());
		assertTrue(new File(destination, "data/" + committed + "/save").isFile());
		assertTrue(new File(destination, "data/untouched/save").isFile());
	}

	@Test public void brokenConfigDoesNotDiscardSavesIntoAPartialInstallation() throws Exception {
		write(source, "configs/old-folder/config.json", "{broken");
		LibraryImporter.Entry entry = entry();
		try { importer.importEntry(entry); fail(); } catch (java.io.IOException expected) { }
		assertNull(catalog.bySource(entry.sourceKey));
		assertTrue(new File(source, "data/old-folder/sudoku.rms").isFile());
	}
}
