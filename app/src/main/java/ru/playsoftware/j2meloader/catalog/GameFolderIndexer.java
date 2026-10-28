/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.R;

public final class GameFolderIndexer {
	private static final int MAX_DEPTH = 8;
	private static final int MAX_ITEMS = 10000;
	private static final String[] COLUMNS = {
			DocumentsContract.Document.COLUMN_DOCUMENT_ID,
			DocumentsContract.Document.COLUMN_DISPLAY_NAME,
			DocumentsContract.Document.COLUMN_MIME_TYPE
	};

	public static final class Result {
		public final List<AppItem> games = new ArrayList<>();
		public int skippedFolders;
		public boolean limited;
		private int visited;

		public String summary(Context context, int added) {
			String text = context.getString(R.string.folders_scan_summary, added, games.size() - added);
			if (games.isEmpty()) text = context.getString(R.string.folders_no_games);
			if (skippedFolders > 0) text += "\n" + context.getResources().getQuantityString(
					R.plurals.folders_unreadable, skippedFolders, skippedFolders);
			if (limited) text += "\n" + context.getString(R.string.folders_scan_limit);
			return text;
		}
	}

	private static final class Entry {
		final String id, name, mime;
		Entry(String id, String name, String mime) { this.id = id; this.name = name; this.mime = mime; }
		boolean directory() { return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime); }
	}

	private GameFolderIndexer() {
	}

	public static synchronized void rememberFolder(Context context, Uri uri) {
		android.content.SharedPreferences preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
		Set<String> folders = new HashSet<>(preferences.getStringSet("pref_game_folders", java.util.Collections.emptySet()));
		folders.add(uri.toString());
		preferences.edit().putStringSet("pref_game_folders", folders).apply();
	}

	public static Result scan(Context context, Uri treeUri) throws IOException {
		Result result = new Result();
		try {
			scanDirectory(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri),
					result, new HashSet<>(), 0);
		} catch (SecurityException | IllegalArgumentException error) {
			throw new IOException(context.getString(R.string.folders_access_required), error);
		}
		return result;
	}

	private static void scanDirectory(Context context, Uri tree, String id, Result result,
			Set<String> directories, int depth) throws IOException {
		checkCancelled();
		if (!directories.add(id)) return;
		if (depth > MAX_DEPTH || result.visited >= MAX_ITEMS) {
			result.limited = true;
			return;
		}
		List<Entry> entries = new ArrayList<>();
		Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
		// DocumentFile.listFiles() hides provider failures as empty folders. Keep them visible.
		try (Cursor cursor = context.getContentResolver().query(children, COLUMNS, null, null, null)) {
			if (cursor == null) throw new IOException(context.getString(R.string.folders_provider_empty));
			while (cursor.moveToNext()) {
				checkCancelled();
				if (result.visited >= MAX_ITEMS) { result.limited = true; break; }
				result.visited++;
				String childId = cursor.getString(0);
				if (childId != null) entries.add(new Entry(childId, cursor.getString(1), cursor.getString(2)));
			}
			if (cursor.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
				throw new IOException(context.getString(R.string.folders_provider_loading));
			}
		} catch (InterruptedIOException cancelled) {
			throw cancelled;
		} catch (RuntimeException | IOException error) {
			if (depth == 0) throw new IOException(context.getString(R.string.folders_read_failed,
					error instanceof IOException ? error.getMessage() : context.getString(R.string.folders_retry_provider)), error);
			result.skippedFolders++;
			return;
		}
		Set<String> jars = new HashSet<>();
		for (Entry entry : entries) {
			if (!entry.directory() && entry.name != null && entry.name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
				jars.add(entry.name.substring(0, entry.name.length() - 4).toLowerCase(Locale.ROOT));
			}
		}
		Set<String> files = new HashSet<>();
		for (Entry entry : entries) {
			checkCancelled();
			String name = entry.name;
			if (entry.directory() || name == null || !isSupported(name) || !files.add(entry.id)) continue;
			String stem = name.substring(0, name.lastIndexOf('.'));
			if (name.toLowerCase(Locale.ROOT).endsWith(".jad") && jars.contains(stem.toLowerCase(Locale.ROOT))) continue;
			Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id);
			String sourceKey = SourceIdentity.key(uri);
			String title = stem.replace('_', ' ').trim();
			AppItem item = new AppItem("pending_" + SourceIdentity.shortId(sourceKey),
					title.isEmpty() ? name : title, "", "");
			item.setSourceUri(uri.toString());
			item.setSourceKey(sourceKey);
			item.setPreparationState("indexed");
			result.games.add(item);
		}
		for (Entry entry : entries) {
			if (entry.directory()) scanDirectory(context, tree, entry.id, result, directories, depth + 1);
		}
	}

	private static void checkCancelled() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Folder scan cancelled");
	}

	static boolean isSupported(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".jar") || lower.endsWith(".jad") || lower.endsWith(".kjx")
				|| lower.endsWith(".mpn") || lower.endsWith(".zip") || lower.endsWith(".7z");
	}
}
