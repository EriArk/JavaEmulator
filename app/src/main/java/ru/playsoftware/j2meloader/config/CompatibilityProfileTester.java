/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.woesss.j2me.jar.Descriptor;

final class CompatibilityProfileTester {
	interface Callback {
		void onProgress(int progress, String message);
	}

	static final class Result {
		final String profileName;
		final int score;
		final String confidence;
		final String reasons;

		private Result(String profileName, int score, String confidence, String reasons) {
			this.profileName = profileName;
			this.score = score;
			this.confidence = confidence;
			this.reasons = reasons;
		}
	}

	private static final String PROFILE_GENERIC = "Generic MIDP";
	private static final String PROFILE_NOKIA = "Nokia S40";
	private static final String PROFILE_SONY_ERICSSON = "Sony Ericsson JP-8";
	private static final String PROFILE_MOTOROLA = "Motorola";
	private static final String PROFILE_SIEMENS = "Siemens";
	private static final String PROFILE_SAMSUNG = "Samsung";
	private static final Pattern WEB_ADDRESS = Pattern.compile(
			"(?:https?://|www\\.)\\S+|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,24}(?:/\\S*)?");
	private static final Pattern RESOLUTION_PATTERN = Pattern.compile(
			"(?<!\\d)(128|160|176|208|220|240|320|352|360|416|480|640|800|854)\\s*[x*,_ -]\\s*(128|160|176|208|220|240|320|352|360|416|480|640|800|854)(?!\\d)");

	private CompatibilityProfileTester() {
	}

	static Result run(Context context, File appDir, ProfileModel params, Callback callback) throws IOException {
		checkCancelled();
		callback.onProgress(5, context.getString(R.string.compatibility_test_manifest));
		Probe probe = readProbe(appDir);

		callback.onProgress(18, context.getString(R.string.compatibility_test_resources));
		readResourceHints(appDir, probe);
		probe.apiText = readDexHints(appDir);
		probe.selectScreen();

		ArrayList<Candidate> candidates = createCandidates(probe);
		Candidate best = null;
		Candidate second = null;
		for (int i = 0, size = candidates.size(); i < size; i++) {
			checkCancelled();
			Candidate candidate = candidates.get(i);
			int progress = 25 + (i * 55 / Math.max(1, size));
			callback.onProgress(progress,
					context.getString(R.string.compatibility_test_profile, candidate.name));
			candidate.score += scoreCandidate(candidate, probe);
			if (best == null || candidate.score > best.score) {
				second = best;
				best = candidate;
			} else if (second == null || candidate.score > second.score) {
				second = candidate;
			}
		}

		if (best == null) {
			best = candidates.get(0);
		}
		// Shared portability libraries often mention several vendors. A tie is not a device hint.
		if (second != null && best.score == second.score) best = candidates.get(0);
		callback.onProgress(86, context.getString(R.string.compatibility_test_save, best.name));
		boolean fresh = params.isNew && !params.compatibilityTested;
		String defaults = ContextHolder.getAssetAsString("defaults/system.props");
		boolean applyProperties = fresh && (params.systemProperties == null
				|| params.systemProperties.trim().equals(defaults.trim()));
		if (applyProperties) {
			params.systemProperties = mergeProperties(params.systemProperties, best.properties);
			params.compatibilityProfile = best.name;
		}
		params.compatibilityScore = best.score;
		int margin = second == null ? best.score : best.score - second.score;
		String confidence = PROFILE_GENERIC.equals(best.name) ? "low"
				: best.score >= 200 && margin >= 80 ? "high"
				: best.score >= 80 && margin >= 30 ? "medium" : "low";
		String reasons = buildReasons(probe);
		if (!applyProperties) reasons += "; existing system properties kept";
		params.suggestedCompatibilityProfile = best.name;
		params.compatibilityConfidence = confidence;
		params.compatibilityReasons = reasons;
		applyDisplayDefaults(params, probe, fresh);
		params.compatibilityTested = true;
		if (BuildConfig.HANDHELD_MODE) {
			params.showKeyboard = false;
			params.touchInput = false;
		}
		checkCancelled();
		if (!ProfilesManager.saveConfig(params)) throw new IOException("Cannot save game settings");
		return new Result(best.name, best.score, confidence, reasons);
	}

