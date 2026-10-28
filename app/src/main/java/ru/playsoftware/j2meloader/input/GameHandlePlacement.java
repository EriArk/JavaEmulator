package ru.playsoftware.j2meloader.input;

import android.graphics.RectF;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Position within the safe travel range, not absolute screen coordinates. */
public final class GameHandlePlacement {
    private GameHandlePlacement() { }

    public static RectF bounds(ProfileModel profile, boolean landscape, RectF safe, float size) {
        TouchPlacement placement = landscape ? profile.gameHandleLandscape : profile.gameHandlePortrait;
        float x = placement == null ? 1 : TouchPlacement.bounded(placement.x, 0, 1, 1);
        float y = placement == null ? 0 : TouchPlacement.bounded(placement.y, 0, 1, 0);
        float width = Math.min(size, Math.max(0, safe.width()));
        float height = Math.min(size, Math.max(0, safe.height()));
        float left = safe.left + x * (safe.width() - width);
        float top = safe.top + y * (safe.height() - height);
        return new RectF(left, top, left + width, top + height);
    }

    public static void move(ProfileModel profile, boolean landscape, RectF safe, float size, float dx, float dy) {
        RectF current = bounds(profile, landscape, safe, size);
        TouchPlacement placement = new TouchPlacement();
        float travelX = safe.width() - current.width(), travelY = safe.height() - current.height();
        placement.x = travelX <= 0 ? 1 : TouchPlacement.bounded((current.left - safe.left + dx) / travelX, 0, 1, 1);
        placement.y = travelY <= 0 ? 0 : TouchPlacement.bounded((current.top - safe.top + dy) / travelY, 0, 1, 0);
        if (landscape) profile.gameHandleLandscape = placement;
        else profile.gameHandlePortrait = placement;
    }
}
