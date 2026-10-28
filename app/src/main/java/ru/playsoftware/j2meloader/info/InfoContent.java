// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.info;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;

/** Current product copy; upstream attribution remains separate from product support. */
final class InfoContent {
    private InfoContent() { }
    static CharSequence about(Context context) {
        return Html.fromHtml(context.getString(R.string.product_about,
                TextUtils.htmlEncode(BuildConfig.VERSION_NAME)),Html.FROM_HTML_MODE_LEGACY);
    }
    static String overview(Context context) {
        return context.getString(BuildConfig.SYMBIAN_EXPERIMENT?R.string.product_overview_combined:R.string.product_overview_java);
    }
    static String help(Context context) {
        StringBuilder text=new StringBuilder(overview(context));
        for(int key:new int[]{R.string.product_help_library,R.string.product_help_controls,
                R.string.product_help_picture,R.string.product_help_transfer})
            text.append("\n\n").append(context.getString(key));
        if(BuildConfig.SYMBIAN_EXPERIMENT)text.append("\n\n").append(context.getString(R.string.product_help_systems));
        text.append("\n\n").append(context.getString(R.string.product_help_online));
        return text.toString();
    }
    static ScrollView view(Context context,CharSequence content) {
        ScrollView scroll=new ScrollView(context);
        TextView text=new TextView(context);
        text.setText(content);text.setTextSize(16);text.setTextIsSelectable(true);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        int padding=Math.round(20*context.getResources().getDisplayMetrics().density);
        text.setPadding(padding,padding,padding,padding);
        scroll.addView(text,new ScrollView.LayoutParams(-1,-2));
        return scroll;
    }
}
