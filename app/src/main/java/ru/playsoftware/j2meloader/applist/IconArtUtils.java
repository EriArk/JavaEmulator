/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package ru.playsoftware.j2meloader.applist;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public final class IconArtUtils {
	private static final int MIN_VISIBLE_ALPHA = 8;
	private static final int[][] FALLBACK_PALETTES = {
			{0xff11161a, 0xff24515a, 0xffffb62e},
			{0xff17151c, 0xff4c375f, 0xff65e05f},
			{0xff101820, 0xff34506b, 0xffffd166},
			{0xff181713, 0xff5b4931, 0xff49e5f2}
	};

	private IconArtUtils() {
	}

	public static Bitmap loadLargeIcon(String path, int targetSizePx) {
		if (path == null || targetSizePx <= 0) {
			return null;
		}
		Bitmap source = BitmapFactory.decodeFile(path);
		if (source == null) {
			return null;
		}
		Rect bounds = findVisibleBounds(source);
		if (bounds.isEmpty()) {
			bounds.set(0, 0, source.getWidth(), source.getHeight());
		}
		Bitmap result = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(result);
		Paint paint = new Paint();
		paint.setAntiAlias(false);
		paint.setFilterBitmap(false);
		paint.setDither(false);
		float scale = Math.min((float) targetSizePx / bounds.width(), (float) targetSizePx / bounds.height());
		float width = bounds.width() * scale;
		float height = bounds.height() * scale;
		float left = (targetSizePx - width) / 2f;
		float top = (targetSizePx - height) / 2f;
		canvas.drawBitmap(source, bounds, new RectF(left, top, left + width, top + height), paint);
		if (source != result) {
			source.recycle();
		}
		return result;
	}

	public static Bitmap createFallback(String title, int width, int height, Bitmap icon) {
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		Bitmap result = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(result);
		int hash = title == null ? 0 : title.hashCode();
		int[] palette = FALLBACK_PALETTES[(hash & 0x7fffffff) % FALLBACK_PALETTES.length];
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(palette[0]);
		canvas.drawRect(0, 0, safeWidth, safeHeight, paint);
		paint.setColor(palette[1]);
		int band = Math.max(8, safeHeight / 7);
		canvas.drawRect(0, safeHeight - band * 2, safeWidth, safeHeight - band, paint);
		int block = Math.max(10, Math.min(safeWidth, safeHeight) / 6);
		for (int x = Math.floorMod(hash, block) - block; x < safeWidth; x += block * 3) {
			canvas.drawRect(x, 0, x + block, band, paint);
		}
		paint.setColor(palette[2]);
		canvas.drawRect(0, safeHeight - Math.max(3, safeHeight / 35), safeWidth, safeHeight, paint);
		if (icon != null) {
			int size = Math.min(safeHeight * 2 / 3, safeWidth / 2);
			int left = (safeWidth - size) / 2;
			int top = (safeHeight - size) / 2;
			paint.setFilterBitmap(false);
			canvas.drawBitmap(icon, null, new Rect(left, top, left + size, top + size), paint);
		}
		return result;
	}

	private static Rect findVisibleBounds(Bitmap bitmap) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int left = width;
		int top = height;
		int right = -1;
		int bottom = -1;
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			bitmap.getPixels(row, 0, width, 0, y, width, 1);
			for (int x = 0; x < width; x++) {
				if (((row[x] >>> 24) & 0xff) > MIN_VISIBLE_ALPHA) {
					if (x < left) left = x;
					if (x > right) right = x;
					if (y < top) top = y;
					if (y > bottom) bottom = y;
				}
			}
		}
		if (right < left || bottom < top) {
			return new Rect();
		}
		return new Rect(left, top, right + 1, bottom + 1);
	}
}
