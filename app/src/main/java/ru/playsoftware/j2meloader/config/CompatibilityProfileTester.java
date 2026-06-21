/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.config;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

		private Result(String profileName, int score) {
			this.profileName = profileName;
			this.score = score;
		}
	}

	private static final String PROFILE_GENERIC = "Generic MIDP";
	private static final String PROFILE_NOKIA = "Nokia S40";
	private static final String PROFILE_SONY_ERICSSON = "Sony Ericsson JP-8";
	private static final String PROFILE_MOTOROLA = "Motorola";
	private static final String PROFILE_SIEMENS = "Siemens";
	private static final String PROFILE_SAMSUNG = "Samsung";

	private CompatibilityProfileTester() {
	}

	static Result run(Context context, File appDir, ProfileModel params, Callback callback) {
		callback.onProgress(5, context.getString(R.string.compatibility_test_manifest));
		Probe probe = readProbe(appDir);
		sleepBriefly();

		callback.onProgress(18, context.getString(R.string.compatibility_test_resources));
		probe.resourceText = readResourceHints(appDir);
		sleepBriefly();

		ArrayList<Candidate> candidates = createCandidates(probe);
		Candidate best = null;
		for (int i = 0, size = candidates.size(); i < size; i++) {
			Candidate candidate = candidates.get(i);
			int progress = 25 + (i * 55 / Math.max(1, size));
			callback.onProgress(progress,
					context.getString(R.string.compatibility_test_profile, candidate.name));
			candidate.score += scoreCandidate(candidate, probe);
			if (best == null || candidate.score > best.score) {
				best = candidate;
			}
			sleepBriefly();
		}

		if (best == null) {
			best = candidates.get(0);
		}
		callback.onProgress(86, context.getString(R.string.compatibility_test_save, best.name));
		params.systemProperties = mergeProperties(params.systemProperties, best.properties);
		params.compatibilityProfile = best.name;
		params.compatibilityScore = best.score;
		params.compatibilityTested = true;
		params.touchInput = false;
		ProfilesManager.saveConfig(params);
		sleepBriefly();
		return new Result(best.name, best.score);
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
		} catch (Exception ignored) {
			probe.attrs = new LinkedHashMap<>();
			probe.manifestText = "";
		}
		return probe;
	}

	private static String readResourceHints(File appDir) {
		File resJar = new File(appDir, Config.MIDLET_RES_FILE);
		if (!resJar.exists()) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		try (ZipFile zip = new ZipFile(resJar)) {
			int count = 0;
			for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
				if (count++ > 700) {
					break;
				}
				text.append(entry.getName()).append('\n');
			}
		} catch (Exception ignored) {
		}
		return text.toString().toLowerCase(Locale.US);
	}

	private static ArrayList<Candidate> createCandidates(Probe probe) {
		String profile = firstNonEmpty(probe.attrs.get("MicroEdition-Profile"), "MIDP-2.0");
		String configuration = firstNonEmpty(probe.attrs.get("MicroEdition-Configuration"), "CLDC-1.1");
		ArrayList<Candidate> candidates = new ArrayList<>();
		candidates.add(new Candidate(PROFILE_GENERIC, 24, baseProperties(
				"J2ME-Loader/Auto", profile, configuration)));
		candidates.add(new Candidate(PROFILE_NOKIA, 48, nokiaProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_SONY_ERICSSON, 44,
				sonyEricssonProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_MOTOROLA, 34,
				baseProperties("Motorola V3x", profile, configuration)));
		candidates.add(new Candidate(PROFILE_SIEMENS, 32,
				siemensProperties(profile, configuration)));
		candidates.add(new Candidate(PROFILE_SAMSUNG, 32,
				baseProperties("SAMSUNG-SGH-D900", profile, configuration)));
		return candidates;
	}

	private static int scoreCandidate(Candidate candidate, Probe probe) {
		String text = probe.manifestText + '\n' + probe.resourceText;
		int score = 0;
		if (PROFILE_NOKIA.equals(candidate.name)) {
			score += scoreHints(text, "nokia", "s40", "series40", "series 40", "6233", "n73");
			if (hasAttrPrefix(probe, "Nokia-")) {
				score += 80;
			}
		} else if (PROFILE_SONY_ERICSSON.equals(candidate.name)) {
			score += scoreHints(text, "sony", "ericsson", "sonyericsson", "k800", "jp-8", "jp8");
		} else if (PROFILE_MOTOROLA.equals(candidate.name)) {
			score += scoreHints(text, "motorola", "moto", "v3", "v3x", "razr");
		} else if (PROFILE_SIEMENS.equals(candidate.name)) {
			score += scoreHints(text, "siemens", "s65", "cx65");
		} else if (PROFILE_SAMSUNG.equals(candidate.name)) {
			score += scoreHints(text, "samsung", "sgh", "d900", "e250");
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

	private static void sleepBriefly() {
		try {
			Thread.sleep(180);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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
	}
}
