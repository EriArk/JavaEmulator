package ru.playsoftware.j2meloader.mophun;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.input.TouchDeck;

/** Phone/gamepad touch routing, independent of the physical controller mapper. */
final class MophunTouchControls {
    final TouchDeck deck;
    private final Runnable gameTap;
    private final float slop;
    private final RectF area = new RectF();
    private int tapPointer = -1;
    private float downX, downY;

    MophunTouchControls(ProfileModel profile, float density, float slop,
                        ControllerInput.Sink input, Runnable invalidate, Runnable gameTap) {
        this.gameTap = gameTap;
        this.slop = slop;
        deck = new TouchDeck(profile, density, new TouchDeck.Sink() {
            public void press(int key) { input.press(key); }
            public void release(int key) { input.release(key); }
            public void repeat(int key) { input.repeat(key); }
            public void invalidate() { invalidate.run(); }
        });
    }

    void layout(int width, int height) {
        cancel();
        deck.layout(width, height);
        area.set(deck.gameArea(width, height));
    }

    RectF gameArea() { return new RectF(area); }
    void draw(Canvas canvas) { deck.draw(canvas); }
    void cancel() { tapPointer = -1; deck.cancel(); }

    boolean touch(MotionEvent event) {
        int index = event.getActionIndex(), id = event.getPointerId(index);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancel();
                downX = event.getX(); downY = event.getY();
                if (!deck.down(id, downX, downY) && area.contains(downX, downY)) tapPointer = id;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                tapPointer = -1;
                deck.down(id, event.getX(index), event.getY(index));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int p = 0; p < event.getPointerCount(); p++) {
                    deck.move(event.getPointerId(p), event.getX(p), event.getY(p));
                    if (event.getPointerId(p) == tapPointer &&
                            Math.hypot(event.getX(p) - downX, event.getY(p) - downY) > slop) tapPointer = -1;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                deck.up(id);
                tapPointer = -1;
                break;
            case MotionEvent.ACTION_UP:
                deck.up(id);
                boolean click = tapPointer == id && area.contains(event.getX(), event.getY()) &&
                        Math.hypot(event.getX() - downX, event.getY() - downY) <= slop;
                cancel();
                if (click) gameTap.run();
                break;
            case MotionEvent.ACTION_CANCEL:
                cancel();
                break;
        }
        return true;
    }
}
