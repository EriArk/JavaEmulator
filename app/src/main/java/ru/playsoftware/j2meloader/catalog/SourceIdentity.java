/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.catalog;

import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public final class SourceIdentity {
	private SourceIdentity() {
	}

	public static String key(Uri uri) {
		if (uri == null) {
			return null;
		}
		if ("file".equalsIgnoreCase(uri.getScheme())) {
			try {
				return Uri.fromFile(new File(uri.getPath()).getCanonicalFile()).toString();
			} catch (IOException ignored) {
				return uri.normalizeScheme().toString();
			}
		}
		return uri.normalizeScheme().buildUpon().fragment(null).build().toString();
	}

	public static String sha256(File file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[64 * 1024];
			try (FileInputStream input = new FileInputStream(file)) {
				int read;
				while ((read = input.read(buffer)) != -1) {
					digest.update(buffer, 0, read);
				}
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	public static String shortId(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder(16);
			for (int i = 0; i < 8; i++) {
				result.append(String.format("%02x", hash[i] & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}
}
