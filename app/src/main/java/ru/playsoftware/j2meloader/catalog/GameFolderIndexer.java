/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.playsoftware.j2meloader.applist.AppItem;

public final class GameFolderIndexer {
	private static final int MAX_DEPTH = 8;
	private static final int MAX_ITEMS = 10000;

	private GameFolderIndexer() {
	}

	public static List<AppItem> scan(Context context, Uri treeUri) {
		ArrayList<AppItem> games = new ArrayList<>();
		DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
		if (root != null) {
			scanDirectory(root, games, 0);
		}
		return games;
	}

	private static void scanDirectory(DocumentFile directory, List<AppItem> result, int depth) {
		if (depth > MAX_DEPTH || result.size() >= MAX_ITEMS) {
			return;
		}
		for (DocumentFile file : directory.listFiles()) {
			if (result.size() >= MAX_ITEMS) {
				return;
			}
			if (file.isDirectory()) {
				scanDirectory(file, result, depth + 1);
				continue;
			}
			String name = file.getName();
			if (name == null || !isSupported(name)) {
				continue;
			}
			Uri uri = file.getUri();
			String sourceKey = SourceIdentity.key(uri);
			String title = name.substring(0, name.lastIndexOf('.')).replace('_', ' ').trim();
			AppItem item = new AppItem("pending_" + SourceIdentity.shortId(sourceKey),
					title, "", "");
			item.setSourceUri(uri.toString());
			item.setSourceKey(sourceKey);
			item.setPreparationState("indexed");
			result.add(item);
		}
	}

	private static boolean isSupported(String name) {
		String lower = name.toLowerCase(Locale.US);
		return lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".7z");
	}
}