	private static Probe readProbe(File appDir) {
		Probe probe = new Probe();
		try {
			File manifest = new File(appDir, Config.MIDLET_MANIFEST_FILE);
			if (manifest.length() > 1024 * 1024) return probe;
			Descriptor descriptor = new Descriptor(manifest, false);
			probe.attrs = descriptor.getAttrs();
			for (Map.Entry<String, String> entry : probe.attrs.entrySet()) {
				String key = entry.getKey().toLowerCase(Locale.US);
				String value = entry.getValue().toLowerCase(Locale.US);
				if (key.equals("target-device") || key.equals("target-platform") || key.equals("midlet-target-device")
						|| key.equals("microedition.platform") || key.equals("device"))
					probe.targetText += " " + withoutWebAddresses(value);
				if (key.equals("midlet-vendor")) probe.publisher = value.trim();
				if (key.contains("screen") || key.contains("resolution") || key.contains("display")
						|| key.contains("canvas")) readResolutionHints(probe, withoutWebAddresses(value), 1000);
				else if (key.equals("midlet-name")) readResolutionHints(probe, withoutWebAddresses(value), 600);
				else if (key.equals("midlet-jar-url")) {
					String filename = android.net.Uri.parse(value).getLastPathSegment();
					if (filename != null) readResolutionHints(probe, filename, 600);
				}
			}
		} catch (Exception ignored) {
			probe.attrs = new LinkedHashMap<>();
			probe.targetText = "";
			probe.publisher = "";
		}
		return probe;
	}

