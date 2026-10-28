package io.github.eriark.abyssme.symbian;

import android.app.Application;
import android.app.Dialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.*;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import ru.playsoftware.j2meloader.databinding.DialogInstallerBinding;
import ru.playsoftware.j2meloader.R;

/** Main-process content routing; native libraries are not loaded by inspection. */
public final class SymbianImportDialog extends DialogFragment implements AdditionalGames.ImportRouter {
    private DialogInstallerBinding binding;
    private Inspection model;
    private boolean delivered;

    @Override public DialogFragment installer(Context context, Uri uri, boolean autoStart, boolean finishHost) {
        String name = SymbianFiles.name(context, uri);
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".jad") || lower.endsWith(".mpn") || lower.endsWith(".kjx"))
            return AdditionalGames.existingInstaller(context, uri, autoStart, finishHost);
        SymbianImportDialog dialog = new SymbianImportDialog(); Bundle args = new Bundle();
        args.putParcelable("uri", uri); args.putBoolean("autoStart", autoStart); args.putBoolean("finishHost", finishHost);
        dialog.setArguments(args); return dialog;
    }
    @Override public Dialog onCreateDialog(Bundle saved) {
        binding = DialogInstallerBinding.inflate(getLayoutInflater());
        Uri uri = requireArguments().getParcelable("uri");
        binding.installerTitle.setText(R.string.native_import_add);
        binding.installerMessage.setText(SymbianFiles.name(requireContext(), uri));
        binding.installationStatus.setText(R.string.native_inspecting_selected);
        binding.installationProgress.setVisibility(View.VISIBLE);
        binding.buttonInstall.setVisibility(View.GONE); binding.buttonStart.setVisibility(View.GONE);
        binding.buttonClose.setContentDescription(getString(R.string.system_cancel)); binding.buttonClose.setTooltipText(getString(R.string.system_cancel));
        binding.buttonClose.setOnClickListener(v -> { cancelImport(); dismiss(); });
        model = new ViewModelProvider(this).get(Inspection.class);
        model.state.observe(this, this::render);
        model.start(uri);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(binding.getRoot()).create();
        dialog.setOnKeyListener((d, key, event) -> {
            if (key != KeyEvent.KEYCODE_BUTTON_B && key != KeyEvent.KEYCODE_BUTTON_A && key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) binding.buttonClose.performClick();
            return true;
        });
        return dialog;
    }
    @Override public void onStart() {
        super.onStart();
        requireDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        requireDialog().getWindow().setLayout(Math.min(Math.round(440 * metrics.density),
                metrics.widthPixels - Math.round(24 * metrics.density)),
                Math.min(Math.round(240 * metrics.density), metrics.heightPixels - Math.round(48 * metrics.density)));
        binding.buttonClose.requestFocus();
    }
    @Override public void onResume() { super.onResume(); render(model.state.getValue()); }
    private void render(Result result) {
        if (result == null || binding == null || delivered) return;
        binding.installationStatus.setText(result.message(requireContext()));
        if (result.failure!=null) {
            binding.installationProgress.setVisibility(View.GONE);
            binding.buttonClose.setContentDescription(getString(R.string.system_close)); binding.buttonClose.setTooltipText(getString(R.string.system_close)); return;
        }
        if (result.kind == null || !isResumed() || getParentFragmentManager().isStateSaved()) return;
        Context context = requireContext(); FragmentManager fragments = getParentFragmentManager();
        Uri uri = requireArguments().getParcelable("uri");
        boolean auto = requireArguments().getBoolean("autoStart"), finish = requireArguments().getBoolean("finishHost");
        if (result.kind != ImportKind.Kind.EXISTING) {
            try {
                File staged = model.transfer();
                try {
                    context.startActivity(new Intent(context, SymbianActivity.class)
                            .putExtra("symbian.importFile", staged.getName())
                            .putExtra("symbian.importName", SymbianFiles.name(context, uri)));
                } catch (RuntimeException error) { Files.deleteIfExists(staged.toPath()); throw error; }
            } catch (IOException | RuntimeException error) {
                binding.installationStatus.setText(SystemFailureText.message(context,error)); binding.installationProgress.setVisibility(View.GONE); return;
            }
        }
        delivered = true;
        dismiss();
        if (result.kind == ImportKind.Kind.EXISTING) {
            AdditionalGames.existingInstaller(context, uri, auto, finish).show(fragments, "installer");
        }
    }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }

    private void cancelImport() {
        // Do not forward a completed inspection after the user has cancelled it.
        delivered = true;
        if (requireArguments().getBoolean("finishHost")) requireActivity().finish();
    }
    @Override public void onCancel(DialogInterface dialog) {
        cancelImport();
        super.onCancel(dialog);
    }

    static final class Result {
        final ImportKind.Kind kind;
        final String raw;
        final Throwable failure;
        final SystemProgress step;
        final Object[] arguments;
        Result(ImportKind.Kind kind,String raw,Throwable failure,SystemProgress step,Object... arguments) {
            this.kind=kind;this.raw=raw;this.failure=failure;this.step=step;this.arguments=arguments.clone();
        }
        String message(Context context) {
            if(failure!=null)return SystemFailureText.message(context,failure);
            if(step!=null)return SystemTransferText.progress(context,step,arguments);
            return kind!=null?context.getString(R.string.native_import_ready):raw;
        }
    }
    public static final class Inspection extends AndroidViewModel {
        final MutableLiveData<Result> state = new MutableLiveData<>();
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private Future<?> work;
        private boolean cancelled;
        private File owned;
        public Inspection(Application application) { super(application); }
        synchronized void start(Uri uri) {
            if (work != null) return;
            work = worker.submit(() -> {
                File staged = null;
                try {
                    staged = SymbianFiles.stage(getApplication(), uri, PreparedSystemArchive.MAX_INPUT,
                            new PreparedSystemArchive.Progress() {
                                public void update(String text) throws IOException {
                                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import cancelled");
                                    state.postValue(new Result(null,text,null,null));
                                }
                                public void update(SystemProgress step,Object... arguments) throws IOException {
                                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import cancelled");
                                    state.postValue(new Result(null,null,null,step,arguments));
                                }
                            });
                    ImportKind.Kind kind = ImportKind.inspect(staged, SymbianFiles.name(getApplication(), uri));
                    synchronized (this) {
                        if (cancelled) return;
                        owned = staged; staged = null;
                        state.postValue(new Result(kind,null,null,null));
                    }
                } catch (Exception error) { state.postValue(new Result(null,null,error,null)); }
                finally { if (staged != null) remove(staged); }
            });
        }
        synchronized File transfer() throws IOException {
            if (cancelled || owned == null) throw new IOException(getApplication().getString(R.string.native_import_unavailable));
            File file = owned; owned = null; return file;
        }
        @Override protected synchronized void onCleared() {
            cancelled = true; if (work != null) work.cancel(true); worker.shutdownNow();
            if (owned != null) { remove(owned); owned = null; }
        }
        private static void remove(File file) {
            try { Files.deleteIfExists(file.toPath()); }
            catch (IOException error) { android.util.Log.w("AbyssSymbian", "Cannot clean staged input", error); }
        }
    }
}
