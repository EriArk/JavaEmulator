// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.info;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;

/** Current product copy; upstream attribution remains separate from product support. */
final class InfoContent {
    private InfoContent() { }
    static CharSequence about(Context context) {
        android.text.SpannableString text=new android.text.SpannableString(Html.fromHtml(context.getString(R.string.product_about,
                TextUtils.htmlEncode(BuildConfig.VERSION_NAME + " · " + BuildConfig.DISTRIBUTION)),Html.FROM_HTML_MODE_LEGACY));
        for(android.text.style.URLSpan original:text.getSpans(0,text.length(),android.text.style.URLSpan.class)) {
            android.text.style.URLSpan link=new android.text.style.URLSpan(original.getURL()) {
                @Override public void onClick(android.view.View widget) { ProductLinks.open(context,getURL()); }
            };
            text.setSpan(link,text.getSpanStart(original),text.getSpanEnd(original),text.getSpanFlags(original));
            text.removeSpan(original);
        }
        return text;
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
    static ScrollView supportView(Context context,CharSequence content) {
        ScrollView scroll=view(context,content);
        android.view.View text=scroll.getChildAt(0); scroll.removeView(text);
        LinearLayout column=new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text,new LinearLayout.LayoutParams(-1,-2));
        Button website=new Button(context); website.setText(R.string.product_website);
        website.setAllCaps(false);
        website.setOnClickListener(v -> ProductLinks.open(context,ProductLinks.WEBSITE));
        int padding=Math.round(20*context.getResources().getDisplayMetrics().density);
        website.setMinHeight(Math.round(48*context.getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(-1,-2);
        button.setMargins(padding,0,padding,padding);
        column.addView(website,button); scroll.addView(column);
        return scroll;
    }
}
