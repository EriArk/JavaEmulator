package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import org.junit.Test;
import ru.playsoftware.j2meloader.util.Screenshots;
import static org.junit.Assert.*;

public class ScreenshotTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void visibilitySettingRoundTripsThroughProvider() {
        boolean before = Screenshots.isButtonVisible(context);
        try {
            Screenshots.setButtonVisible(context, false);
            assertFalse(Screenshots.isButtonVisible(context.createConfigurationContext(context.getResources().getConfiguration())));
            Screenshots.setButtonVisible(context, true);
            assertTrue(Screenshots.isButtonVisible(context));
        } finally { Screenshots.setButtonVisible(context, before); }
    }

    @Test public void pngIsPublishedInGalleryWithExactPixelsAndUniqueName() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED); bitmap.setPixel(0,0,Color.BLUE);
        Uri first = null, second = null;
        try {
            first = Screenshots.save(context, bitmap);
            second = Screenshots.save(context, bitmap);
            assertNotEquals(first, second);
            try (Cursor cursor = context.getContentResolver().query(first, new String[]{
                    MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.MIME_TYPE}, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0, cursor.getInt(0));
                assertEquals("Pictures/AbyssME/", cursor.getString(1));
                assertEquals("image/png", cursor.getString(2));
            }
            try (InputStream in = context.getContentResolver().openInputStream(first)) {
                Bitmap decoded = BitmapFactory.decodeStream(in);
                assertNotNull(decoded);
                assertEquals(8, decoded.getWidth()); assertEquals(12, decoded.getHeight());
                assertEquals(Color.BLUE, decoded.getPixel(0,0));
                assertEquals(Color.RED, decoded.getPixel(7,11));
                decoded.recycle();
            }
        } finally {
            bitmap.recycle();
            if (first != null) context.getContentResolver().delete(first,null,null);
            if (second != null) context.getContentResolver().delete(second,null,null);
        }
    }

    private int pendingCount() {
        android.os.Bundle args = new android.os.Bundle();
        args.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
        args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "is_pending=1 AND relative_path=?");
        args.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                new String[]{"Pictures/AbyssME/"});
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, args, null)) { return cursor.getCount(); }
    }

    @Test public void failedWriteRemovesPendingGalleryEntry() throws Exception {
        int before = pendingCount();
        Bitmap recycled = Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888);
        recycled.recycle();
        try { Screenshots.save(context,recycled); fail("Recycled frame should fail"); }
        catch (IllegalStateException expected) { }
        assertEquals(before,pendingCount());
    }
}
