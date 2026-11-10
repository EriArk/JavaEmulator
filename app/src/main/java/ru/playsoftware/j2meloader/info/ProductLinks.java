// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.info;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import ru.playsoftware.j2meloader.R;

/** Official destinations, opened externally without dismissing the current app screen. */
final class ProductLinks {
    static final String WEBSITE = "https://abysstail.art/en/projects/abyssme";
    private ProductLinks() { }

    static boolean tryOpen(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
        Context owner = context;
        while (owner instanceof ContextWrapper && !(owner instanceof Activity)) {
            Context base = ((ContextWrapper) owner).getBaseContext();
            if (base == owner) break;
            owner = base;
        }
        if (!(owner instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); return true; }
        catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }

    static void open(Context context, String url) {
        if (tryOpen(context, url)) return;
        new AlertDialog.Builder(context).setTitle(R.string.product_link_unavailable)
                .setMessage(context.getString(R.string.product_link_help, url))
                .setPositiveButton(R.string.product_copy_link, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("AbyssME", url));
                })
                .setNegativeButton(R.string.helper_close, null).show();
    }
}
