/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.applist.LibraryQuery;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

public final class HandheldOpenDialog extends DialogFragment {
    private Disposable work;
    static HandheldOpenDialog create(Uri uri, boolean play, boolean finish) {
        Bundle args=new Bundle(); args.putParcelable("uri",uri); args.putBoolean("play",play); args.putBoolean("finish",finish);
        HandheldOpenDialog fragment=new HandheldOpenDialog();fragment.setArguments(args);return fragment;
    }
    @Override public Dialog onCreateDialog(Bundle state) { return new GameMenuDialog(requireContext(),"Open game"); }
    @Override public void onStart() {
        super.onStart();
        GameMenuDialog dialog=(GameMenuDialog)requireDialog();
        Context context=requireContext().getApplicationContext();
        Uri uri=requireArguments().getParcelable("uri");
        dialog.page("Reading game file");
        work=Single.fromCallable(()->HandheldFormat.detect(context,uri,HandheldFormat.name(context,uri)))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(ids->{
                    if(ids.isEmpty()){legacy(uri);return;}
                    if(ids.size()==1){open(uri,ids.iterator().next());return;}
                    dialog.page("Choose platform");
                    for(int id:ids)dialog.action(id==0?"Java ME, Mophun or Symbian":LibraryQuery.PLATFORMS[id],0,()->open(uri,id));
                }, error->legacy(uri));
    }
    private void legacy(Uri uri) {
        FragmentManager fm=getParentFragmentManager();Context c=requireContext();
        Bundle args=requireArguments();dismiss();
        AdditionalGames.installLegacy(c,fm,uri,args.getBoolean("play"),args.getBoolean("finish"));
    }
    private void open(Uri uri,int platform) {
        if(platform==0){legacy(uri);return;}
        GameMenuDialog dialog=(GameMenuDialog)requireDialog();dialog.page("Installing game");
        Context c=requireContext().getApplicationContext();
        work=Single.fromCallable(()->ExternalLibrary.packageProvider(platform).install(c,uri))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(item->{
                    Bundle result=new Bundle();result.putInt("platform",item.platformId());
                    getParentFragmentManager().setFragmentResult("handheld-installed",result);
                    if(requireArguments().getBoolean("play")) item.launch(requireActivity(),false);
                    dismiss();
                },error->{dialog.page("Could not install game");dialog.message(error.getMessage());dialog.setBackAction(this::dismiss);});
    }
    @Override public void onStop(){if(work!=null)work.dispose();super.onStop();}
}
