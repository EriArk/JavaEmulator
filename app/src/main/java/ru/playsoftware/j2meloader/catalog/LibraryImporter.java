/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.database.Cursor;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.android.dx.command.dexer.Main;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.woesss.j2me.jar.Descriptor;

/** Copy-only folder import. No source writes and no old DEX execution. */
public final class LibraryImporter {
	public interface Catalog {
		AppItem bySource(String key);
		AppItem byPath(String path);
		AppItem byTitle(String title, String vendor);
		void insert(AppItem item);
	}

	public static Catalog catalog(AppRepository repository) {
		return new Catalog() {
			public AppItem bySource(String key) { return repository.getBySourceKey(key); }
			public AppItem byPath(String path) { return repository.getByPath(path); }
			public AppItem byTitle(String title, String vendor) { return repository.get(title, vendor); }
			public void insert(AppItem item) { repository.insertImported(item); }
		};
	}

	public static final class Entry {
		public String title, vendor, version, sourceKey, problem;
		public boolean selected, duplicateTitle;
		private DocumentFile game, data, config;
	}

	public static final class Preview {
		public final List<Entry> entries = new ArrayList<>();
		public boolean sharedFiles;
	}

	private static final long MAX_BYTES = 256L * 1024 * 1024;
	private static final int MAX_FILES = 10000;
	private static final Set<String> active = new HashSet<>();
	private final Context context;
	private final File destination;
	private final Catalog catalog;
	private final AtomicBoolean cancelled;
	private long copied;
	private int files;

	public LibraryImporter(Context context, File destination, Catalog catalog, AtomicBoolean cancelled) {
		this.context = context.getApplicationContext();
		this.destination = destination;
		this.catalog = catalog;
		this.cancelled = cancelled;
	}

	public Preview scan(DocumentFile root) throws IOException {
		checkCancelled();
		if (root == null || !root.isDirectory() || !root.canRead()) throw new IOException("Cannot read this folder");
		DocumentFile converted = find(root, "converted");
		if (converted == null || !converted.isDirectory()) throw new IOException("Select the J2ME Loader folder containing converted, data and configs");
		Preview result = new Preview();
		result.sharedFiles = find(root, "fs") != null;
		DocumentFile data = find(root, "data"), configs = find(root, "configs");
		DocumentFile[] games = children(converted);
		if (games.length > MAX_FILES) throw new IOException("Too many library entries");
		for (DocumentFile game : games) {
			checkCancelled();
			if (!game.isDirectory() || ".tmp".equals(game.getName())) continue;
			Entry entry = new Entry();
			entry.game = game;
			entry.title = game.getName();
			entry.sourceKey = "j2me-library:" + game.getUri();
			try {
				validName(game.getName());
				Descriptor descriptor = new Descriptor(readText(find(game, "converted.dex.conf"), 1024 * 1024), false);
				entry.title = descriptor.getName();
				entry.vendor = descriptor.getVendor();
				entry.version = descriptor.getVersion();
				entry.data = data == null ? null : find(data, game.getName());
				entry.config = configs == null ? null : find(configs, game.getName());
				if (find(game, "res.jar") == null) entry.problem = "Original JAR required (no res.jar)";
				else if (catalog.bySource(entry.sourceKey) != null) entry.problem = "Already imported; existing saves will be kept";
				entry.duplicateTitle = catalog.byTitle(entry.title, entry.vendor) != null;
				entry.selected = entry.problem == null && !entry.duplicateTitle;
			} catch (IOException | RuntimeException error) {
				if (error instanceof CancellationException) throw error;
				entry.problem = "Unsupported entry: " + error.getMessage();
			}
			result.entries.add(entry);
		}
		result.entries.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
		return result;
	}

