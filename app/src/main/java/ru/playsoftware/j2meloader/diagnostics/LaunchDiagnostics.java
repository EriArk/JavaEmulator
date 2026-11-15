/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.diagnostics;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

public final class LaunchDiagnostics {
	private LaunchDiagnostics() {
	}

	/** Reflection is a host detail; show the guest failure behind its wrapper. */
	public static String describeFailure(Throwable error) {
		for (int depth = 0; depth < 16
				&& error instanceof java.lang.reflect.InvocationTargetException; depth++) {
			Throwable cause = error.getCause();
			if (cause == null || cause == error) break;
			error = cause;
		}
		return error.toString();
	}

	public static synchronized void record(Context context, String event, String game, String detail) {
		try {
			JSONObject line = new JSONObject();
			line.put("time", System.currentTimeMillis());
			line.put("event", event);
			line.put("game", game == null ? JSONObject.NULL : game);
			line.put("detail", detail == null ? JSONObject.NULL : detail);
			File file = new File(context.getFilesDir(), "launch-diagnostics.jsonl");
			try (FileWriter writer = new FileWriter(file, true)) {
				writer.write(line.toString());
				writer.write('\n');
			}
		} catch (Exception ignored) {
		}
	}
}
