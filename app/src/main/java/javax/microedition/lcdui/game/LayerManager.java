/*
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;

public class LayerManager {
	private int nlayers;
	private Layer[] component = new Layer[4];
	private Layer[] paintOrder;
	private int viewX, viewY, viewWidth, viewHeight;

	public LayerManager() {
		setViewWindow(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	public synchronized void append(Layer l) {
		// remove the Layer if it is already present
		// will throw NullPointerException if the Layer is null
		removeImpl(l);
		addImpl(l, nlayers);
	}

	public synchronized void insert(Layer l, int index) {
		if ((index < 0) || (index > nlayers) || (exist(l) && (index >= nlayers))) {
			throw new IndexOutOfBoundsException();
		}
		removeImpl(l);
		addImpl(l, index);
	}

	public synchronized Layer getLayerAt(int index) {
		if ((index < 0) || (index >= nlayers)) {
			throw new IndexOutOfBoundsException();
		}
		return component[index];
	}

	public synchronized int getSize() {
		return nlayers;
	}

	public synchronized void remove(Layer l) {
		removeImpl(l);
	}

	public void paint(Graphics g, int x, int y) {
		Layer[] frame;
		int vx, vy, width, height;
		synchronized (this) {
			if (paintOrder == null) {
				paintOrder = java.util.Arrays.copyOf(component, nlayers);
			}
			frame = paintOrder;
			vx = viewX;
			vy = viewY;
			width = viewWidth;
			height = viewHeight;
		}
		int clipX = g.getClipX();
		int clipY = g.getClipY();
		int clipW = g.getClipWidth();
		int clipH = g.getClipHeight();
		int originX = g.getTranslateX();
		int originY = g.getTranslateY();
		g.translate(x - vx, y - vy);
		try {
			g.clipRect(vx, vy, width, height);
			for (int i = frame.length - 1; i >= 0; i--) {
				Layer layer = frame[i];
				if (layer.visible) {
					layer.paint(g);
				}
			}
		} finally {
			g.translate(originX - g.getTranslateX(), originY - g.getTranslateY());
			g.setClip(clipX, clipY, clipW, clipH);
		}
	}

	public synchronized void setViewWindow(int x, int y, int width, int height) {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException();
		}

		viewX = x;
		viewY = y;
		viewWidth = width;
		viewHeight = height;
	}

	private void addImpl(Layer layer, int index) {
		if (nlayers == component.length) {
			Layer newcomponents[] = new Layer[nlayers + 4];
			System.arraycopy(component, 0, newcomponents, 0, nlayers);
			System.arraycopy(component, index, newcomponents,
					index + 1, nlayers - index);
			component = newcomponents;
		} else {
			System.arraycopy(component, index, component,
					index + 1, nlayers - index);
		}

		component[index] = layer;
		nlayers++;
		paintOrder = null;
	}

	private void removeImpl(Layer l) {
		if (l == null) {
			throw new NullPointerException();
		}

		for (int i = nlayers; --i >= 0; ) {
			if (component[i] == l) {
				remove(i);
			}
		}
	}

	private boolean exist(Layer l) {
		if (l == null) {
			return false;
		}

		for (int i = nlayers; --i >= 0; ) {
			if (component[i] == l) {
				return true;
			}
		}
		return false;
	}

	private void remove(int index) {
		System.arraycopy(component, index + 1,
				component, index,
				nlayers - index - 1);
		component[--nlayers] = null;
		paintOrder = null;
	}

}
