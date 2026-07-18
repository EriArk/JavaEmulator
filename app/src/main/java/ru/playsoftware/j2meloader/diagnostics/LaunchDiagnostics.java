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
