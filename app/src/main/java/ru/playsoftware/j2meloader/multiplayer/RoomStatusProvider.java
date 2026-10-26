/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Read-only main-process projection. Never constructs a room owner in :midlet. */
public final class RoomStatusProvider extends ContentProvider {
    private static volatile boolean active;
    static void update(boolean value) { active = value; }
    public static boolean active(Context context) {
        try {
            Bundle state = context.getContentResolver().call(
                    Uri.parse("content://" + context.getPackageName() + ".roomStatus"), "active", null, null);
            return state != null && state.getBoolean("active");
        } catch (RuntimeException unavailable) { return false; }
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"active".equals(method)) throw new IllegalArgumentException("Read-only room status");
        Bundle result = new Bundle(); result.putBoolean("active", active); return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