	public String importEntry(Entry entry) throws Exception {
		checkCancelled();
		if (entry.problem != null) throw new IOException(entry.problem);
		copied = 0; files = 0;
		String id = "import-" + UUID.randomUUID();
		File stage = new File(new File(destination, ".library-import"), id);
		synchronized (LibraryImporter.class) {
			mkdir(stage);
			active.add(id);
		}
		File app = new File(stage, "converted"), data = new File(stage, "data"), config = new File(stage, "configs");
		boolean committed = false;
		String notes;
		try {
			mkdir(app); mkdir(data); mkdir(config);
			copyFile(find(entry.game, "res.jar"), new File(app, "res.jar"));
			Descriptor descriptor = new Descriptor(readText(find(entry.game, "converted.dex.conf"), 1024 * 1024), false);
			if (!entry.title.equals(descriptor.getName()) || !entry.vendor.equals(descriptor.getVendor())
					|| !entry.version.equals(descriptor.getVersion())) throw new IOException("Source changed; scan the library again");
			File jar = new File(app, "res.jar");
			validateJar(jar, descriptor);
			checkCancelled();
			// Recompile original classes instead of trusting a foreign converted.dex.
			Main.main(new String[]{"--no-optimize", "--core-library", "--output=" + new File(app, "converted.dex"), jar.getPath()});
			if (!new File(app, "converted.dex").isFile()) throw new IOException("Missing converted code");
			descriptor.writeTo(new File(app, "converted.dex.conf"));
			for (String art : new String[]{"icon.png", "cover.png"}) {
				DocumentFile file = find(entry.game, art);
				if (file != null) copyFile(file, new File(app, art));
			}
			if (entry.data != null) copyTree(entry.data, data, 0);
			notes = importConfig(entry.config, config);
			checkCancelled();
			AppItem item = new AppItem(id, descriptor.getName(), descriptor.getVendor(), descriptor.getVersion());
			item.setSourceKey(entry.sourceKey);
			item.setSourceHash(SourceIdentity.sha256(jar));
			if (new File(app, "icon.png").isFile()) item.setImagePathExt("icon.png");
			if (new File(app, "cover.png").isFile()) item.setCoverPathExt("cover.png");
			// Recovery and startup indexing share this lock. The marker is durable before any moves.
			synchronized (LibraryImporter.class) {
				checkCancelled();
				if (catalog.bySource(entry.sourceKey) != null) throw new IOException("Already imported; no files replaced");
				for (String dir : new String[]{"data", "configs", "converted"}) {
					File target = new File(new File(destination, dir), id);
					if (target.exists()) throw new IOException("Destination collision; no files replaced");
					mkdir(target.getParentFile());
				}
				try (FileOutputStream marker = new FileOutputStream(new File(stage, "pending"))) {
					marker.write(1); marker.getFD().sync();
				}
				try {
					for (String dir : new String[]{"data", "configs", "converted"}) {
						checkCancelled();
						if (!new File(stage, dir).renameTo(new File(new File(destination, dir), id)))
							throw new IOException("Cannot publish imported files");
					}
					catalog.insert(item);
					committed = true;
				} finally {
					if (!committed) rollback(destination, id);
				}
			}
			return "Imported" + (notes.isEmpty() ? "" : "; " + notes);
		} finally {
			// A pending marker is kept if cleanup fails so startup can retry safely.
			synchronized (LibraryImporter.class) {
				try {
					if (committed || !new File(stage, "pending").exists() || !hasPublished(destination, id)) delete(stage);
				} finally { active.remove(id); }
			}
		}
	}