	private static void readResourceHints(File appDir, Probe probe) {
		File resJar = new File(appDir, Config.MIDLET_RES_FILE);
		if (!resJar.exists()) {
			return;
		}
		try (ZipFile zip = new ZipFile(resJar)) {
			ArrayList<ZipEntry> images = new ArrayList<>();
			Enumeration<? extends ZipEntry> entries = zip.entries();
			int count = 0;
			while (entries.hasMoreElements()) {
				checkCancelled();
				ZipEntry entry = entries.nextElement();
				if (++count > 20000) { images.clear(); break; }
				if (!entry.isDirectory() && isImageFile(entry.getName().toLowerCase(Locale.US))) images.add(entry);
			}
			Collections.sort(images, Comparator.comparing(ZipEntry::getName));
			for (ZipEntry entry : images.subList(0, Math.min(700, images.size()))) {
				checkCancelled();
				String lower = entry.getName().toLowerCase(Locale.US);
				if (!isExcludedImage(lower) && entry.getSize() >= 0 && entry.getSize() <= 8 * 1024 * 1024) {
					readResolutionHints(probe, lower, 500);
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					try (InputStream stream = zip.getInputStream(entry)) {
						BitmapFactory.decodeStream(stream, null, options);
						int score = scoreScreenCandidate(lower, options.outWidth, options.outHeight);
						if (score > 0) rememberScreenCandidate(probe, options.outWidth, options.outHeight, score);
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}
		checkCancelled();
	}

	private static String readDexHints(File appDir) {
		File dex = new File(appDir, Config.MIDLET_DEX_FILE);
		if (!dex.exists() || dex.length() <= 0 || dex.length() > 12 * 1024 * 1024) {
			return "";
		}
		byte[] data = new byte[(int) dex.length()];
		try (FileInputStream in = new FileInputStream(dex)) {
			int offset = 0;
			while (offset < data.length) {
				checkCancelled();
				int read = in.read(data, offset, data.length - offset);
				if (read < 0) {
					break;
				}
				offset += read;
			}
		} catch (Exception ignored) {
			return "";
		}
		return new String(data, StandardCharsets.ISO_8859_1).toLowerCase(Locale.US);
	}

	private static ArrayList<Candidate> createCandidates(Probe probe) {
		String profile = firstNonEmpty(probe.attrs.get("MicroEdition-Profile"), "MIDP-2.0");
		String configuration = firstNonEmpty(probe.attrs.get("MicroEdition-Configuration"), "CLDC-1.1");
		ArrayList<Candidate> candidates = new ArrayList<>();
		candidates.add(new Candidate(PROFILE_GENERIC, 1, baseProperties(
				"J2ME-Loader/Auto", profile, configuration)));
		candidates.add(new Candidate(PROFILE_NOKIA, 0, nokiaProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_SONY_ERICSSON, 0,
				sonyEricssonProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_MOTOROLA, 0,
				baseProperties("Motorola V3x", profile, configuration)));
		candidates.add(new Candidate(PROFILE_SIEMENS, 0,
				siemensProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_SAMSUNG, 0,
				baseProperties("SAMSUNG-SGH-D900", profile, configuration)));
		return candidates;
	}

	private static int scoreCandidate(Candidate candidate, Probe probe) {
		String[] hints;
		String[] apis;
		String prefix;
		String publisher;
		if (PROFILE_NOKIA.equals(candidate.name)) {
			prefix = "nokia-";
			publisher = "nokia";
			hints = new String[]{"nokia", "s40", "series40", "series 40", "6233", "n73"};
			apis = new String[]{"com/nokia/", "com.nokia."};
		} else if (PROFILE_SONY_ERICSSON.equals(candidate.name)) {
			prefix = "sonyericsson-";
			publisher = "sony ericsson";
			hints = new String[]{"sonyericsson", "sony ericsson", "k800", "jp-8", "jp8"};
			apis = new String[]{"com/sonyericsson/", "com.sonyericsson.", "com/semc/", "com.semc."};
		} else if (PROFILE_MOTOROLA.equals(candidate.name)) {
			prefix = "motorola-";
			publisher = "motorola";
			hints = new String[]{"motorola", "v3x", "razr"};
			apis = new String[]{"com/motorola/", "com.motorola."};
		} else if (PROFILE_SIEMENS.equals(candidate.name)) {
			prefix = "siemens-";
			publisher = "siemens";
			hints = new String[]{"siemens", "s65", "cx65"};
			apis = new String[]{"com/siemens/", "com.siemens."};
		} else if (PROFILE_SAMSUNG.equals(candidate.name)) {
			prefix = "samsung-";
			publisher = "samsung";
			hints = new String[]{"samsung", "sgh", "gt_s8000", "gt-s8000", "d900", "e250"};
			apis = new String[]{"com/samsung/", "com.samsung."};
		} else {
			return 0;
		}
		// Aliases count once. Metadata wins over weak references in multi-vendor code.
		if (containsAny(probe.targetText, hints)) {
			candidate.reason = "target metadata";
			return 240;
		}
		for (String key : probe.attrs.keySet()) {
			if (key.toLowerCase(Locale.US).startsWith(prefix)) {
				candidate.reason = "vendor-specific manifest attribute";
				return 60;
			}
		}
		if (probe.publisher.replace(" ", "").equals(publisher.replace(" ", ""))) {
			candidate.reason = "publisher name (not a device model)";
			return 40;
		}
		if (containsAny(probe.apiText, apis)) {
			candidate.reason = "API reference (not a device model)";
			return 30;
		}
		return 0;
	}

	private static boolean isImageFile(String lowerName) {
		return lowerName.endsWith(".png")
				|| lowerName.endsWith(".jpg")
				|| lowerName.endsWith(".jpeg");
	}

	private static void readResolutionHints(Probe probe, String text, int bonus) {
		Matcher matcher = RESOLUTION_PATTERN.matcher(text);
		while (matcher.find()) {
			try {
				int width = Integer.parseInt(matcher.group(1));
				int height = Integer.parseInt(matcher.group(2));
				rememberScreenCandidate(probe, width, height, bonus);
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private static void rememberScreenCandidate(Probe probe, int width, int height, int score) {
		if (width < 96 || height < 96 || width > 854 || height > 854) {
			return;
		}
		String key = width + "x" + height;
		Integer previous = probe.screens.get(key);
		probe.screens.put(key, previous == null ? score : Math.max(previous, score));
	}

	private static int scoreScreenCandidate(String name, int width, int height) {
		if (isExcludedImage(name)) return 0;
		boolean screen = containsAny(name, "splash", "title", "menu", "background", "loading");
		if (isKnownJ2meResolution(width, height)) return screen ? 300 : 100;
		return 0;
	}

	private static boolean isExcludedImage(String name) {
		return containsAny(name, "font", "sprite", "icon", "button", "tile", "digit", "logo", "atlas");
	}

	private static boolean isKnownJ2meResolution(int width, int height) {
		return matchesResolution(width, height, 128, 128)
				|| matchesResolution(width, height, 128, 160)
				|| matchesResolution(width, height, 176, 208)
				|| matchesResolution(width, height, 176, 220)
				|| matchesResolution(width, height, 208, 208)
				|| matchesResolution(width, height, 240, 320)
				|| matchesResolution(width, height, 320, 240)
				|| matchesResolution(width, height, 352, 416)
				|| matchesResolution(width, height, 360, 640)
				|| matchesResolution(width, height, 480, 800)
				|| matchesResolution(width, height, 480, 854);
	}

	private static boolean matchesResolution(int width, int height, int expectedWidth, int expectedHeight) {
		return (width == expectedWidth && height == expectedHeight)
				|| (width == expectedHeight && height == expectedWidth);
	}

	private static void applyDisplayDefaults(ProfileModel params, Probe probe, boolean fresh) {
		boolean defaults = fresh && params.screenWidth == 240 && params.screenHeight == 320;
		if (probe.screenWidth > 0 && probe.screenHeight > 0) {
			params.detectedScreenWidth = probe.screenWidth;
			params.detectedScreenHeight = probe.screenHeight;
			if (defaults) {
				params.screenWidth = probe.screenWidth;
				params.screenHeight = probe.screenHeight;
			}
		} else {
			params.detectedScreenWidth = 0;
			params.detectedScreenHeight = 0;
		}
		if (fresh) params.forceFullscreen = true;
		if (BuildConfig.HANDHELD_MODE) params.orientation = 3;
	}

	private static boolean containsAny(String text, String... hints) {
		for (String hint : hints) {
			if (text.contains(hint)) return true;
		}
		return false;
	}

	private static String withoutWebAddresses(String value) {
		return WEB_ADDRESS.matcher(value).replaceAll(" ");
	}

	private static void checkCancelled() {
		if (Thread.currentThread().isInterrupted()) throw new CancellationException();
	}

	private static LinkedHashMap<String, String> baseProperties(String platform, String profile,
															   String configuration) {
		LinkedHashMap<String, String> props = new LinkedHashMap<>();
		props.put("microedition.configuration", configuration);
		props.put("microedition.profiles", profile);
		props.put("microedition.encoding", "ISO-8859-1");
		props.put("microedition.platform", platform);
		props.put("microedition.io.file.FileConnection.version", "1.0");
		props.put("microedition.sensor.version", "1");
		props.put("microedition.m3g.version", "1.1");
		props.put("microedition.media.version", "1.0");
		props.put("microedition.pim.version", "1.0");
		props.put("microedition.location.version", "1.0");
		props.put("supports.mixing", "true");
		props.put("supports.audio.capture", "true");
		props.put("supports.video.capture", "true");
		props.put("supports.recording", "true");
		props.put("device.imei", "000000000000000");
		props.put("wireless.messaging.sms.smsc", "+8613800010000");
		return props;
	}

	private static LinkedHashMap<String, String> nokiaProperties(String profile, String configuration) {
		LinkedHashMap<String, String> props = baseProperties("Nokia6233/05.10", profile, configuration);
		props.put("com.nokia.mid.impl.isa.visual_radio_operator_id", "0");
		props.put("com.nokia.mid.impl.isa.visual_radio_channel_freq", "0");
		props.put("com.nokia.mid.ui.DirectGraphics.PIXEL_FORMAT", "565");
		props.put("com.nokia.mid.ui.softnotification", "true");
		return props;
	}

	private static LinkedHashMap<String, String> sonyEricssonProperties(String profile, String configuration) {
		LinkedHashMap<String, String> props = baseProperties("SonyEricssonK800i/R8BF003", profile, configuration);
		props.put("com.sonyericsson.java.platform", "JP-8");
		props.put("com.sonyericsson.imei", "IMEI 00460101-501594-5-00");
		return props;
	}

	private static LinkedHashMap<String, String> siemensProperties(String profile, String configuration) {
		LinkedHashMap<String, String> props = baseProperties("SIEMENS-S65/58", profile, configuration);
		props.put("com.siemens.mp.systemfolder.ringingtone", "fs/MyStuff/Ringtones");
		props.put("com.siemens.mp.systemfolder.pictures", "fs/MyStuff/Pictures");
		props.put("com.siemens.OSVersion", "11");
		props.put("com.siemens.IMEI", "000000000000000");
		return props;
	}

	private static String mergeProperties(String current, LinkedHashMap<String, String> overrides) {
		if (current == null || current.trim().isEmpty()) {
			current = ContextHolder.getAssetAsString("defaults/system.props");
		}
		LinkedHashMap<String, String> merged = new LinkedHashMap<>();
		String[] lines = current.split("[\\n\\r]+");
		for (String line : lines) {
			int separator = line.indexOf(':');
			if (separator <= 0) {
				continue;
			}
			merged.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
		}
		merged.putAll(overrides);
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, String> entry : merged.entrySet()) {
			out.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		return out.toString();
	}

	private static String firstNonEmpty(String value, String fallback) {
		return value == null || value.trim().isEmpty() ? fallback : value.trim();
	}

	private static String buildReasons(Probe probe) {
		ArrayList<String> reasons = new ArrayList<>();
		if (probe.screenWidth > 0 && probe.screenHeight > 0) {
			reasons.add((probe.screenScore >= 1000 ? "declared screen " : "resources suggest ")
					+ probe.screenWidth + "x" + probe.screenHeight);
		} else {
			reasons.add(probe.screens.isEmpty() ? "no screen-size evidence" : "conflicting screen sizes; size kept");
		}
		for (Candidate candidate : createCandidates(probe)) {
			if (scoreCandidate(candidate, probe) > 0) reasons.add(candidate.name + ": " + candidate.reason);
		}
		reasons.add("static analysis, not a gameplay test");
		return android.text.TextUtils.join(", ", reasons);
	}

	private static final class Candidate {
		final String name;
		final LinkedHashMap<String, String> properties;
		int score;
		String reason;

		private Candidate(String name, int score, LinkedHashMap<String, String> properties) {
			this.name = name;
			this.score = score;
			this.properties = properties;
		}
	}

	private static final class Probe {
		Map<String, String> attrs = new LinkedHashMap<>();
		String publisher = "";
		String targetText = "";
		String apiText = "";
		final Map<String, Integer> screens = new LinkedHashMap<>();
		int screenWidth;
		int screenHeight;
		int screenScore;

		void selectScreen() {
			String best = null;
			boolean tied = false;
			for (Map.Entry<String, Integer> entry : screens.entrySet()) {
				if (entry.getValue() > screenScore) {
					screenScore = entry.getValue();
					best = entry.getKey();
					tied = false;
				} else if (entry.getValue() == screenScore) tied = true;
			}
			if (best != null && !tied) {
				String[] size = best.split("x");
				screenWidth = Integer.parseInt(size[0]);
				screenHeight = Integer.parseInt(size[1]);
			}
		}
	}
}
