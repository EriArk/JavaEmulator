package ru.playsoftware.j2meloader.catalog;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;

public class LibraryImportActivity extends CompactSettingsActivity {
    private int LIGHT, MUTED, AMBER;
    private LibraryImportModel model;
    private TextView status;
    private TextView homeStatus;
    private Button allowAccess;
    private Button start;
    private CheckBox select;
    private ProgressBar progress;
    private ImageButton options;
    private ScrollView home;
    private LinearLayout footer;
    private RecyclerView list;
    private boolean includeShared = true, homeRequested, restoring, updatingSelection, pickerPending;
    private int singlePart;
    private int displayedStage = -1;
    private final Entries adapter = new Entries();
    private LibraryImportModel.State current;
    private final ActivityResultLauncher<Uri> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                pickerReturned(uri);
                if (uri == null) return;
                retain(uri); homeRequested = false; restoring = false; model.scan(uri);
            });
    private final ActivityResultLauncher<String[]> restorePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                pickerReturned(uri);
                if (uri != null) { retain(uri); homeRequested = false; restoring = true; model.scanBackup(uri); }
            });
    private final ActivityResultLauncher<String> backupPicker = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                pickerReturned(uri);
                if (uri != null) { homeRequested = false; restoring = false; model.exportBackup(uri); }
            });
    private final ActivityResultLauncher<Uri> singlePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                pickerReturned(uri);
                if (uri == null) return;
                retain(uri); homeRequested = false; restoring = false; model.scanSingle(singlePart, uri);
            });
    private final ActivityResultLauncher<Uri> accessPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                pickerReturned(uri);
                if (uri == null) return;
                retain(uri); model.allowFolder(uri);
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LIGHT = getColor(R.color.text_primary);
        MUTED = getColor(R.color.text_secondary);
        AMBER = getColor(R.color.accent);
        screenTitle("Library transfer");
        if (state != null) {
            singlePart = state.getInt("singlePart"); includeShared = state.getBoolean("includeShared", true);
            homeRequested = state.getBoolean("homeRequested"); restoring = state.getBoolean("restoring");
            pickerPending = state.getBoolean("pickerPending");
        }
        model = new ViewModelProvider(this).get(LibraryImportModel.class);
        if (state != null) model.restoreState(state.getBundle("transfer"), pickerPending);
        options = headerButton("Transfer options", R.drawable.ic_baseline_tune_24, this::showOptions);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        status = text(14, MUTED); status.setMaxLines(2); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(4), 0, dp(4), dp(8)); root.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(AMBER));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        home = new ScrollView(this);
        LinearLayout choices = new LinearLayout(this); choices.setOrientation(LinearLayout.VERTICAL);
        homeStatus = text(14, LIGHT);
        homeStatus.setPadding(dp(4), 0, dp(4), dp(12)); choices.addView(homeStatus);
        allowAccess = button("Allow folder access", R.drawable.ic_setting_folder, () -> {
            if (current != null && current.access != null) launchPicker(accessPicker, android.provider.DocumentsContract.buildDocumentUri(
                    current.access.folder.getAuthority(), android.provider.DocumentsContract.getDocumentId(current.access.folder)));
        });
        choices.addView(allowAccess, actionParams());
        choices.addView(button(ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT ? "Back up Java / Mophun" : "Back up library", android.R.drawable.ic_menu_save, () -> launchPicker(backupPicker,
                "AbyssME-" + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT)
                        .format(new java.util.Date()) + ".zip")), actionParams());
        choices.addView(button(ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT ? "Restore Java / Mophun backup" : "Restore backup", android.R.drawable.ic_menu_revert,
                () -> launchPicker(restorePicker, new String[]{"application/zip", "application/octet-stream"})), actionParams());
        choices.addView(button("Import from J2ME Loader", R.drawable.ic_setting_folder, this::showImportSources), actionParams());
        choices.addView(button("Move from AbyssME Phone", android.R.drawable.ic_menu_revert, this::showPhoneMigration), actionParams());
        if (ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT) {
            choices.addView(button("Symbian / N-Gage phone backups", android.R.drawable.ic_menu_save, () ->
                    startActivity(new Intent().setClassName(this,"io.github.eriark.abyssme.symbian.SymbianActivity")
                            .putExtra("symbian.backups",true))), actionParams());
        }
        home.addView(choices); root.addView(home, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new RecyclerView(this); list.setContentDescription("Transfer games");
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        select = new CheckBox(this); select.setText("Select all"); select.setTextColor(LIGHT); select.setTextSize(14);
        select.setMinHeight(dp(48)); select.setButtonTintList(ColorStateList.valueOf(AMBER));
        select.setOnCheckedChangeListener((view, checked) -> {
            if (updatingSelection) return;
            for (LibraryImporter.Entry entry : adapter.entries) entry.selected = entry.problem == null && checked;
            adapter.notifyDataSetChanged(); updateSelection();
        });
        footer.addView(select, new LinearLayout.LayoutParams(0, -2, 1));
        start = button("Import", android.R.drawable.ic_media_play, this::startOrCancel);
        footer.addView(start, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(footer);
        screenContent.addView(root);
        model.state.observe(this, this::render);
    }

    private void retain(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
    }

    private void showPhoneMigration() {
        GameMenuDialog dialog = menu("Move from AbyssME Phone");
        dialog.message("In AbyssME Phone, open Settings > Library transfer and back up Java / Mophun. "
                + "Then restore that ZIP here. Games, saves and supported game settings travel together. "
                + "Keep the old app installed until you have checked your games and saves.");
        dialog.action("Open AbyssME Phone", android.R.drawable.ic_menu_view, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).setClassName("io.github.eriark.abyssme.phone",
                        "ru.playsoftware.j2meloader.MainActivity"));
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(this, "AbyssME Phone is not installed", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        dialog.action("Restore Java / Mophun backup", android.R.drawable.ic_menu_revert, () -> {
            dialog.dismiss(); launchPicker(restorePicker, new String[]{"application/zip", "application/octet-stream"});
        });
        if (ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT) {
            dialog.message("Symbian / N-Gage uses separate phone backups. Export each phone in the old app, "
                    + "then restore it here, including its installed games and saves. App-wide preferences are not transferred.");
            dialog.action("Symbian / N-Gage phone backups", android.R.drawable.ic_menu_save, () -> {
                dialog.dismiss(); startActivity(new Intent().setClassName(this,
                        "io.github.eriark.abyssme.symbian.SymbianActivity").putExtra("symbian.backups", true));
            });
        }
    }
    private <T> void launchPicker(ActivityResultLauncher<T> launcher, T input) {
        pickerPending = true;
        launcher.launch(input);
    }
    private void pickerReturned(Uri uri) {
        pickerPending = false;
        model.pickerReturned(uri == null);
    }
    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6); return params;
    }
    private GameMenuDialog menu(String title) {
        GameMenuDialog dialog = new GameMenuDialog(this, "Library transfer");
        dialog.show(); dialog.page(title); return dialog;
    }
    private void showImportSources() {
        GameMenuDialog dialog = menu("J2ME Loader");
        dialog.message("Close J2ME Loader before importing. Original files stay unchanged.");
        dialog.action("Whole library", R.drawable.ic_setting_folder, () -> { dialog.dismiss(); launchPicker(picker, null); });
        dialog.action("Single game", R.drawable.ic_list, () -> { dialog.dismiss(); selectSinglePart(0); });
    }
    private void showOptions() {
        GameMenuDialog dialog = menu("Transfer options");
        dialog.message(current == null ? "" : current.status);
        dialog.message(current != null && current.preview != null && current.preview.sharedFiles
                ? "Shared file conflicts keep the files already on this device."
                : "Original files and existing saves stay unchanged.");
        if (current != null && !current.busy && current.preview != null && !homeRequested) {
            if (model.isSingleGame()) {
                dialog.action("Add saves", android.R.drawable.ic_menu_save, () -> { dialog.dismiss(); selectSinglePart(1); });
                dialog.action("Add settings", R.drawable.ic_baseline_tune_24, () -> { dialog.dismiss(); selectSinglePart(2); });
            }
            if (current.preview.sharedFiles) dialog.toggle("Shared files (keep existing)", includeShared, value -> includeShared = value);
        }
    }
    private void render(LibraryImportModel.State state) {
        current = state;
        boolean preview = state.preview != null && !homeRequested;
        boolean results = state.results != null && !homeRequested;
        boolean review = preview || results || state.busy;
        home.setVisibility(review ? View.GONE : View.VISIBLE);
        list.setVisibility(review ? View.VISIBLE : View.GONE);
        footer.setVisibility(review ? View.VISIBLE : View.GONE);
        boolean showStatus = !"No folder selected".equals(state.status) && !(homeRequested && state.preview != null);
        status.setText(review ? state.status : "");
        status.setVisibility(showStatus && review ? View.VISIBLE : View.GONE);
        homeStatus.setText(!review ? state.status : "");
        homeStatus.setVisibility(showStatus && !review ? View.VISIBLE : View.GONE);
        allowAccess.setVisibility(!review && state.access != null ? View.VISIBLE : View.GONE);
        options.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(state.total == 0);
        if (state.total > 0) { progress.setMax(state.total); progress.setProgress(state.completed); }
        select.setVisibility(preview && !state.busy ? View.VISIBLE : View.GONE);
        adapter.entries = preview ? state.preview.entries : new ArrayList<>();
        adapter.results = results ? state.results : new ArrayList<>();
        adapter.notifyDataSetChanged(); updateSelection();
        int stage = state.busy ? 2 : preview ? 1 : results ? 3 : state.access != null ? 4 : 0;
        if (displayedStage != stage) { displayedStage = stage; focusContentWhenReady(); }
    }
    private void updateSelection() {
        if (current == null) return;
        if (current.busy) {
            start.setText("Cancel"); setIcon(start, android.R.drawable.ic_menu_close_clear_cancel);
            start.setEnabled(true); return;
        }
        if (current.results != null) {
            start.setText("Done"); setIcon(start, android.R.drawable.checkbox_on_background);
            start.setEnabled(true); return;
        }
        setIcon(start, android.R.drawable.ic_media_play);
        int count = 0, available = 0;
        for (LibraryImporter.Entry entry : adapter.entries) if (entry.problem == null) {
            available++; if (entry.selected) count++;
        }
        updatingSelection = true; select.setChecked(available > 0 && count == available); updatingSelection = false;
        select.setEnabled(available > 0);
        start.setText((restoring ? "Restore" : "Import") + (count > 0 ? " (" + count + ")" : ""));
        start.setEnabled(count > 0);
    }
    private void startOrCancel() {
        if (current == null) return;
        if (current.busy) { model.cancel(); status.setText("Stopping safely..."); return; }
        if (current.results != null) { homeRequested = true; render(current); return; }
        if (model.isSingleGame() && !adapter.entries.isEmpty() && !adapter.entries.get(0).hasSaves) {
            GameMenuDialog dialog = menu("No saves selected");
            dialog.message("The game will start without your previous progress.");
            dialog.action("Add saves", android.R.drawable.ic_menu_save, () -> { dialog.dismiss(); selectSinglePart(1); });
            dialog.action("Import without saves", android.R.drawable.ic_media_play, () -> { dialog.dismiss(); model.start(includeShared); });
        } else model.start(includeShared);
    }
    private void selectSinglePart(int part) {
        singlePart = part;
        GameMenuDialog dialog = menu(new String[]{"Game folder", "Saves folder", "Settings folder"}[part]);
        dialog.message(new String[]{"J2ME Loader / converted / your game", "J2ME Loader / data / the same game",
                "J2ME Loader / configs / the same game"}[part]);
        dialog.action("Choose folder", R.drawable.ic_setting_folder, () -> { dialog.dismiss(); launchPicker(singlePicker, null); });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBundle("transfer", model.saveState());
        state.putBoolean("pickerPending", pickerPending);
        state.putInt("singlePart", singlePart); state.putBoolean("includeShared", includeShared);
        state.putBoolean("homeRequested", homeRequested); state.putBoolean("restoring", restoring);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (current != null && current.busy) { model.cancel(); status.setText("Stopping safely..."); }
        else if (current != null && !homeRequested && (current.preview != null || current.results != null)) {
            homeRequested = true; render(current);
        } else super.onBackPressed();
    }
    private Button button(String label, int icon, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{getColor(ru.playsoftware.j2meloader.R.color.text_secondary), LIGHT}));
        button.setBackgroundResource(R.drawable.bg_quick_setting_button); button.setBackgroundTintList(null);
        setIcon(button, icon);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private void setIcon(Button button, int icon) {
        android.graphics.drawable.Drawable drawable = getDrawable(icon).mutate();
        drawable.setTint(AMBER); drawable.setBounds(0, 0, dp(22), dp(22));
        button.setCompoundDrawables(drawable, null, null, null); button.setCompoundDrawablePadding(dp(12));
    }
    private TextView text(int size, int color) { TextView view = new TextView(this); view.setTextSize(size); view.setTextColor(color); return view; }
    private final class Entries extends RecyclerView.Adapter<Row> {
        List<LibraryImporter.Entry> entries = new ArrayList<>(); List<String> results = new ArrayList<>();
        @Override public int getItemCount() { return results.isEmpty() ? entries.size() : results.size(); }
        @Override public Row onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(LibraryImportActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(64)); row.setPadding(0, dp(8), 0, dp(8)); row.setFocusable(true);
            row.setBackgroundResource(R.drawable.bg_library_tab);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            CheckBox check = new CheckBox(LibraryImportActivity.this); check.setButtonTintList(ColorStateList.valueOf(AMBER));
            check.setFocusable(false);
            row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView label = text(15, LIGHT); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            return new Row(row, check, label);
        }
        @Override public void onBindViewHolder(Row row, int position) {
            row.check.setOnCheckedChangeListener(null);
            if (!results.isEmpty()) {
                row.check.setVisibility(View.GONE); row.label.setText(results.get(position));
                row.itemView.setOnClickListener(null); row.itemView.setFocusable(true); return;
            }
            LibraryImporter.Entry entry = entries.get(position);
            row.check.setVisibility(View.VISIBLE); row.check.setChecked(entry.selected); row.check.setEnabled(entry.problem == null);
            row.check.setContentDescription(entry.title);
            row.label.setText(entry.title + "\n" + (entry.problem != null ? entry.problem
                    : entry.vendor + " / " + entry.version + (entry.hasSaves ? "\nSaves included" : "\nNo saves found")
                    + (entry.duplicateTitle ? "\nAlso installed: imports as a separate copy" : "")));
            row.check.setOnCheckedChangeListener((button, checked) -> { entry.selected = checked; updateSelection(); });
            row.itemView.setOnClickListener(v -> { if (row.check.isEnabled()) row.check.toggle(); });
        }
    }
    private static final class Row extends RecyclerView.ViewHolder {
        final CheckBox check; final TextView label;
        Row(View view, CheckBox check, TextView label) { super(view); this.check = check; this.label = label; }
    }
}
