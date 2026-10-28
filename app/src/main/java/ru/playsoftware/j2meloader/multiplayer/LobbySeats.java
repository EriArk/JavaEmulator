/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import com.google.gson.*;
import java.util.Locale;
import java.util.function.Consumer;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Member identity and status come only from the room snapshot. No inferred presence. */
final class LobbySeats {
    static void add(LinearLayout parent, LobbyRoomModel.State state, Consumer<String> memberAction, Runnable invite) {
        Context context = parent.getContext();
        JsonArray members = state.snapshot.has("members") ? state.snapshot.getAsJsonArray("members") : new JsonArray();
        boolean empty = state.hosting && members.size() < (state.snapshot.has("game") ? 2 : 16);
        int columns = context.getResources().getConfiguration().screenWidthDp >= 340
                && context.getResources().getConfiguration().fontScale <= 1.25f ? 2 : 1;
        LinearLayout line = null;
        for (int i = 0; i < members.size() + (empty ? 1 : 0); i++) {
            if (i % columns == 0) {
                line = new SeatRow(context); line.setBaselineAligned(false);
                parent.addView(line, new LinearLayout.LayoutParams(-1, -2));
            }
            final boolean vacant = i == members.size();
            JsonObject member = vacant ? new JsonObject() : members.get(i).getAsJsonObject();
            String id = text(member, "id"), name = vacant ? context.getString(R.string.social_invite_player) : state.names.getOrDefault(id, id);
            String status = vacant ? context.getString(R.string.social_empty_seat) : !bool(member,"connected") || !state.available ? context.getString(R.string.social_reconnecting)
                    : bool(member,"ready") ? context.getString(R.string.social_ready) : bool(member,"prepared") ? context.getString(R.string.social_prepared) : context.getString(R.string.social_waiting_game);
            LinearLayout seat = new LinearLayout(context); seat.setOrientation(LinearLayout.VERTICAL);
            seat.setGravity(Gravity.CENTER); seat.setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 14));
            seat.setBackground(new ConsoleSurface(context, ConsoleSurface.Kind.CARD));
            seat.setTag(vacant ? "empty-seat" : "member:" + id);
            seat.setFocusable(true); seat.setClickable(true); seat.setDefaultFocusHighlightEnabled(false);
            seat.setEnabled(!vacant || state.available && !state.snapshot.has("launch"));
            String roleName=context.getString(id.equals(state.host)?R.string.social_host:R.string.social_player);
            seat.setContentDescription(context.getString(R.string.social_seat_accessibility,i+1,name,roleName,status));
            seat.setOnClickListener(v -> { if (vacant) invite.run(); else memberAction.accept(id); });
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1);
            cell.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 6)); line.addView(seat, cell);
            TextView badge = label(context, vacant ? "+" : initials(name), 22);
            badge.setGravity(Gravity.CENTER); badge.setTypeface(null, Typeface.BOLD);
            GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL);
            circle.setColor(AppPalette.color(context, R.color.input_surface));
            circle.setStroke(dp(context, 1), AppPalette.color(context, R.color.accent)); badge.setBackground(circle);
            android.graphics.Paint.FontMetrics metrics=badge.getPaint().getFontMetrics();
            int badgeSize=Math.max(dp(context,48),Math.max(
                    (int)Math.ceil(metrics.bottom-metrics.top)+dp(context,8),
                    (int)Math.ceil(badge.getPaint().measureText(badge.getText().toString()))+dp(context,12)));
            seat.addView(badge, new LinearLayout.LayoutParams(badgeSize,badgeSize));
            TextView title = label(context, name, 16); title.setGravity(Gravity.CENTER); title.setTypeface(null, Typeface.BOLD);
            title.setPadding(0, dp(context, 8), 0, 0); seat.addView(title, new LinearLayout.LayoutParams(-1,-2));
            TextView role = label(context, context.getString(R.string.social_seat_role,i+1,roleName), 12);
            role.setGravity(Gravity.CENTER); seat.addView(role, new LinearLayout.LayoutParams(-1,-2));
            TextView phase = label(context, status, 14); phase.setGravity(Gravity.CENTER);
            phase.setPadding(0, dp(context, 6), 0, 0); seat.addView(phase, new LinearLayout.LayoutParams(-1,-2));
            if (bool(member, "ready")) phase.setTextColor(AppPalette.color(context,R.color.phone_green));
            for (int child=0; child<seat.getChildCount(); child++) seat.getChildAt(child).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (line != null && columns == 2 && line.getChildCount() == 1) line.addView(new View(context),new LinearLayout.LayoutParams(0,1,1));
    }
    static String initials(String name) {
        String value = name.trim(); if(value.isEmpty()) return "?";
        String[] words = value.split("\\s+");
        String first = new String(Character.toChars(words[0].codePointAt(0)));
        return (first + (words.length > 1 ? new String(Character.toChars(words[words.length-1].codePointAt(0))) : "")).toUpperCase(Locale.ROOT);
    }
    private static final class SeatRow extends LinearLayout {
        SeatRow(Context context) { super(context); }
        @Override protected void onMeasure(int width, int height) {
            super.onMeasure(width, height);
            // First measure wrapping text, then equalize seats without MATCH_PARENT's
            // zero-height first pass inside an unbounded ScrollView.
            for (int i=0; i<getChildCount(); i++) {
                View child=getChildAt(i); LayoutParams lp=(LayoutParams)child.getLayoutParams();
                child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(),MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(0,getMeasuredHeight()-lp.topMargin-lp.bottomMargin),MeasureSpec.EXACTLY));
            }
        }
    }
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private static TextView label(Context c,String text,int size){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(AppPalette.color(c,R.color.text_primary));return v;}
}
