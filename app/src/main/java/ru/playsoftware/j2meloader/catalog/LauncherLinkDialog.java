package ru.playsoftware.j2meloader.catalog;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

public final class LauncherLinkDialog extends DialogFragment {
    private Disposable work;
    public static LauncherLinkDialog create(Uri uri, boolean finishHost) {
        LauncherLinkDialog result = new LauncherLinkDialog();
        Bundle args = new Bundle(); args.putParcelable("uri", uri); args.putBoolean("finishHost", finishHost);
        result.setArguments(args); return result;
    }
    @Override public Dialog onCreateDialog(Bundle state) {
        return new GameMenuDialog(requireContext(), "AbyssME");
    }
    @Override public void onStart() {
        super.onStart();
        GameMenuDialog dialog = (GameMenuDialog) requireDialog();
        dialog.page("Opening game"); dialog.message("Reading launch file...");
        Context context = requireContext().getApplicationContext();
        Uri uri = requireArguments().getParcelable("uri");
        work = Single.fromCallable(() -> LauncherLink.resolve(context, uri))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(item -> {
                    item.launch(requireActivity(), false);
                    if (requireArguments().getBoolean("finishHost")) requireActivity().finish();
                    dismiss();
                }, error -> {
                    dialog.page("Cannot open game"); dialog.message(error.getMessage());
                    dialog.action("Close", android.R.drawable.ic_menu_close_clear_cancel, this::dismiss);
                });
    }
    @Override public void onStop() {
        if (work != null) work.dispose();
        super.onStop();
    }
}
