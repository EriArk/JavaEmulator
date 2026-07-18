/*
 *  Copyright 2020-2022 Yury Kharchenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ru.woesss.j2me.installer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.applist.IconArtUtils;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.databinding.DialogInstallerBinding;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.woesss.j2me.jar.Descriptor;

public class InstallerDialog extends DialogFragment {
	private static final String ARG_URI = "InstallerDialog.uri";
	private static final String ARG_ID = "InstallerDialog.id";
	private static final String ARG_AUTO_START = "InstallerDialog.autoStart";
	private static final String ARG_FINISH_HOST = "InstallerDialog.finishHost";
	private final CompositeDisposable compositeDisposable = new CompositeDisposable();

	private AppRepository appRepository;
	private Button btnOk;
	private Button btnClose;
	private Button btnRun;
	private AppInstaller installer;
	private AlertDialog mDialog;

	private DialogInstallerBinding binding;

	private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
			FileUtils.getFilePicker(),
			this::onPickFileResult);

	/**
	 * @param uri original uri from intent.
	 * @return A new instance of fragment InstallerDialog.
	 */
	public static InstallerDialog newInstance(Uri uri) {
		return newInstance(uri, false);
	}

	public static InstallerDialog newInstance(Uri uri, boolean autoStart) {
		return newInstance(uri, autoStart, autoStart);
	}

	public static InstallerDialog newInstance(Uri uri, boolean autoStart, boolean finishHost) {
		InstallerDialog fragment = new InstallerDialog();
		Bundle args = new Bundle();
		args.putParcelable(ARG_URI, uri);
		args.putBoolean(ARG_AUTO_START, autoStart);
		args.putBoolean(ARG_FINISH_HOST, finishHost);
		fragment.setArguments(args);
		fragment.setCancelable(false);
		return fragment;
	}

	public static InstallerDialog newInstance(int id) {
		InstallerDialog fragment = new InstallerDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_ID, id);
		fragment.setArguments(args);
		fragment.setCancelable(false);
		return fragment;
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		AppListModel appListModel = new ViewModelProvider(requireActivity()).get(AppListModel.class);
		appRepository = appListModel.getAppRepository();
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			dismissAllowingStateLoss();
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		binding = DialogInstallerBinding.inflate(LayoutInflater.from(getContext()));
		mDialog = new AlertDialog.Builder(requireActivity(), getTheme())
				.setView(binding.getRoot())
				.setCancelable(false)
				.create();
		return mDialog;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}

	@Override
	public void onDestroy() {
		compositeDisposable.dispose();
		super.onDestroy();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (installer != null) {
			return;
		}
		if (mDialog.getWindow() != null) {
			mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		}
		btnOk = binding.buttonInstall;
		btnClose = binding.buttonClose;
		btnRun = binding.buttonStart;
		binding.installerTitle.setText("MIDlet installer");
		binding.installerMessage.setText("");
		binding.installerIcon.setImageResource(R.mipmap.ic_launcher);
		hideButtons();
		Bundle args = requireArguments();
		Uri uri = args.getParcelable(ARG_URI);
		if (uri != null) {
			installApp(null, uri);
			return;
		}
		int id = args.getInt(ARG_ID);
		reinstallApp(id);
	}

	private void installApp(String path, Uri uri) {
		installer = new AppInstaller(path, uri, requireActivity().getApplication(), appRepository);
		btnClose.setOnClickListener(v -> {
			installer.deleteTemp();
			installer.clearCache();
			dismiss();
		});
		Disposable disposable = Single.create(installer::loadInfo)
				.subscribeOn(Schedulers.computation())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onProgress, this::onError);
		compositeDisposable.add(disposable);
	}

	private void reinstallApp(int id) {
		installer = new AppInstaller(id, requireActivity().getApplication(), appRepository);
		btnClose.setOnClickListener(v -> {
			installer.deleteTemp();
			installer.clearCache();
			dismiss();
		});
		Disposable disposable = Single.create(installer::loadInfo)
				.subscribeOn(Schedulers.computation())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onProgress, this::onError);
		compositeDisposable.add(disposable);
	}

	@SuppressLint("CheckResult")
	private void onPickFileResult(Uri uri) {
		if (uri == null) {
			return;
		}
		Disposable disposable = installer.updateInfo(uri)
				.subscribeOn(Schedulers.computation())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onProgress, this::onError);
		compositeDisposable.add(disposable);
	}

	private void hideProgress() {
		binding.installationProgress.setVisibility(View.GONE);
		binding.installationStatus.setVisibility(View.GONE);
	}

	private void showProgress() {
		binding.installationProgress.setVisibility(View.VISIBLE);
		binding.installationStatus.setVisibility(View.VISIBLE);
	}

	private void hideButtons() {
		btnOk.setVisibility(View.GONE);
		btnClose.setVisibility(View.GONE);
		btnRun.setVisibility(View.GONE);
	}

	private void showButtons() {
		btnOk.setVisibility(View.VISIBLE);
		btnClose.setVisibility(View.VISIBLE);
	}

	private void convert() {
		Descriptor nd = installer.getNewDescriptor();
		SpannableStringBuilder info = nd.getInfo(requireActivity());
		binding.installerMessage.setText(info);
		binding.installationStatus.setText(R.string.converting_wait);
		showProgress();
		hideButtons();
		Disposable disposable = Single.create(installer::install)
				.subscribeOn(Schedulers.computation())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onProgress, this::onError);
		compositeDisposable.add(disposable);
	}

	private void alertConfirm(SpannableStringBuilder message,
							  View.OnClickListener positive) {
		hideProgress();
		mDialog.setCancelable(false);
		mDialog.setCanceledOnTouchOutside(false);
		binding.installerMessage.setText(message);
		btnOk.setOnClickListener(positive);
		showButtons();
	}

	private void alertSelectJar(View.OnClickListener positive) {
		hideProgress();
		mDialog.setCancelable(false);
		mDialog.setCanceledOnTouchOutside(false);
		binding.installerMessage.setText(getString(R.string.install_jar_needed));
		btnOk.setOnClickListener(positive);
		showButtons();
	}

	private void onProgress(@NonNull Integer status) {
		if (!isAdded()) {
			return;
		}
		if (status != AppInstaller.STATUS_ARCHIVE_CHOICE) {
			binding.installerArchiveChoices.setVisibility(View.GONE);
		}
		if (status == AppInstaller.STATUS_SUCCESS) {
			binding.installationProgress.setVisibility(View.GONE);
			binding.installationStatus.setText(getString(R.string.install_done));
			AppItem app = installer.getExistsApp();
			setInstallerIcon(app.getImagePathExt());
			binding.installerTitle.setText(app.getTitle());
			if (isAutoStart()) {
				launchAndDismiss(app);
				return;
			}
			btnOk.setText(R.string.START_CMD);
			btnOk.setOnClickListener(v -> launchAndDismiss(app));
			btnClose.setText(R.string.close);
			showButtons();
			return;
		}
		Descriptor nd = installer.getNewDescriptor();
		SpannableStringBuilder message;
		switch (status) {
			case AppInstaller.STATUS_NEW:
				if (installer.getJar() != null) {
					convert();
					return;
				}
				message = nd.getInfo(requireActivity());
				break;
			case AppInstaller.STATUS_OLDEST:
				if (isAutoStart()) {
					convert();
					return;
				}
				message = new SpannableStringBuilder(getString(
						R.string.reinstall_older,
						nd.getVersion(),
						installer.getCurrentVersion()));
				break;
			case AppInstaller.STATUS_EQUAL:
				if (isAutoStart()) {
					launchAndDismiss(installer.getExistsApp());
					return;
				}
				message = new SpannableStringBuilder(getString(R.string.reinstall));
				AppItem app = installer.getExistsApp();
				btnRun.setVisibility(View.VISIBLE);
				btnRun.setOnClickListener(v -> {
					installer.clearCache();
					installer.deleteTemp();
					Config.startApp(v.getContext(), app.getTitle(), app.getPathExt(), false);
					dismiss();
				});
				break;
			case AppInstaller.STATUS_NEWEST:
				if (isAutoStart()) {
					convert();
					return;
				}
				message = new SpannableStringBuilder(getString(
						R.string.reinstall_newest,
						nd.getVersion(),
						installer.getCurrentVersion()));
				break;
			case AppInstaller.STATUS_UNMATCHED:
				SpannableStringBuilder info = installer.getManifest().getInfo(requireActivity());
				info.append(getString(R.string.install_jar_non_matched_jad));
				alertConfirm(info, v -> installApp(installer.getJar(), null));
				return;
			case AppInstaller.STATUS_NEED_JAD:
				alertSelectJar(v -> openFileLauncher.launch(null));
				return;
			case AppInstaller.STATUS_ARCHIVE_CHOICE:
				showArchiveChoices();
				return;
			default:
				throw new IllegalStateException("Unexpected value: " + status);
		}
		if (installer.getJar() == null) {
			message.append('\n').append(getString(R.string.warn_install_from_net));
		}
		setInstallerIcon(installer.getIconPath());
		binding.installerTitle.setText(nd.getName());
		mDialog.setCancelable(false);
		mDialog.setCanceledOnTouchOutside(false);
		binding.installerMessage.setText(message);
		btnOk.setOnClickListener(v -> convert());
		hideProgress();
		showButtons();
	}

	private void showArchiveChoices() {
		hideProgress();
		hideButtons();
		btnClose.setVisibility(View.VISIBLE);
		binding.installerMessage.setText(R.string.archive_choose_game);
		LinearLayout choices = binding.installerArchiveChoices;
		choices.removeAllViews();
		choices.setVisibility(View.VISIBLE);
		for (String entry : installer.getArchiveEntries()) {
			Button button = new Button(requireContext());
			button.setText(new java.io.File(entry).getName());
			button.setTextColor(getResources().getColor(R.color.text_primary));
			button.setTextSize(12);
			button.setAllCaps(false);
			button.setBackgroundResource(R.drawable.bg_quick_setting_button);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(180), dp(52));
			params.setMargins(dp(3), dp(3), dp(3), dp(3));
			button.setLayoutParams(params);
			button.setOnClickListener(v -> {
				choices.setVisibility(View.GONE);
				showProgress();
				Disposable disposable = installer.selectArchiveEntry(entry)
						.subscribeOn(Schedulers.computation())
						.observeOn(AndroidSchedulers.mainThread())
						.subscribe(this::onProgress, this::onError);
				compositeDisposable.add(disposable);
			});
			choices.addView(button);
		}
	}

	private boolean isAutoStart() {
		return requireArguments().getBoolean(ARG_AUTO_START, false);
	}

	private void launchAndDismiss(AppItem app) {
		if (app == null || !isAdded()) {
			return;
		}
		Config.startApp(requireActivity(), app.getTitle(), app.getPathExt(), false);
		dismissAllowingStateLoss();
		if (requireArguments().getBoolean(ARG_FINISH_HOST, false)) {
			requireActivity().finish();
		}
	}

	private void setInstallerIcon(String path) {
		Bitmap icon = IconArtUtils.loadLargeIcon(path, dp(56));
		if (icon != null) {
			binding.installerIcon.setImageBitmap(icon);
			return;
		}
		Drawable drawable = Drawable.createFromPath(path);
		if (drawable != null) {
			binding.installerIcon.setImageDrawable(drawable);
		}
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private void onError(Throwable e) {
		e.printStackTrace();
		installer.clearCache();
		installer.deleteTemp();
		if (!isAdded()) return;
		hideProgress();
		Toast.makeText(requireActivity(), getString(R.string.error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
		dismissAllowingStateLoss();
	}
}
