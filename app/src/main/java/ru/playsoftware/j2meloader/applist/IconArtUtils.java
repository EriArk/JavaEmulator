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
