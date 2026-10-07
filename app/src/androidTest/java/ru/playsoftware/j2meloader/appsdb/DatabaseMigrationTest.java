package ru.playsoftware.j2meloader.appsdb;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;

import ru.playsoftware.j2meloader.applist.AppItem;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
	private final Context context = ApplicationProvider.getApplicationContext();

	private File directory() throws Exception {
		return Files.createTempDirectory(context.getCacheDir().toPath(), "migration-").toFile();
	}

	private void oldDatabase(File root, String filename, int version, boolean rows) {
		try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(new File(root, filename), null)) {
			db.execSQL("CREATE TABLE apps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, imagePath TEXT, "
					+ (version >= 2 ? "coverPath TEXT, " : "")
					+ "title TEXT, author TEXT, version TEXT, path TEXT)");
			db.execSQL("CREATE UNIQUE INDEX index_apps_path ON apps(path)");
			if (rows) {
				db.execSQL("INSERT INTO apps (id,path,title,author,version,imagePath) VALUES "
						+ "(42,'game','My game','Vendor','1.0','game/icon.png')");
				if (version == 2) db.execSQL("UPDATE apps SET coverPath='game/cover.png'");
			}
			db.setVersion(version);
		}
	}

	@Test public void upgradesHistoricalSchemasWithoutTouchingSaves() throws Exception {
		for (int version = 1; version <= 2; version++) {
			File root = directory();
			File save = new File(root, "data/game/save.rms");
			assertTrue(save.getParentFile().mkdirs());
			Files.write(save.toPath(), new byte[]{4, 2, 9});
			oldDatabase(root, "abyssme.db", version, true);
			try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
				AppItem item = db.appItemDao().get(42);
				assertEquals("My game", item.getTitle());
				assertEquals("Vendor", item.getAuthor());
				assertEquals("1.0", item.getVersion());
				assertEquals("game/icon.png", item.getImagePath());
				assertEquals(version == 2 ? "game/cover.png" : null, item.getCoverPath());
				assertNull(item.getSourceKey());
				assertEquals("ready", item.getPreparationState());
				assertFalse(item.isFavorite());
				assertEquals(0, item.getPlayCount());
			}
			assertArrayEquals(new byte[]{4, 2, 9}, Files.readAllBytes(save.toPath()));
		}
	}

	@Test public void emptyLegacyDatabaseUpgradesInPlace() throws Exception {
		File root = directory();
		oldDatabase(root, "J2ME-apps.db", 1, false);
		try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
			assertNull(db.appItemDao().get(42));
			assertEquals(3, db.getOpenHelper().getWritableDatabase().getVersion());
		}
		assertFalse(new File(root, "abyssme.db").exists());
		assertTrue(new File(root, "J2ME-apps.db").exists());
	}

	@Test public void existingV3RetainsIndexedAndPreparedMetadata() throws Exception {
		File root = directory();
		for (String state : new String[]{"ready", "indexed"}) {
			try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
				AppItem item = new AppItem(state, "Edited title", "Vendor", "2");
				item.setId(state.equals("ready") ? 1 : 2);
				item.setFavorite(true);
				item.setLastPlayedAt(123456);
				item.setPlayCount(7);
				item.setPreparationState(state);
				item.setSourceKey("key:" + state);
				item.setSourceUri("content://games/" + state);
				item.setSourceHash("hash");
				item.setCoverPath(state + "/cover.png");
				db.appItemDao().insert(item);
			}
		}
		oldDatabase(root, "J2ME-apps.db", 1, true);
		try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
			for (int id = 1; id <= 2; id++) {
				AppItem item = db.appItemDao().get(id);
				assertTrue(item.isFavorite());
				assertEquals(7, item.getPlayCount());
				assertEquals(123456, item.getLastPlayedAt());
				assertEquals("Edited title", item.getTitle());
				assertEquals("hash", item.getSourceHash());
				assertNotNull(item.getCoverPath());
				assertEquals("content://games/" + item.getPath(), item.getSourceUri());
				assertEquals(item.getPath(), item.getPreparationState());
			}
			assertNull(db.appItemDao().get(42));
		}
		try (SQLiteDatabase old = SQLiteDatabase.openDatabase(new File(root, "J2ME-apps.db").getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
			assertEquals(1, old.getVersion());
		}
	}

	@Test public void unknownVersionFailsWithoutDestroyingCatalog() throws Exception {
		File root = directory();
		oldDatabase(root, "abyssme.db", 1, true);
		try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(root, "abyssme.db").getPath(), null, 0)) {
			db.setVersion(99);
		}
		try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
			try { db.appItemDao().get(42); fail("Unknown version must not reset the database"); }
			catch (IllegalStateException expected) { }
		}
		try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(root, "abyssme.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
			 Cursor cursor = db.rawQuery("SELECT title FROM apps WHERE id=42", null)) {
			assertEquals(99, db.getVersion());
			assertTrue(cursor.moveToFirst());
			assertEquals("My game", cursor.getString(0));
		}
	}
}
