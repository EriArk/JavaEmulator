package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.keyboard.KeyMapper;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Paused, transactional controller editor. No input from this view reaches a MIDlet. */
public final class ControllerMapperView extends LinearLayout {
    public interface Listener {
        void save(ArrayList<ProfileModel.KeyMappingProfile> profiles, int active);
        void cancel();
    }

    private static final int BG = 0xff0e1418, PANEL = 0xff192328, LINE = 0xff35464e;
    private static final int TEXT = 0xffedf1ee, MUTED = 0xffa9b9bd;
    private static final int AMBER = 0xffffc15a, CYAN = 0xff71d3d5;
    private static final int[] DPAD = {19, 20, 21, 22, -1011, -1012, -1013, -1014};
    private static final int[] STICK = {-1001, -1002, -1003, -1004, -1005, -1006, -1007, -1008};
    private static final String[] DIR_NAMES = {"Up", "Down", "Left", "Right",
            "Up + left", "Up + right", "Down + left", "Down + right"};
    private static final String[] DIR_ICONS = {"\u2191", "\u2193", "\u2190", "\u2192",
            "\u2196", "\u2197", "\u2199", "\u2198"};

    private final ArrayList<ProfileModel.KeyMappingProfile> profiles = new ArrayList<>();
    private final Map<Integer, View> rows = new LinkedHashMap<>();
    private final Map<Integer, TextView> values = new LinkedHashMap<>();
    private final Map<Integer, String> inputNames = new LinkedHashMap<>();
    private final Map<Integer, Button> phoneKeys = new LinkedHashMap<>();
    private final Set<Integer> capturedHeld = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseIntArray captureDpad = new SparseIntArray();
    private final Listener listener;
    private final ScrollView sourceScroll;
    private final LinearLayout sourceList;
    private final LinearLayout phone;
    private final TextView lcd;
    private final Spinner profilePicker;
    private final Button detect;
    private final Button saveAction;
    private int selected = KeyEvent.KEYCODE_BUTTON_A;
    private int active;
    private boolean listening;
    private boolean compactPhone;
    private int sourceGroup;
    private int buildingGroup;
    private final ArrayList<Button> groupTabs = new ArrayList<>();
    private ArrayList<ProfileModel.KeyMappingProfile> undoProfiles;
    private int undoActive;
    private Button undo;
    private int candidate;
    private int navigationDirection;
    private final Runnable repeatNavigation = new Runnable() {
        @Override public void run() {
            if (navigationDirection == 0 || listening) return;
            moveFocus(navigationDirection);
            handler.postDelayed(this, 180);
        }
    };
    private final Runnable captured = () -> {
        if (listening && candidate != 0) {
            int code = candidate;
            setListening(false);
            select(code, true);
        }
    };

