/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui.game;

import org.junit.Test;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class LayerManagerTest {
	private static Layer layer(Runnable draw) {
		return new Layer(1, 1) {
			public void paint(Graphics graphics) {
				draw.run();
			}
		};
	}

	@Test public void gameThreadCanReplaceLayersDuringPaint() throws Exception {
		LayerManager manager = new LayerManager();
		StringBuilder order = new StringBuilder();
		CountDownLatch painting = new CountDownLatch(1);
		CountDownLatch changed = new CountDownLatch(1);
		Layer back = layer(() -> {
			order.append("B");
			painting.countDown();
			try {
				assertTrue(changed.await(3, TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
		});
		Layer front = layer(() -> order.append("F"));
		Layer replacement = layer(() -> order.append("R"));
		manager.append(front);
		manager.append(back);
		Thread game = new Thread(() -> {
			try {
				if (painting.await(3, TimeUnit.SECONDS)) {
					manager.remove(front);
					manager.remove(back);
					manager.append(replacement);
				}
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			} finally {
				changed.countDown();
			}
		});
		Graphics graphics = Image.createImage(8, 8).getGraphics();
		game.start();
		try {
			manager.paint(graphics, 0, 0);
		} finally {
			game.join(4000);
		}
		assertFalse(game.isAlive());
		manager.paint(graphics, 0, 0);
		assertEquals("BFR", order.toString());
	}
}