	private String importConfig(DocumentFile source, File target) throws IOException {
		String notes = "";
		DocumentFile json = source == null ? null : find(source, "config.json");
		if (json == null) return "settings not transferred; automatic setup on launch";
		JsonObject original;
		try { original = JsonParser.parseString(readText(json, 1024 * 1024)).getAsJsonObject(); }
		catch (RuntimeException e) { throw new IOException("Invalid settings; game was not imported", e); }
		int version = original.has("Version") ? original.get("Version").getAsInt() : 0;
		if (version < 3 || version > ProfileModel.VERSION) return "unsupported settings version; automatic setup on launch";
		// Transfer only fields the current profile understands. Global paths and shaders are not imported.
		Gson gson = new Gson();
		JsonObject merged = gson.toJsonTree(new ProfileModel(target)).getAsJsonObject();
		Set<String> allowed = new HashSet<>(Arrays.asList(
				"ScreenWidth", "ScreenHeight", "ScreenBackgroundColor", "ScreenScaleRatio", "Orientation",
				"ScreenScaleType", "ScreenGravity", "ScreenFilter", "ImmediateMode", "GraphicsMode",
				"ParallelRedrawScreen", "ShowFps", "FpsLimit", "ForceFullscreen", "FontSizeSmall",
				"FontSizeMedium", "FontSizeLarge", "FontApplyDimensions", "FontAntiAlias", "TouchInput",
				"ShowKeyboard", "VirtualKeyboardType", "ButtonShape", "VirtualKeyboardAlpha",
				"VirtualKeyboardForceOpacity", "VirtualKeyboardFeedback", "VirtualKeyboardDelay",
				"VirtualKeyboardColorBackground", "VirtualKeyboardColorBackgroundSelected",
				"VirtualKeyboardColorForeground", "VirtualKeyboardColorForegroundSelected",
				"VirtualKeyboardColorOutline", "Layout", "KeyCodeMap", "KeyMappings", "SystemProperties"));
		for (String key : allowed) if (original.has(key) && !original.get(key).isJsonNull()) merged.add(key, original.get(key));
		ProfileModel profile;
		try { profile = gson.fromJson(merged, ProfileModel.class); }
		catch (RuntimeException e) { throw new IOException("Invalid settings; game was not imported", e); }
		if (profile.screenWidth < 0 || profile.screenWidth > 4096 || profile.screenHeight < 0 || profile.screenHeight > 4096
				|| profile.screenScaleRatio < 1 || profile.screenScaleRatio > 400)
			throw new IOException("Unsupported display settings; game was not imported");
		StringBuilder properties = new StringBuilder();
		for (String line : profile.systemProperties.split("[\\r\\n]+")) {
			if (line.contains("file:") || line.contains("content:") || line.contains("/storage/")
					|| line.contains("/sdcard/") || line.startsWith("user.home") || line.startsWith("fileconn.dir.")) {
				notes = "external paths and custom shaders not transferred";
			} else properties.append(line).append('\n');
		}
		profile.systemProperties = properties.toString();
		if (original.has("Shader") && !original.get("Shader").isJsonNull()) notes = "external paths and custom shaders not transferred";
		profile.dir = target;
		profile.keyMappingProfiles = null;
		profile.ensureKeyMappingProfiles();
		profile.touchLayout = null;
		if (BuildConfig.HANDHELD_MODE) {
			profile.showKeyboard = false;
			profile.touchInput = false;
			profile.orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		} else {
			DocumentFile keyboard = find(source, "VirtualKeyboardLayout");
			if (keyboard != null) copyFile(keyboard, new File(target, "VirtualKeyboardLayout"));
		}
		if (!ProfilesManager.saveConfig(profile)) throw new IOException("Cannot save imported settings");
		return notes;
	}

