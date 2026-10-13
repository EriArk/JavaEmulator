package ru.playsoftware.j2meloader.mophun;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.databinding.DialogInstallerBinding;

public final class MophunInstallerDialog extends DialogFragment {
    private DialogInstallerBinding binding;
    private Disposable work;
    private boolean busy;
    private AppItem installed;

    public static MophunInstallerDialog create(Uri uri, boolean autoStart, boolean finishHost) {
        MophunInstallerDialog dialog = new MophunInstallerDialog();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        args.putBoolean("autoStart", autoStart);
        args.putBoolean("finishHost", finishHost);
        dialog.setArguments(args);
        return dialog;
    }

    @Override public Dialog onCreateDialog(Bundle state) {
        binding = DialogInstallerBinding.inflate(getLayoutInflater());
        Uri uri = requireArguments().getParcelable("uri");
        binding.installerTitle.setText("Mophun installer");
        binding.installationStatus.setText("Experimental");
        binding.installerMessage.setMinHeight(0);
        binding.installerMessage.setText(MophunStore.name(requireContext(), uri));
        binding.installationProgress.setVisibility(View.GONE);
        binding.buttonStart.setVisibility(View.GONE);
        binding.buttonClose.setOnClickListener(v -> dismiss());
        binding.buttonInstall.setOnClickListener(v -> install(uri));
        binding.buttonStart.setOnClickListener(v -> start());
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(binding.getRoot()).create();
        dialog.setOnKeyListener((d, key, event) -> {
            if (key == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.getAction() == KeyEvent.ACTION_UP && !busy) {
                    View focus = dialog.getCurrentFocus();
                    if (focus != null && focus.isClickable()) focus.performClick();
                    else if (installed == null) binding.buttonInstall.performClick();
                    else binding.buttonStart.performClick();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && !busy) dismiss();
                return true;
            }
            return false;
        });
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        updateWindow();
        requireDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        binding.buttonInstall.requestFocusFromTouch();
        if (requireArguments().getBoolean("autoStart") && work == null) install(requireArguments().getParcelable("uri"));
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        updateWindow();
    }

    private void updateWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;
        android.view.Window window = requireDialog().getWindow();
        window.setLayout(Math.min(Math.round(480 * density), metrics.widthPixels - Math.round(24 * density)),
                Math.min(Math.round(360 * density), metrics.heightPixels - Math.round(48 * density)));
        if (ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE) {
            androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat
                    .getInsetsController(window, window.getDecorView());
            bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }

    private void install(Uri uri) {
        busy = true;
        setCancelable(false);
        binding.buttonClose.setEnabled(false);
        binding.buttonInstall.setEnabled(false);
        binding.installationProgress.setVisibility(View.VISIBLE);
        binding.installationStatus.setText("Preparing game");
        android.content.Context context = requireContext().getApplicationContext();
        File root = new File(Config.getEmulatorDir());
        work = Single.fromCallable(() -> MophunStore.install(context, root, uri))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(item -> {
                    installed = item;
                    done();
                    binding.installationStatus.setText("Ready");
                    binding.buttonInstall.setVisibility(View.GONE);
                    binding.buttonStart.setVisibility(View.VISIBLE);
                    binding.buttonStart.requestFocus();
                    if (requireArguments().getBoolean("autoStart")) start();
                }, error -> {
                    done();
                    binding.installationStatus.setText("Could not import game");
                    binding.installerMessage.setText(error.getMessage());
                    binding.buttonInstall.setEnabled(true);
                });
    }

    private void done() {
        busy = false;
        setCancelable(true);
        binding.installationProgress.setVisibility(View.GONE);
        binding.buttonClose.setEnabled(true);
    }

    private void start() {
        new MophunEngine().launch(requireContext(), installed.getTitle(), new File(installed.getPathExt()), false);
        if (requireArguments().getBoolean("finishHost")) requireActivity().finish();
        dismiss();
    }

    @Override public void onDestroyView() {
        if (work != null) work.dispose();
        binding = null;
        super.onDestroyView();
    }
}