    public ControllerMapperView(Context context, String game,
                                ArrayList<ProfileModel.KeyMappingProfile> original,
                                int activeIndex, Listener listener) {
        super(context);
        this.listener = listener;
        for (ProfileModel.KeyMappingProfile p : original) {
            profiles.add(new ProfileModel.KeyMappingProfile(p.name,
                    p.mappings == null ? null : p.mappings.clone()));
        }
        boolean hasEightWay = false;
        for (ProfileModel.KeyMappingProfile p : profiles) hasEightWay |= "8-way numpad".equals(p.name);
        if (!hasEightWay) profiles.add(new ProfileModel.KeyMappingProfile("8-way numpad", KeyMapper.getEightWayKeyMap()));
        active = Math.max(0, Math.min(activeIndex, profiles.size() - 1));
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setPadding(dp(16), dp(12), dp(16), dp(10));
        setFocusable(true);
        setClickable(true);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout title = new LinearLayout(context);
        title.setOrientation(VERTICAL);
        title.addView(text(context.getString(R.string.mapper_title), 21, TEXT));
        TextView subtitle = text(game, 12, MUTED);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        title.addView(subtitle);
        header.addView(title, new LayoutParams(0, dp(52), 1));
        profilePicker = new Spinner(context);
        profilePicker.setContentDescription("Control profile");
        profilePicker.setBackground(states(PANEL));
        header.addView(profilePicker, new LayoutParams(0, dp(44), 1.2f));
        Button duplicate = iconButton(android.R.drawable.ic_menu_add, R.string.mapper_duplicate);
        duplicate.setOnClickListener(v -> copyProfile());
        LayoutParams addParams = new LayoutParams(dp(40), dp(40));
        addParams.setMargins(dp(6), 0, 0, 0);
        header.addView(duplicate, addParams);
        addView(header);

        LinearLayout body = new LinearLayout(context);
        LayoutParams bodyParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
        bodyParams.setMargins(0, dp(12), 0, dp(10));
        addView(body, bodyParams);
        LinearLayout sources = new LinearLayout(context);
        sources.setOrientation(VERTICAL);
        sources.addView(section(R.string.mapper_controller));
        LinearLayout groups = new LinearLayout(context);
        String[] groupNames = {"Buttons", "D-pad", "Stick"};
        for (int i = 0; i < groupNames.length; i++) {
            final int group = i;
            Button tab = button(groupNames[i]);
            tab.setTextSize(12);
            tab.setPadding(0,0,0,0);
            tab.setOnClickListener(v -> showGroup(group));
            groups.addView(tab, new LayoutParams(0, dp(40), 1));
            groupTabs.add(tab);
        }
        sources.addView(groups);
        sourceScroll = new ScrollView(context);
        sourceScroll.setFillViewport(false);
        sourceList = new LinearLayout(context);
        sourceList.setOrientation(VERTICAL);
        sourceScroll.addView(sourceList);
        sources.addView(sourceScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        LayoutParams sourceParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        sourceParams.setMargins(0, 0, dp(16), 0);
        body.addView(sources, sourceParams);

        LinearLayout destination = new LinearLayout(context);
        destination.setOrientation(VERTICAL);
        destination.addView(section(R.string.mapper_phone));
        phone = new LinearLayout(context);
        phone.setOrientation(VERTICAL);
        phone.setPadding(dp(10), dp(10), dp(10), dp(10));
        phone.setBackground(shape(0xff111c21, 0xff4a5a60, 8));
        phone.setMinimumHeight(dp(350));
        ScrollView phoneScroll = new ScrollView(context);
        phoneScroll.setFillViewport(true);
        phoneScroll.addView(phone, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        destination.addView(phoneScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        body.addView(destination, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        lcd = text("", 13, 0xffcbe5c7);
        lcd.setTypeface(Typeface.MONOSPACE);
        lcd.setGravity(Gravity.CENTER);
        lcd.setMaxLines(2);
        lcd.setBackground(shape(0xff253a32, 0xff426151, 3));
        rebuildPhone();

        addSection("Buttons");
        addInput(96, "A", "A", 0xff9eda97);
        addInput(97, "B", "B", 0xffef9494);
        addInput(99, "X", "X", 0xff88ccef);
        addInput(100, "Y", "Y", 0xffedcf76);
        buildingGroup = 1;
        addSection("D-pad");
        for (int i = 0; i < DPAD.length; i++) addInput(DPAD[i], DIR_ICONS[i], "D-pad " + DIR_NAMES[i], CYAN);
        buildingGroup = 0;
        addSection("Shoulders & system");
        addInput(102, "L1", "L1", TEXT); addInput(103, "R1", "R1", TEXT);
        addInput(104, "L2", "L2", TEXT); addInput(105, "R2", "R2", TEXT);
        addInput(109, "\u2212", "Select", TEXT); addInput(108, "+", "Start", TEXT);
        addInput(106, "L3", "L3", TEXT); addInput(107, "R3", "R3", TEXT);
        buildingGroup = 2;
        addSection("Left stick");
        for (int i = 0; i < STICK.length; i++) addInput(STICK[i], DIR_ICONS[i], "Stick " + DIR_NAMES[i], CYAN);

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        detect = button(context.getString(R.string.mapper_detect));
        detect.setCompoundDrawables(tinted(android.R.drawable.ic_menu_search), null, null, null);
        detect.setCompoundDrawablePadding(dp(6));
        detect.setOnClickListener(v -> setListening(!listening));
        footer.addView(detect, new LayoutParams(0, dp(44), 1));
        Button unbind = iconButton(android.R.drawable.ic_menu_close_clear_cancel, R.string.mapper_unbind);
        unbind.setOnClickListener(v -> bind(ControllerInput.UNBOUND));
        LayoutParams unbindParams = new LayoutParams(dp(44), dp(44));
        unbindParams.setMargins(dp(6), 0, 0, 0);
        footer.addView(unbind, unbindParams);
        undo = iconButton(android.R.drawable.ic_menu_revert, R.string.mapper_undo);
        undo.setEnabled(false);
        undo.setOnClickListener(v -> {
            if (undoProfiles == null) return;
            profiles.clear(); profiles.addAll(undoProfiles); active = undoActive;
            undoProfiles = null; undo.setEnabled(false); refreshProfiles(); refresh();
        });
        footer.addView(undo, new LayoutParams(dp(40), dp(44)));
        Button cancel = button(context.getString(android.R.string.cancel));
        cancel.setOnClickListener(v -> listener.cancel());
        footer.addView(cancel, new LayoutParams(0, dp(44), 0.8f));
        Button save = button(context.getString(R.string.mapper_save));
        saveAction = save;
        save.setTextColor(AMBER);
        save.setOnClickListener(v -> { setListening(false); listener.save(profiles, active); });
        LayoutParams saveParams = new LayoutParams(0, dp(44), 1.3f);
        saveParams.setMargins(dp(6), 0, 0, 0);
        footer.addView(save, saveParams);
        addView(footer);

        refreshProfiles();
        profilePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                active = index;
                refresh();
            }
        });
        refresh();
        showGroup(0);
        rows.get(selected).requestFocusFromTouch();
    }

    private void refreshProfiles() {
        ArrayList<String> names = new ArrayList<>();
        for (ProfileModel.KeyMappingProfile profile : profiles) {
            String name = profile.name;
            if ("Native arrows".equals(name)) name = "Arrows";
            else if ("Numpad 2/4/6/8".equals(name)) name = "Number movement";
            else if ("8-way numpad".equals(name)) name = "Eight-way numbers";
            names.add(name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) label(position);
                view.setCompoundDrawablesWithIntrinsicBounds(null, null, tinted(android.R.drawable.arrow_down_float), null);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) { return label(position); }
            private View label(int position) {
                TextView label = text(getItem(position), 13, TEXT);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(12), 0, dp(12), 0);
                label.setBackground(states(PANEL));
                label.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
                return label;
            }
        };
        profilePicker.setAdapter(adapter);
        profilePicker.setSelection(active);
    }

    private void copyProfile() {
        ProfileModel.KeyMappingProfile current = profiles.get(active);
        HashSet<String> names = new HashSet<>();
        for (ProfileModel.KeyMappingProfile profile : profiles) names.add(profile.name);
        int suffix = 1;
        while (names.contains("Custom " + suffix)) suffix++;
        profiles.add(new ProfileModel.KeyMappingProfile("Custom " + suffix,
                KeyMapper.resolveMappings(current.mappings)));
        active = profiles.size() - 1;
        refreshProfiles();
        refresh();
    }

    private void bind(int key) {
        undoProfiles = new ArrayList<>();
        for (ProfileModel.KeyMappingProfile p : profiles) undoProfiles.add(
                new ProfileModel.KeyMappingProfile(p.name, p.mappings == null ? null : p.mappings.clone()));
        undoActive = active;
        undo.setEnabled(true);
        setListening(false);
        String name = profiles.get(active).name;
        if ("Native arrows".equals(name) || "Numpad 2/4/6/8".equals(name)
                || "Numpad diagonals".equals(name) || "Action digits".equals(name) || "8-way numpad".equals(name)) {
            copyProfile();
        }
        SparseIntArray mappings = KeyMapper.resolveMappings(profiles.get(active).mappings);
        mappings.put(selected, key);
        profiles.get(active).mappings = mappings;
        refresh();
    }

    private void select(int code, boolean scroll) {
        if (!rows.containsKey(code)) {
            addInput(code, "\u2022", KeyEvent.keyCodeToString(code).replace("KEYCODE_", ""), TEXT);
        }
        selected = code;
        showGroup((Integer) rows.get(code).getTag());
        refresh();
        if (scroll) sourceScroll.post(() -> sourceScroll.smoothScrollTo(0, rows.get(code).getTop()));
    }

    private void refresh() {
        SparseIntArray mappings = KeyMapper.resolveMappings(profiles.get(active).mappings);
        for (Map.Entry<Integer, View> row : rows.entrySet()) {
            int code = row.getKey();
            int value = mappings.get(code, ControllerInput.UNBOUND);
            String label = ControllerInput.isDiagonal(code) && mappings.indexOfKey(code) < 0
                    ? getContext().getString(R.string.mapper_combined) : keyName(value);
            values.get(code).setText(label);
            row.getValue().setSelected(code == selected);
        }
        int key = mappings.get(selected, ControllerInput.UNBOUND);
        lcd.setText(inputNames.get(selected) + "\n" + values.get(selected).getText());
        for (Map.Entry<Integer, Button> entry : phoneKeys.entrySet()) entry.getValue().setSelected(entry.getKey() == key);
    }

    private String keyName(int key) {
        if (key == ControllerInput.UNBOUND) return getContext().getString(R.string.mapper_unbound);
        switch (key) {
            case Canvas.KEY_SOFT_LEFT: return "Soft left";
            case Canvas.KEY_SOFT_RIGHT: return "Soft right";
            case Canvas.KEY_UP: return "Up";
            case Canvas.KEY_DOWN: return "Down";
            case Canvas.KEY_LEFT: return "Left";
            case Canvas.KEY_RIGHT: return "Right";
            case Canvas.KEY_FIRE: return "OK / Fire";
            case Canvas.KEY_CLEAR: return "Clear";
            case Canvas.KEY_END: return "End call";
            case 0: return "Menu";
            default: return key > 0 && key < 128 ? Character.toString((char) key) : Integer.toString(key);
        }
    }

    private void addSection(String label) {
        TextView heading = text(label, 11, MUTED);
        heading.setTag(buildingGroup);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        sourceList.addView(heading, new LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));
    }

    private void addInput(int code, String icon, String name, int color) {
        LinearLayout row = new LinearLayout(getContext());
        row.setTag(buildingGroup);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.setBackground(states(PANEL));
        row.setFocusable(true);
        row.setClickable(true);
        row.setContentDescription(name);
        TextView badge = text(icon, icon.length() > 1 ? 12 : 20, color);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setBackground(shape(0xff0d171c, LINE, icon.length() > 1 ? 5 : 18));
        row.addView(badge, new LayoutParams(dp(36), dp(36)));
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        TextView title = text(name, 13, TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        TextView value = text("", 11, CYAN);
        labels.addView(value);
        row.addView(labels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = text("\u2192", 16, MUTED);
        row.addView(arrow);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, 0, 0, dp(4));
        sourceList.addView(row, lp);
        row.setOnClickListener(v -> {
            setListening(false);
            select(code, false);
            phoneKeys.get(Canvas.KEY_FIRE).requestFocusFromTouch();
        });
        rows.put(code, row);
        values.put(code, value);
        inputNames.put(code, name);
    }

    private void rebuildPhone() {
        phone.removeAllViews();
        phoneKeys.clear();
        phone.setMinimumHeight(dp(compactPhone ? 192 : 350));
        phone.addView(lcd, new LayoutParams(LayoutParams.MATCH_PARENT, dp(compactPhone ? 32 : 42)));
        LinearLayout navigation = phone, digits = phone;
        if (compactPhone) {
            LinearLayout keys = new LinearLayout(getContext());
            phone.addView(keys, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
            navigation = new LinearLayout(getContext());
            navigation.setOrientation(VERTICAL);
            digits = new LinearLayout(getContext());
            digits.setOrientation(VERTICAL);
            LayoutParams navParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            navParams.setMargins(0, 0, dp(10), 0);
            keys.addView(navigation, navParams);
            keys.addView(digits, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.2f));
        }
        addPhoneRow(navigation, new int[]{Canvas.KEY_SOFT_LEFT, Canvas.KEY_UP, Canvas.KEY_SOFT_RIGHT},
                new String[]{"\u2014", "\u2191", "\u2014"});
        addPhoneRow(navigation, new int[]{Canvas.KEY_LEFT, Canvas.KEY_FIRE, Canvas.KEY_RIGHT},
                new String[]{"\u2190", "OK", "\u2192"});
        addPhoneRow(navigation, new int[]{Canvas.KEY_CLEAR, Canvas.KEY_DOWN, Canvas.KEY_END},
                new String[]{"C", "\u2193", "End"});
        addPhoneRow(digits, new int[]{49, 50, 51}, new String[]{"1\n\u00b7", "2\nABC", "3\nDEF"});
        addPhoneRow(digits, new int[]{52, 53, 54}, new String[]{"4\nGHI", "5\nJKL", "6\nMNO"});
        addPhoneRow(digits, new int[]{55, 56, 57}, new String[]{"7\nPQRS", "8\nTUV", "9\nWXYZ"});
        addPhoneRow(digits, new int[]{42, 48, 35}, new String[]{"*", "0\n+", "#"});
        if (!rows.isEmpty()) refresh();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean compact = MeasureSpec.getSize(heightSpec) < dp(450) && MeasureSpec.getSize(widthSpec) >= dp(560);
        if (compact != compactPhone) {
            compactPhone = compact;
            rebuildPhone();
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private void addPhoneRow(LinearLayout container, int[] keys, String[] labels) {
        LinearLayout row = new LinearLayout(getContext());
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
        rowParams.setMargins(0, dp(5), 0, 0);
        container.addView(row, rowParams);
        for (int i = 0; i < keys.length; i++) {
            final int key = keys[i];
            Button button = button(labels[i]);
            button.setTextSize(compactPhone ? 15 : 19);
            button.setPadding(0, 0, 0, 0);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription("Phone key " + keyName(key));
            button.setTooltipText(keyName(key));
            button.setBackground(states(0xff28363d));
            if (labels[i].contains("\n")) {
                SpannableString styled = new SpannableString(labels[i]);
                styled.setSpan(new RelativeSizeSpan(0.43f), labels[i].indexOf('\n') + 1, labels[i].length(), 0);
                button.setText(styled);
                button.setLineSpacing(0, 0.85f);
            }
            button.setOnClickListener(v -> bind(key));
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (i != 0) lp.setMargins(dp(5), 0, 0, 0);
            row.addView(button, lp);
            phoneKeys.put(key, button);
        }
    }

    public boolean handleKey(KeyEvent event) {
        int key = event.getKeyCode();
        if (capturedHeld.contains(key)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                capturedHeld.remove(key);
                captureDpad.put(event.getDeviceId(), captureDpad.get(event.getDeviceId()) & ~ControllerInput.direction(key));
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) listener.cancel();
            return true;
        }
        if (listening && (KeyEvent.isGamepadButton(key) || ControllerInput.direction(key) != 0)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                capturedHeld.add(key);
                int mask = captureDpad.get(event.getDeviceId()) | ControllerInput.direction(key);
                captureDpad.put(event.getDeviceId(), mask);
                int diagonal = ControllerInput.diagonal(mask, false);
                candidate = diagonal != 0 ? diagonal : key;
                handler.removeCallbacks(captured);
                handler.postDelayed(captured, 120);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                showGroup((sourceGroup + (key == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : 2)) % 3);
                groupTabs.get(sourceGroup).requestFocusFromTouch();
            }
            return true;
        }
        if (ControllerInput.direction(key) != 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int direction = key == 19 ? View.FOCUS_UP : key == 20 ? View.FOCUS_DOWN
                        : key == 21 ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
                moveFocus(direction);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (key == KeyEvent.KEYCODE_BUTTON_A) {
                    View focus = findFocus();
                    if (focus == null || focus == this) focus = rows.get(selected);
                    focus.performClick();
                } else if (isInPhone(findFocus())) rows.get(selected).requestFocusFromTouch();
                else listener.cancel();
            }
            return true;
        }
        return false;
    }

    private void moveFocus(int direction) {
        View focus = findFocus();
        if (focus == null || focus == this) {
            rows.get(selected).requestFocusFromTouch();
            return;
        }
        View next = FocusFinder.getInstance().findNextFocus(this, focus, direction);
        if (next != null) next.requestFocusFromTouch();
    }

    private void showGroup(int group) {
        sourceGroup = group;
        for (int i = 0; i < sourceList.getChildCount(); i++) {
            View child = sourceList.getChildAt(i);
            child.setVisibility(Integer.valueOf(group).equals(child.getTag()) ? VISIBLE : GONE);
        }
        for (int i = 0; i < groupTabs.size(); i++) groupTabs.get(i).setSelected(i == group);
        if (!Integer.valueOf(group).equals(rows.get(selected).getTag())) {
            for (Map.Entry<Integer,View> entry : rows.entrySet()) {
                if (Integer.valueOf(group).equals(entry.getValue().getTag())) {
                    selected = entry.getKey(); break;
                }
            }
            refresh();
        }
    }

    private boolean isInPhone(View focus) {
        while (focus != null) {
            if (focus == phone) return true;
            focus = focus.getParent() instanceof View ? (View) focus.getParent() : null;
        }
        return false;
    }

    public void captureAxes(MotionEvent event) {
        if (!listening) {
            int mask = ControllerInput.axisMask(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0);
            int direction = (mask & ControllerInput.UP) != 0 ? View.FOCUS_UP
                    : (mask & ControllerInput.DOWN) != 0 ? View.FOCUS_DOWN
                    : (mask & ControllerInput.LEFT) != 0 ? View.FOCUS_LEFT
                    : (mask & ControllerInput.RIGHT) != 0 ? View.FOCUS_RIGHT : 0;
            if (direction != navigationDirection) {
                navigationDirection = direction;
                handler.removeCallbacks(repeatNavigation);
                if (direction != 0) {
                    moveFocus(direction);
                    handler.postDelayed(repeatNavigation, 350);
                }
            }
            return;
        }
        int stick = ControllerInput.axisMask(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0);
        int hat = ControllerInput.axisMask(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0);
        int mask = hat != 0 ? hat : stick;
        int input = ControllerInput.diagonal(mask, hat == 0);
        if (input == 0) {
            int[] codes = hat != 0 ? DPAD : STICK;
            if ((mask & ControllerInput.UP) != 0) input = codes[0];
            else if ((mask & ControllerInput.DOWN) != 0) input = codes[1];
            else if ((mask & ControllerInput.LEFT) != 0) input = codes[2];
            else if ((mask & ControllerInput.RIGHT) != 0) input = codes[3];
        }
        if (input == 0 && Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.55f) input = 104;
        if (input == 0 && Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.55f) input = 105;
        if (input != 0 && input != candidate) {
            candidate = input;
            handler.removeCallbacks(captured);
            handler.postDelayed(captured, 120);
        }
    }

    private void setListening(boolean enabled) {
        navigationDirection = 0;
        handler.removeCallbacks(repeatNavigation);
        listening = enabled;
        candidate = 0;
        captureDpad.clear();
        handler.removeCallbacks(captured);
        detect.setText(enabled ? R.string.mapper_listening : R.string.mapper_detect);
        detect.setSelected(enabled);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(() -> rows.get(selected).requestFocusFromTouch());
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            navigationDirection = 0;
            handler.removeCallbacks(repeatNavigation);
        }
    }

    private TextView section(int title) {
        TextView label = text(getContext().getString(title), 11, CYAN);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(25)));
        return label;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setLetterSpacing(0);
        return view;
    }

    public void setSaveLabel(String label) { saveAction.setText(label); }

    private Button button(String label) {
        Button view = new Button(getContext());
        view.setText(label);
        view.setTextSize(13);
        view.setLetterSpacing(0);
        view.setAllCaps(false);
        view.setMinHeight(0); view.setMinimumHeight(0);
        view.setMinWidth(0); view.setMinimumWidth(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setTextColor(TEXT);
        view.setBackgroundTintList(null);
        view.setBackground(states(PANEL));
        view.setFocusable(true);
        return view;
    }

    private Button iconButton(int icon, int title) {
        Button view = button("");
        view.setContentDescription(getContext().getString(title));
        view.setTooltipText(getContext().getString(title));
        view.setPadding(dp(10),0,dp(10),0);
        view.setCompoundDrawables(tinted(icon), null, null, null);
        return view;
    }

    private android.graphics.drawable.Drawable tinted(int resource) {
        android.graphics.drawable.Drawable icon = getContext().getDrawable(resource).mutate();
        icon.setTint(MUTED);
        icon.setBounds(0,0,dp(22),dp(22));
        return icon;
    }

    private StateListDrawable states(int color) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, shape(0xff51412a, AMBER, 6));
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xff293a40, CYAN, 6));
        states.addState(new int[]{android.R.attr.state_selected}, shape(0xff3a3221, AMBER, 6));
        states.addState(new int[]{}, shape(color, LINE, 6));
        return states;
    }

    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
