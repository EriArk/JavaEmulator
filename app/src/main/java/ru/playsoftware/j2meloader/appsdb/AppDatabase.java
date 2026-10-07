/*
 * Copyright 2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.appsdb;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;

import ru.playsoftware.j2meloader.applist.AppItem;

@Database(entities = {AppItem.class}, version = 3, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase implements AutoCloseable {

	public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
		@Override
		public void migrate(SupportSQLiteDatabase db) {
			db.execSQL("ALTER TABLE apps ADD COLUMN coverPath TEXT");
		}
	};

	public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
		@Override
		public void migrate(SupportSQLiteDatabase db) {
			// Rebuild with the exact v3 schema, without permanent SQL defaults on new columns.
			db.execSQL("CREATE TABLE apps_migrated (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
					+ "imagePath TEXT, coverPath TEXT, title TEXT, author TEXT, version TEXT, path TEXT, "
					+ "sourceUri TEXT, sourceKey TEXT, sourceHash TEXT, favorite INTEGER NOT NULL, "
					+ "lastPlayedAt INTEGER NOT NULL, playCount INTEGER NOT NULL, preparationState TEXT, lastError TEXT)");
			db.execSQL("INSERT INTO apps_migrated (id,imagePath,coverPath,title,author,version,path,"
					+ "favorite,lastPlayedAt,playCount,preparationState) "
					+ "SELECT id,imagePath,coverPath,title,author,version,path,0,0,0,'ready' FROM apps");
			db.execSQL("DROP TABLE apps");
			db.execSQL("ALTER TABLE apps_migrated RENAME TO apps");
			db.execSQL("CREATE UNIQUE INDEX index_apps_path ON apps(path)");
			db.execSQL("CREATE UNIQUE INDEX index_apps_sourceKey ON apps(sourceKey)");
		}
	};

	public abstract AppItemDao appItemDao();

	public static synchronized AppDatabase open(Context context, String dir) {
		File current = new File(dir, "abyssme.db");
		File legacy = new File(dir, "J2ME-apps.db");
		// Keep the legacy file and its WAL together. Never overwrite or merge two catalogs.
		File database = !current.exists() && legacy.isFile() ? legacy : current;
		return Room.databaseBuilder(
				context.getApplicationContext(),
				AppDatabase.class,
				database.getAbsolutePath())
				.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
				.enableMultiInstanceInvalidation()
				.build();
	}
}