	private void validateJar(File jar, Descriptor descriptor) throws IOException {
		long size = 0;
		int count = 0;
		boolean classes = false;
		try (ZipFile zip = new ZipFile(jar)) {
			java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
			byte[] buffer = new byte[65536];
			while (entries.hasMoreElements()) {
				checkCancelled();
				ZipEntry entry = entries.nextElement();
				if (++count > MAX_FILES) throw new IOException("JAR contains too many entries");
				String name = entry.getName();
				if (name.startsWith("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe JAR path");
				for (String part : name.split("/")) if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe JAR path");
				classes |= name.endsWith(".class");
				CRC32 crc = new CRC32();
				try (InputStream input = zip.getInputStream(entry)) {
					int n;
					while ((n = input.read(buffer)) != -1) {
						checkCancelled(); size += n; crc.update(buffer, 0, n);
						if (size > MAX_BYTES) throw new IOException("JAR expands beyond 256 MiB");
					}
				}
				if (crc.getValue() != entry.getCrc()) throw new IOException("Corrupt JAR entry");
			}
			String midlet = descriptor.getAttrs().get("MIDlet-1");
			if (!classes || midlet == null) throw new IOException("Original JAR required (missing classes or MIDlet entry)");
			String main = midlet.substring(midlet.lastIndexOf(',') + 1).trim().replace('.', '/') + ".class";
			if (zip.getEntry(main) == null) throw new IOException("Original JAR required (MIDlet class missing)");
		}
	}

	private void copyTree(DocumentFile source, File target, int depth) throws IOException {
		checkCancelled();
		if (!source.isDirectory() || depth > 20) throw new IOException("Unsupported save folder structure");
		mkdir(target);
		for (DocumentFile child : children(source)) {
			checkCancelled(); validName(child.getName());
			if (++files > MAX_FILES) throw new IOException("Too many files in game data");
			File file = new File(target, child.getName());
			if (child.isDirectory()) copyTree(child, file, depth + 1);
			else copyFile(child, file);
		}
	}

	private void copyFile(DocumentFile source, File target) throws IOException {
		if (source == null || !source.isFile() || !source.canRead()) throw new IOException("Missing or unreadable source file");
		long expected = source.length(), written = 0, modified = source.lastModified();
		try (InputStream in = open(source); FileOutputStream out = new FileOutputStream(target)) {
			byte[] buffer = new byte[65536]; int n;
			while ((n = in.read(buffer)) != -1) {
				checkCancelled(); copied += n; written += n;
				if (copied > MAX_BYTES) throw new IOException("Game import exceeds 256 MiB");
				out.write(buffer, 0, n);
			}
			out.getFD().sync();
		}
		if ((expected > 0 && expected != written) || source.length() != expected || source.lastModified() != modified)
			throw new IOException("Source changed during import; close J2ME Loader and retry");
	}

	private String readText(DocumentFile file, int limit) throws IOException {
		if (file == null || !file.isFile()) throw new IOException("Missing library metadata");
		try (InputStream in = open(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192]; int n;
			while ((n = in.read(buffer)) != -1) {
				checkCancelled();
				if (out.size() + n > limit) throw new IOException("Metadata too large");
				out.write(buffer, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private DocumentFile find(DocumentFile directory, String name) throws IOException {
		for (DocumentFile child : children(directory)) if (name.equals(child.getName())) return child;
		return null;
	}

	private DocumentFile[] children(DocumentFile directory) throws IOException {
		checkCancelled();
		if (!directory.isDirectory() || !directory.canRead()) throw new IOException("Cannot read source folder");
		Uri uri = directory.getUri();
		if ("file".equals(uri.getScheme())) {
			File file = new File(uri.getPath());
			if (Files.isSymbolicLink(file.toPath()) || file.list() == null) throw new IOException("Unreadable or linked source folder");
		}
		DocumentFile[] children = directory.listFiles();
		// DocumentFile swallows provider errors and returns an empty/partial list. Never call that success.
		if ("content".equals(uri.getScheme())) {
			Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
			try (Cursor cursor = context.getContentResolver().query(childUri,
					new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
				if (cursor == null || cursor.getCount() != children.length) throw new IOException("Incomplete folder listing; retry import");
			} catch (RuntimeException error) { throw new IOException("Cannot enumerate source folder", error); }
			for (DocumentFile child : children) {
				try (Cursor cursor = context.getContentResolver().query(child.getUri(),
						new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
					if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0)) throw new IOException("Cannot read a source entry");
				} catch (RuntimeException error) {
					throw new IOException("This provider blocks nested folders. Select a copy in Documents instead.", error);
				}
			}
		}
		if (children.length > MAX_FILES) throw new IOException("Too many source files");
		return children;
	}

	private InputStream open(DocumentFile file) throws IOException {
		Uri uri = file.getUri();
		if ("file".equals(uri.getScheme())) {
			File source = new File(uri.getPath());
			if (Files.isSymbolicLink(source.toPath())) throw new IOException("Symbolic links are not supported");
			return new FileInputStream(source);
		}
		InputStream input = context.getContentResolver().openInputStream(uri);
		if (input == null) throw new IOException("Cannot open source file");
		return input;
	}

	private void checkCancelled() {
		if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("Import cancelled");
	}

	private static void validName(String name) throws IOException {
		if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
				|| name.contains("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe file name");
	}

	private static void mkdir(File dir) throws IOException {
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create import folder");
	}

	private static void delete(File root) throws IOException {
		if (!root.exists()) return;
		if (Files.isSymbolicLink(root.toPath())) throw new IOException("Unexpected link in import folder");
		File[] children = root.listFiles();
		if (children != null) for (File child : children) delete(child);
		if (!root.delete()) throw new IOException("Could not clean up import folder");
	}

	private static boolean hasPublished(File root, String id) {
		for (String dir : new String[]{"data", "configs", "converted"}) if (new File(new File(root, dir), id).exists()) return true;
		return false;
	}

	private static void rollback(File root, String id) throws IOException {
		for (String dir : new String[]{"converted", "configs", "data"}) delete(new File(new File(root, dir), id));
	}

	/** Only transactions with our generated IDs and durable pending marker own published paths. */
	public static synchronized void recover(File root, Catalog catalog) throws IOException {
		File[] stages = new File(root, ".library-import").listFiles();
		if (stages == null) return;
		for (File stage : stages) {
			String id = stage.getName();
			if (active.contains(id)) continue;
			if (!id.matches("import-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) continue;
			if (new File(stage, "pending").isFile() && catalog.byPath(id) == null) rollback(root, id);
			delete(stage);
		}
	}
}
