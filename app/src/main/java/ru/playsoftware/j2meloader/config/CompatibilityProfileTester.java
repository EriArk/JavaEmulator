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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.microedition.util.ContextHolder;

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
	private static final Pattern RESOLUTION_PATTERN = Pattern.compile(
			"(?<!\\d)(128|176|208|240|320|352|360|480|640)[x*_ -](128|160|176|208|220|240|320|352|360|416|480|640)(?!\\d)");

	private CompatibilityProfileTester() {
	}

	static Result run(Context context, File appDir, ProfileModel params, Callback callback) {
		callback.onProgress(5, context.getString(R.string.compatibility_test_manifest));
		Probe probe = readProbe(appDir);

		callback.onProgress(18, context.getString(R.string.compatibility_test_resources));
		readResourceHints(appDir, probe);
		probe.resourceText += '\n' + readDexHints(appDir);

		ArrayList<Candidate> candidates = createCandidates(probe);
		Candidate best = null;
		Candidate second = null;
		for (int i = 0, size = candidates.size(); i < size; i++) {
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
		callback.onProgress(86, context.getString(R.string.compatibility_test_save, best.name));
		params.systemProperties = mergeProperties(params.systemProperties, best.properties);
		params.compatibilityProfile = best.name;
		params.compatibilityScore = best.score;
		int margin = second == null ? best.score : best.score - second.score;
		String confidence = margin >= 80 ? "high" : margin >= 30 ? "medium" : "low";
		String reasons = buildReasons(probe);
		params.compatibilityConfidence = confidence;
		params.compatibilityReasons = reasons;
		params.compatibilityTested = true;
		applyDisplayDefaults(params, probe);
		params.showKeyboard = false;
		params.touchInput = false;
		ProfilesManager.saveConfig(params);
		return new Result(best.name, best.score, confidence, reasons);
	}

	private static Probe readProbe(File appDir) {
		Probe probe = new Probe();
		try {
			Descriptor descriptor = new Descriptor(new File(appDir, Config.MIDLET_MANIFEST_FILE), false);
			probe.attrs = descriptor.getAttrs();
			StringBuilder text = new StringBuilder();
			for (Map.Entry<String, String> entry : probe.attrs.entrySet()) {
				text.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
			}
			probe.manifestText = text.toString().toLowerCase(Locale.US);
			readResolutionHints(probe, probe.manifestText, 320);
		} catch (Exception ignored) {
			probe.attrs = new LinkedHashMap<>();
			probe.manifestText = "";
		}
		return probe;
	}

	private static void readResourceHints(File appDir, Probe probe) {
		File resJar = new File(appDir, Config.MIDLET_RES_FILE);
		if (!resJar.exists()) {
			probe.resourceText = "";
			return;
		}
		StringBuilder text = new StringBuilder();
		try (ZipFile zip = new ZipFile(resJar)) {
			int count = 0;
			for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
				if (count++ > 700) {
					break;
				}
				text.append(entry.getName()).append('\n');
				String lower = entry.getName().toLowerCase(Locale.US);
				if (isImageFile(lower)) {
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					try (InputStream stream = zip.getInputStream(entry)) {
						BitmapFactory.decodeStream(stream, null, options);
						rememberScreenCandidate(probe, lower, options.outWidth, options.outHeight, 0);
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}
		probe.resourceText = text.toString().toLowerCase(Locale.US);
		readResolutionHints(probe, probe.resourceText, 80);
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
		candidates.add(new Candidate(PROFILE_GENERIC, 24, baseProperties(
				"J2ME-Loader/Auto", profile, configuration)));
		candidates.add(new Candidate(PROFILE_NOKIA, 28, nokiaProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_SONY_ERICSSON, 28,
				sonyEricssonProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_MOTOROLA, 26,
				baseProperties("Motorola V3x", profile, configuration)));
		candidates.add(new Candidate(PROFILE_SIEMENS, 26,
				siemensProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_SAMSUNG, 26,
				baseProperties("SAMSUNG-SGH-D900", profile, configuration)));
		return candidates;
	}

	private static int scoreCandidate(Candidate candidate, Probe probe) {
		String text = probe.manifestText + '\n' + probe.resourceText;
		int score = 0;
		if (PROFILE_NOKIA.equals(candidate.name)) {
			score += scoreHints(text, "nokia", "s40", "series40", "series 40", "6233", "n73",
					"com/nokia", "com.nokia", "directgraphics");
			if (hasAttrPrefix(probe, "Nokia-")) {
				score += 80;
			}
		} else if (PROFILE_SONY_ERICSSON.equals(candidate.name)) {
			score += scoreHints(text, "sony", "ericsson", "sonyericsson", "sony ericsson",
					"k800", "jp-8", "jp8", "com/sonyericsson", "com.sonyericsson",
					"com/semc", "com.semc");
		} else if (PROFILE_MOTOROLA.equals(candidate.name)) {
			score += scoreHints(text, "motorola", "moto", "v3", "v3x", "razr",
					"com/motorola", "com.motorola");
		} else if (PROFILE_SIEMENS.equals(candidate.name)) {
			score += scoreHints(text, "siemens", "s65", "cx65", "com/siemens", "com.siemens");
		} else if (PROFILE_SAMSUNG.equals(candidate.name)) {
			score += scoreHints(text, "samsung", "sgh", "d900", "e250", "com/samsung", "com.samsung");
		}
		if (text.contains("mascot") || text.contains(".m3g")) {
			score += 6;
		}
		if (text.contains("midp-1.0") && PROFILE_GENERIC.equals(candidate.name)) {
			score += 10;
		}
		if (text.contains("gameloft") && PROFILE_NOKIA.equals(candidate.name)) {
			score += 10;
		}
		return score;
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
				rememberScreenCandidate(probe, "declared-resolution", width, height, bonus);
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private static void rememberScreenCandidate(Probe probe, String name, int width, int height, int bonus) {
		if (width < 96 || height < 96 || width > 640 || height > 640) {
			return;
		}
		int score = scoreScreenCandidate(name, width, height) + bonus;
		if (score > probe.screenScore) {
			probe.screenScore = score;
			probe.screenWidth = width;
			probe.screenHeight = height;
		}
	}

	private static int scoreScreenCandidate(String name, int width, int height) {
		int area = width * height;
		int score = area / 512;
		if (isKnownJ2meResolution(width, height)) {
			score += 180;
		}
		if (name.contains("splash") || name.contains("title") || name.contains("menu")
				|| name.contains("background") || name.contains("loading")) {
			score += 160;
		}
		if (name.contains("font") || name.contains("sprite") || name.contains("icon")
				|| name.contains("button") || name.contains("tile") || name.contains("digit")
				|| name.contains("logo")) {
			score -= 180;
		}
		float ratio = width / (float) height;
		if ((ratio >= 0.45f && ratio <= 0.85f) || (ratio >= 1.2f && ratio <= 2.2f)) {
			score += 40;
		}
		return score;
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
				|| matchesResolution(width, height, 360, 640);
	}

	private static boolean matchesResolution(int width, int height, int expectedWidth, int expectedHeight) {
		return (width == expectedWidth && height == expectedHeight)
				|| (width == expectedHeight && height == expectedWidth);
	}

	private static void applyDisplayDefaults(ProfileModel params, Probe probe) {
		if (probe.screenWidth > 0 && probe.screenHeight > 0) {
			params.screenWidth = probe.screenWidth;
			params.screenHeight = probe.screenHeight;
		}
		params.screenScaleType = 1;
		params.screenScaleRatio = 100;
		params.screenGravity = 2;
		params.forceFullscreen = true;
		params.orientation = 3;
	}

	private static int scoreHints(String text, String... hints) {
		int score = 0;
		for (String hint : hints) {
			if (text.contains(hint)) {
				score += 30;
			}
		}
		return score;
	}

	private static boolean hasAttrPrefix(Probe probe, String prefix) {
		for (String key : probe.attrs.keySet()) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
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
			reasons.add("resources suggest " + probe.screenWidth + "x" + probe.screenHeight);
		}
		String hints = probe.manifestText + '\n' + probe.resourceText;
		if (hints.contains("nokia")) reasons.add("Nokia APIs/resources detected");
		if (hints.contains("sony") || hints.contains("jbed")) reasons.add("Sony Ericsson hints detected");
		if (hints.contains("siemens")) reasons.add("Siemens APIs detected");
		if (reasons.isEmpty()) reasons.add("standard MIDP metadata");
		return android.text.TextUtils.join(", ", reasons);
	}

	private static final class Candidate {
		final String name;
		final LinkedHashMap<String, String> properties;
		int score;

		private Candidate(String name, int score, LinkedHashMap<String, String> properties) {
			this.name = name;
			this.score = score;
			this.properties = properties;
		}
	}

	private static final class Probe {
		Map<String, String> attrs = new LinkedHashMap<>();
		String manifestText = "";
		String resourceText = "";
		int screenWidth;
		int screenHeight;
		int screenScore;
	}
}
