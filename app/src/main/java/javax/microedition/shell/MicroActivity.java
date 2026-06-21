/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2018 Nikita Shakarun
 * Copyright 2019-2022 Yury Kharchenko
 * Copyright 2022-2024 Arman Jussupgaliyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.shell;

import static ru.playsoftware.j2meloader.util.Constants.*;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.InputDevice;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.preference.PreferenceManager;

import org.acra.ACRA;
import org.acra.ErrorReporter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.keyboard.KeyMapper;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.location.LocationProviderImpl;
import javax.microedition.util.ContextHolder;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.LogUtils;

public class MicroActivity extends AppCompatActivity {
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;
	private static final int QUICK_MAP_HOLD_MS = 350;
	private static final int QUICK_SETTINGS_HIDE_MS = 5000;
	private static final float QUICK_MAP_AXIS_DEADZONE = 0.45f;

	private static final int[] QUICK_MAP_TARGET_KEYS = {
			Canvas.KEY_UP, Canvas.KEY_DOWN, Canvas.KEY_LEFT, Canvas.KEY_RIGHT, Canvas.KEY_FIRE,
			Canvas.KEY_NUM1, Canvas.KEY_NUM2, Canvas.KEY_NUM3, Canvas.KEY_NUM4, Canvas.KEY_NUM5,
			Canvas.KEY_NUM6, Canvas.KEY_NUM7, Canvas.KEY_NUM8, Canvas.KEY_NUM9, Canvas.KEY_NUM0,
			Canvas.KEY_STAR, Canvas.KEY_POUND, Canvas.KEY_SOFT_LEFT, Canvas.KEY_SOFT_RIGHT,
			KeyMapper.KEY_OPTIONS_MENU
	};

	private static final String[] QUICK_MAP_TARGET_LABELS = {
			"UP", "DOWN", "LEFT", "RIGHT", "FIRE",
			"1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
			"*", "#", "SOFT1", "SOFT2", "MENU"
	};

	private Displayable current;
	private boolean visible;
	private boolean actionBarEnabled;
	private boolean statusBarEnabled;
	private MicroLoader microLoader;
	private String appName;
	private InputMethodManager inputMethodManager;
	private int menuKey;
	private String appPath;
	private int quickMapTargetKey;
	private String quickMapTargetLabel;
	private int quickMapCapturedKeyUp = KeyEvent.KEYCODE_UNKNOWN;
	private boolean selectQuickMapTracking;
	private boolean selectQuickMapOpened;
	private final Handler quickSettingsHandler = new Handler(Looper.getMainLooper());
	private final Runnable hideQuickSettingsRunnable = new Runnable() {
		@Override
		public void run() {
			hideQuickSettingsOverlay();
		}
	};
	private final Runnable openQuickMapRunnable = new Runnable() {
		@Override
		public void run() {
			selectQuickMapOpened = true;
			showQuickMapOverlay();
		}
	};

	public ActivityMicroBinding binding;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		lockNightMode();
		super.onCreate(savedInstanceState);
		ContextHolder.setCurrentActivity(this);

		binding = ActivityMicroBinding.inflate(getLayoutInflater());
		View view = binding.getRoot();
		setContentView(view);
		setSupportActionBar(binding.toolbar);

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		actionBarEnabled = sp.getBoolean(PREF_TOOLBAR, false);
		statusBarEnabled = sp.getBoolean(PREF_STATUSBAR, false);
		if (sp.getBoolean(PREF_ADD_CUTOUT_AREA, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode =
					WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
		if (sp.getBoolean(PREF_KEEP_SCREEN, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		ContextHolder.setVibration(sp.getBoolean(PREF_VIBRATION, true));
		Canvas.setScreenshotRawMode(sp.getBoolean(PREF_SCREENSHOT_SWITCH, false));
		Intent intent = getIntent();
		if (BuildConfig.FULL_EMULATOR) {
			appName = intent.getStringExtra(KEY_MIDLET_NAME);
			Uri data = intent.getData();
			if (data == null) {
				showErrorDialog("Invalid intent: app path is null");
				return;
			}
			appPath = data.toString();
		} else {
			appName = getTitle().toString();
			appPath = getApplicationInfo().dataDir + "/files/converted/midlet";
			File dir = new File(appPath);
			if (!dir.exists() && !dir.mkdirs()) {
				throw new RuntimeException("Can't access file system");
			}
		}
		String arguments = intent.getStringExtra(KEY_START_ARGUMENTS);
		if (arguments != null) {
			MidletSystem.setProperty("com.nokia.mid.cmdline", arguments);
			String[] arr = arguments.split(";");
			for (String s: arr) {
				if (s.length() == 0) {
					continue;
				}
				if (s.contains("=")) {
					int i = s.indexOf('=');
					String k = s.substring(0, i);
					String v = s.substring(i + 1);
					MidletSystem.setProperty(k, v);
				} else {
					MidletSystem.setProperty(s, "");
				}
			}
		}
		MidletSystem.setProperty("com.nokia.mid.cmdline.instance", "1");
		microLoader = new MicroLoader(this, appPath);
		if (!microLoader.init()) {
			Config.startApp(this, appName, appPath, true, arguments);
			finish();
			return;
		}
		microLoader.applyConfiguration();
		VirtualKeyboard vk = ContextHolder.getVk();
		int orientation = microLoader.getOrientation();
		if (vk != null) {
			vk.setView(binding.overlayView);
			binding.overlayView.addLayer(vk);
			if (vk.isPhone()) {
				orientation = ORIENTATION_PORTRAIT;
			}
		}
		setOrientation(orientation);
		menuKey = microLoader.getMenuKeyCode();
		setupQuickSettingsOverlay();
		setupQuickMapOverlay();
		inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		try {
			loadMIDlet();
		} catch (Exception e) {
			e.printStackTrace();
			showErrorDialog(e.toString());
		}
	}

	public void lockNightMode() {
		int current = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (current == Configuration.UI_MODE_NIGHT_YES) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		visible = true;
		MidletThread.resumeApp();
	}

	@Override
	public void onPause() {
		visible = false;
		hideSoftInput();
		cancelQuickMapTrigger();
		hideQuickSettingsOverlay();
		if (binding != null && binding.quickMapOverlay.getVisibility() == View.VISIBLE) {
			binding.quickMapOverlay.setVisibility(View.GONE);
			quickMapTargetKey = 0;
		}
		MidletThread.pauseApp();
		super.onPause();
	}

	private void hideSoftInput() {
		if (inputMethodManager != null) {
			IBinder windowToken = binding.displayableContainer.getWindowToken();
			inputMethodManager.hideSoftInputFromWindow(windowToken, 0);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
				current instanceof Canvas) {
			hideSystemUI();
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void setOrientation(int orientation) {
		switch (orientation) {
			case ORIENTATION_AUTO:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				break;
			case ORIENTATION_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
				break;
			case ORIENTATION_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				break;
			case ORIENTATION_DEFAULT:
			default:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				break;
		}
	}

	private void loadMIDlet() throws Exception {
		LinkedHashMap<String, String> midlets = microLoader.loadMIDletList();
		int size = midlets.size();
		String[] midletsNameArray = midlets.values().toArray(new String[0]);
		String[] midletsClassArray = midlets.keySet().toArray(new String[0]);
		if (size == 0) {
			throw new Exception("No MIDlets found");
		} else if (size == 1) {
			MidletThread.create(microLoader, midletsClassArray[0]);
		} else {
			showMidletDialog(midletsNameArray, midletsClassArray);
		}
	}

	private void showMidletDialog(String[] names, final String[] classes) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.select_dialog_title)
				.setItems(names, (d, n) -> {
					String clazz = classes[n];
					ErrorReporter errorReporter = ACRA.getErrorReporter();
					String report = errorReporter.getCustomData(Constants.KEY_APPCENTER_ATTACHMENT);
					StringBuilder sb = new StringBuilder();
					if (report != null) {
						sb.append(report).append("\n");
					}
					sb.append("Begin app: ").append(names[n]).append(", ").append(clazz);
					errorReporter.putCustomData(Constants.KEY_APPCENTER_ATTACHMENT, sb.toString());
					MidletThread.create(microLoader, clazz);
					MidletThread.resumeApp();
				})
				.setOnCancelListener(d -> {
					d.dismiss();
					MidletThread.notifyDestroyed();
				});
		builder.show();
	}

	void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.error)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, (d, w) -> MidletThread.notifyDestroyed());
		builder.setOnCancelListener(dialogInterface -> MidletThread.notifyDestroyed());
		builder.show();
	}

	private int getToolBarHeight() {
		int[] attrs = new int[]{androidx.appcompat.R.attr.actionBarSize};
		TypedArray ta = obtainStyledAttributes(attrs);
		int toolBarHeight = ta.getDimensionPixelSize(0, -1);
		ta.recycle();
		return toolBarHeight;
	}

	private void hideSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			if (!statusBarEnabled) {
				flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN;
			}
			getWindow().getDecorView().setSystemUiVisibility(flags);
		} else if (!statusBarEnabled) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	private void showSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	public void setCurrent(Displayable displayable) {
		ViewHandler.postEvent(new SetCurrentEvent(current, displayable));
		current = displayable;
	}

	public Displayable getCurrent() {
		return current;
	}

	public boolean isVisible() {
		return visible;
	}

	public void showExitConfirmation() {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		alertBuilder.setTitle(R.string.CONFIRMATION_REQUIRED)
				.setMessage(R.string.FORCE_CLOSE_CONFIRMATION)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					hideSoftInput();
					MidletThread.destroyApp();
				})
				.setNeutralButton(R.string.action_settings, (d, w) -> {
					hideSoftInput();
					Config.startApp(this, appName, appPath, true);
					MidletThread.destroyApp();
				})
				.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.create().show();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (handleQuickMapKeyEvent(event)) {
			return true;
		}
		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU)
			if (current instanceof Canvas && binding.displayableContainer.dispatchKeyEvent(event)) {
				return true;
			} else if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.getRepeatCount() == 0) {
					event.startTracking();
					return true;
				} else if (event.isLongPress()) {
					return onKeyLongPress(event.getKeyCode(), event);
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				return onKeyUp(event.getKeyCode(), event);
			}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		if (isQuickMapVisible()) {
			if (quickMapTargetKey != 0 && event.getAction() == MotionEvent.ACTION_MOVE
					&& isFromSource(event, InputDevice.SOURCE_JOYSTICK)) {
				int inputCode = getQuickMapStickInput(event);
				if (inputCode != 0) {
					saveQuickKeyMapping(inputCode);
				}
			}
			return true;
		}
		return super.dispatchGenericMotionEvent(event);
	}

	private boolean handleQuickMapKeyEvent(KeyEvent event) {
		if (binding == null || microLoader == null) {
			return false;
		}
		int keyCode = event.getKeyCode();
		if (event.getAction() == KeyEvent.ACTION_UP && keyCode == quickMapCapturedKeyUp) {
			quickMapCapturedKeyUp = KeyEvent.KEYCODE_UNKNOWN;
			return true;
		}
		if (isQuickMapVisible()) {
			if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					hideQuickMapOverlay();
					quickMapCapturedKeyUp = keyCode;
					return true;
				}
				if (quickMapTargetKey != 0 && !isIgnoredQuickMapKey(keyCode)) {
					quickMapCapturedKeyUp = keyCode;
					saveQuickKeyMapping(keyCode);
					return true;
				}
			}
			return false;
		}
		if (keyCode != KeyEvent.KEYCODE_BUTTON_SELECT || binding.displayableContainer.getChildCount() == 0) {
			return false;
		}
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			if (event.getRepeatCount() == 0) {
				selectQuickMapTracking = true;
				selectQuickMapOpened = false;
				binding.midletFrame.postDelayed(openQuickMapRunnable, QUICK_MAP_HOLD_MS);
			}
			return true;
		}
		if (event.getAction() == KeyEvent.ACTION_UP && selectQuickMapTracking) {
			cancelQuickMapTrigger();
			if (!selectQuickMapOpened) {
				sendDeferredSelectPress();
			}
			return true;
		}
		return false;
	}

	private void setupQuickMapOverlay() {
		binding.quickMapClose.setOnClickListener(v -> hideQuickMapOverlay());
		binding.quickMapOverlay.setOnClickListener(v -> {
		});
		binding.quickMapPanel.setOnClickListener(v -> {
		});
		rebuildQuickMapProfiles();
		rebuildQuickMapTargets();
	}

	private void setupQuickSettingsOverlay() {
		binding.quickSettingsSize.setOnClickListener(v -> {
			microLoader.cycleScreenScaleRatio();
			applyRuntimeDisplaySettings(false);
		});
		binding.quickSettingsOrientation.setOnClickListener(v -> {
			microLoader.cycleOrientation();
			applyRuntimeDisplaySettings(true);
		});
		binding.quickSettingsScaleType.setOnClickListener(v -> {
			microLoader.cycleScreenScaleType();
			applyRuntimeDisplaySettings(false);
		});
		binding.quickSettingsQuality.setOnClickListener(v -> {
			microLoader.cycleDisplayPreset();
			applyRuntimeDisplaySettings(false);
		});
		updateQuickSettingsLabels();
	}

	public void showQuickSettingsOverlay() {
		if (binding == null || microLoader == null || isQuickMapVisible()
				|| binding.displayableContainer.getChildCount() == 0) {
			return;
		}
		updateQuickSettingsLabels();
		binding.quickSettingsOverlay.setVisibility(View.VISIBLE);
		scheduleQuickSettingsHide();
	}

	private void hideQuickSettingsOverlay() {
		if (binding == null) {
			return;
		}
		quickSettingsHandler.removeCallbacks(hideQuickSettingsRunnable);
		binding.quickSettingsOverlay.setVisibility(View.GONE);
	}

	private void scheduleQuickSettingsHide() {
		quickSettingsHandler.removeCallbacks(hideQuickSettingsRunnable);
		quickSettingsHandler.postDelayed(hideQuickSettingsRunnable, QUICK_SETTINGS_HIDE_MS);
	}

	private void applyRuntimeDisplaySettings(boolean updateOrientation) {
		if (updateOrientation) {
			setOrientation(microLoader.getOrientation());
		}
		if (current instanceof Canvas) {
			Canvas canvas = (Canvas) current;
			canvas.updateSize();
			canvas.updateRenderingSettings();
		}
		updateQuickSettingsLabels();
		scheduleQuickSettingsHide();
	}

	private void updateQuickSettingsLabels() {
		if (binding == null || microLoader == null) {
			return;
		}
		String[] orientationLabels = {"Default", "Auto", "Port", "Land"};
		String[] scaleLabels = {"1:1", "Fit", "Full"};
		String[] presetLabels = getResources().getStringArray(R.array.quick_display_preset_entries);
		int orientation = clampIndex(microLoader.getOrientation(), orientationLabels.length);
		int scaleType = clampIndex(microLoader.getScreenScaleType(), scaleLabels.length);
		int preset = clampIndex(microLoader.getDisplayPreset(), presetLabels.length);
		binding.quickSettingsSize.setText(getString(R.string.quick_settings_size)
				+ "\n" + microLoader.getScreenScaleRatio() + "%");
		binding.quickSettingsOrientation.setText(getString(R.string.quick_settings_orientation)
				+ "\n" + orientationLabels[orientation]);
		binding.quickSettingsScaleType.setText(getString(R.string.quick_settings_scale)
				+ "\n" + scaleLabels[scaleType]);
		binding.quickSettingsQuality.setText(getString(R.string.quick_settings_look)
				+ "\n" + presetLabels[preset]);
	}

	private int clampIndex(int index, int size) {
		if (index < 0 || index >= size) {
			return 0;
		}
		return index;
	}

	private void rebuildQuickMapProfiles() {
		binding.quickMapProfiles.removeAllViews();
		ArrayList<ProfileModel.KeyMappingProfile> profiles = microLoader.getKeyMappingProfiles();
		int active = microLoader.getActiveKeyMappingProfile();
		for (int i = 0, size = profiles.size(); i < size; i++) {
			ProfileModel.KeyMappingProfile profile = profiles.get(i);
			final int index = i;
			String name = profile.name == null ? "Profile " + (i + 1) : profile.name;
			Button button = createQuickMapButton((i == active ? "* " : "") + name);
			button.setOnClickListener(v -> {
				microLoader.setActiveKeyMappingProfile(index);
				menuKey = microLoader.getMenuKeyCode();
				quickMapTargetKey = 0;
				quickMapTargetLabel = null;
				binding.quickMapHint.setText(R.string.quick_map_pick_target);
				rebuildQuickMapProfiles();
			});
			binding.quickMapProfiles.addView(button);
		}
	}

	private void rebuildQuickMapTargets() {
		binding.quickMapTargets.removeAllViews();
		for (int i = 0; i < QUICK_MAP_TARGET_KEYS.length; i++) {
			final int targetKey = QUICK_MAP_TARGET_KEYS[i];
			final String label = QUICK_MAP_TARGET_LABELS[i];
			Button button = createQuickMapButton(label);
			button.setOnClickListener(v -> {
				quickMapTargetKey = targetKey;
				quickMapTargetLabel = label;
				binding.quickMapHint.setText(getString(R.string.quick_map_waiting, label));
			});
			binding.quickMapTargets.addView(button);
		}
	}

	private Button createQuickMapButton(String label) {
		Button button = new Button(this);
		button.setAllCaps(false);
		button.setSingleLine(true);
		button.setText(label);
		button.setFocusable(true);
		button.setMinWidth(dp(56));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.setMargins(0, 0, dp(8), 0);
		button.setLayoutParams(lp);
		return button;
	}

	private void showQuickMapOverlay() {
		if (binding == null || binding.displayableContainer.getChildCount() == 0) {
			return;
		}
		quickMapTargetKey = 0;
		quickMapTargetLabel = null;
		quickMapCapturedKeyUp = KeyEvent.KEYCODE_UNKNOWN;
		binding.quickMapHint.setText(R.string.quick_map_pick_target);
		rebuildQuickMapProfiles();
		binding.quickMapOverlay.setVisibility(View.VISIBLE);
		binding.quickMapOverlay.requestFocus();
		showSystemUI();
		MidletThread.pauseApp();
	}

	private void hideQuickMapOverlay() {
		cancelQuickMapTrigger();
		quickMapTargetKey = 0;
		quickMapTargetLabel = null;
		quickMapCapturedKeyUp = KeyEvent.KEYCODE_UNKNOWN;
		binding.quickMapOverlay.setVisibility(View.GONE);
		if (visible) {
			MidletThread.resumeApp();
		}
		if (current instanceof Canvas) {
			hideSystemUI();
		}
	}

	private void cancelQuickMapTrigger() {
		if (binding != null) {
			binding.midletFrame.removeCallbacks(openQuickMapRunnable);
		}
		selectQuickMapTracking = false;
	}

	private void sendDeferredSelectPress() {
		long now = SystemClock.uptimeMillis();
		binding.displayableContainer.dispatchKeyEvent(new KeyEvent(now, now,
				KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT, 0));
		binding.displayableContainer.dispatchKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(),
				KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_SELECT, 0));
	}

	private void saveQuickKeyMapping(int inputCode) {
		String targetLabel = quickMapTargetLabel == null ? getMidpKeyLabel(quickMapTargetKey) : quickMapTargetLabel;
		microLoader.saveQuickKeyMapping(inputCode, quickMapTargetKey);
		menuKey = microLoader.getMenuKeyCode();
		quickMapTargetKey = 0;
		quickMapTargetLabel = null;
		binding.quickMapHint.setText(R.string.quick_map_pick_target);
		rebuildQuickMapProfiles();
		Toast.makeText(this, getString(R.string.quick_map_saved,
				targetLabel, getInputLabel(inputCode)), Toast.LENGTH_SHORT).show();
	}

	private boolean isQuickMapVisible() {
		return binding != null && binding.quickMapOverlay.getVisibility() == View.VISIBLE;
	}

	private boolean isIgnoredQuickMapKey(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_HOME
				|| keyCode == KeyEvent.KEYCODE_VOLUME_UP
				|| keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
	}

	private int getQuickMapStickInput(MotionEvent event) {
		float x = getCenteredAxis(event, MotionEvent.AXIS_X);
		float y = getCenteredAxis(event, MotionEvent.AXIS_Y);
		float hatX = getCenteredAxis(event, MotionEvent.AXIS_HAT_X);
		float hatY = getCenteredAxis(event, MotionEvent.AXIS_HAT_Y);
		if (x == 0) {
			x = hatX;
		}
		if (y == 0) {
			y = hatY;
		}
		if (x < -QUICK_MAP_AXIS_DEADZONE) {
			return KeyMapper.INPUT_STICK_LEFT;
		}
		if (x > QUICK_MAP_AXIS_DEADZONE) {
			return KeyMapper.INPUT_STICK_RIGHT;
		}
		if (y < -QUICK_MAP_AXIS_DEADZONE) {
			return KeyMapper.INPUT_STICK_UP;
		}
		if (y > QUICK_MAP_AXIS_DEADZONE) {
			return KeyMapper.INPUT_STICK_DOWN;
		}
		return 0;
	}

	private boolean isFromSource(MotionEvent event, int source) {
		return (event.getSource() & source) == source;
	}

	private float getCenteredAxis(MotionEvent event, int axis) {
		InputDevice device = event.getDevice();
		if (device == null) {
			return 0;
		}
		InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
		if (range == null) {
			return 0;
		}
		float value = event.getAxisValue(axis);
		return Math.abs(value) > Math.max(range.getFlat(), 0.15f) ? value : 0;
	}

	private String getInputLabel(int inputCode) {
		switch (inputCode) {
			case KeyMapper.INPUT_STICK_UP:
				return "STICK_UP";
			case KeyMapper.INPUT_STICK_DOWN:
				return "STICK_DOWN";
			case KeyMapper.INPUT_STICK_LEFT:
				return "STICK_LEFT";
			case KeyMapper.INPUT_STICK_RIGHT:
				return "STICK_RIGHT";
			default:
				return KeyEvent.keyCodeToString(inputCode);
		}
	}

	private String getMidpKeyLabel(int keyCode) {
		for (int i = 0; i < QUICK_MAP_TARGET_KEYS.length; i++) {
			if (QUICK_MAP_TARGET_KEYS[i] == keyCode) {
				return QUICK_MAP_TARGET_LABELS[i];
			}
		}
		return String.valueOf(keyCode);
	}

	private int dp(int value) {
		return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
	}

	@Override
	public void openOptionsMenu() {
		if (!actionBarEnabled &&
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && current instanceof Canvas) {
			showSystemUI();
		}
		super.openOptionsMenu();
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
			showExitConfirmation();
			return true;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
				&& (event.getFlags() & (KeyEvent.FLAG_LONG_PRESS | KeyEvent.FLAG_CANCELED)) == 0) {
			openOptionsMenu();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		// Intentionally overridden by empty due to support for back-key remapping.
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.midlet_displayable, menu);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			menu.findItem(R.id.action_lock_orientation).setVisible(true);
		}
		if (actionBarEnabled) {
			menu.findItem(R.id.action_ime_keyboard).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			menu.findItem(R.id.action_take_screenshot).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		}
		if (inputMethodManager == null) {
			menu.findItem(R.id.action_ime_keyboard).setVisible(false);
		}
		if (ContextHolder.getVk() == null) {
			menu.findItem(R.id.action_submenu_vk).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (current instanceof Canvas) {
			menu.setGroupVisible(R.id.action_group_canvas, true);
			VirtualKeyboard vk = ContextHolder.getVk();
			if (vk != null) {
				boolean visible = vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF;
				menu.findItem(R.id.action_layout_edit_finish).setVisible(visible);
			}
		} else {
			menu.setGroupVisible(R.id.action_group_canvas, false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_exit_midlet) {
			showExitConfirmation();
		} else if (id == R.id.action_save_log) {
			saveLog();
		} else if (id == R.id.action_lock_orientation) {
			if (item.isChecked()) {
				VirtualKeyboard vk = ContextHolder.getVk();
				int orientation = vk != null && vk.isPhone() ? ORIENTATION_PORTRAIT : microLoader.getOrientation();
				setOrientation(orientation);
				item.setChecked(false);
			} else {
				item.setChecked(true);
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
			}
		} else if (id == R.id.action_ime_keyboard) {
			inputMethodManager.toggleSoftInputFromWindow(binding.displayableContainer.getWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
		} else if (id == R.id.action_take_screenshot) {
			takeScreenshot();
		} else if (id == R.id.action_limit_fps) {
			showLimitFpsDialog();
		} else if (ContextHolder.getVk() != null) {
			// Handled only when virtual keyboard is enabled
			handleVkOptions(id);
		}
		return true;
	}

	private void handleVkOptions(int id) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (id == R.id.action_layout_edit_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_KEYS);
			Toast.makeText(this, R.string.layout_edit_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_scale_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_SCALES);
			Toast.makeText(this, R.string.layout_scale_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_edit_finish) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_EOF);
			Toast.makeText(this, R.string.layout_edit_finished, Toast.LENGTH_SHORT).show();
			showSaveVkAlert(false);
		} else if (id == R.id.action_layout_switch) {
			showSetLayoutDialog();
		} else if (id == R.id.action_hide_buttons) {
			showHideButtonDialog();
		}
	}

	@SuppressLint("CheckResult")
	private void takeScreenshot() {
		microLoader.takeScreenshot((Canvas) current, new SingleObserver<String>() {
			@Override
			public void onSubscribe(@NonNull Disposable d) {
			}

			@Override
			public void onSuccess(@NonNull String s) {
				Toast.makeText(MicroActivity.this, getString(R.string.screenshot_saved)
						+ " " + s, Toast.LENGTH_LONG).show();
			}

			@Override
			public void onError(@NonNull Throwable e) {
				e.printStackTrace();
				Toast.makeText(MicroActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void saveLog() {
		try {
			LogUtils.writeLog();
			Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
		}
	}

	private void showHideButtonDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		boolean[] states = vk.getKeysVisibility();
		boolean[] changed = states.clone();
		new AlertDialog.Builder(this)
				.setTitle(R.string.hide_buttons)
				.setMultiChoiceItems(vk.getKeyNames(), changed, (dialog, which, isChecked) -> {})
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					if (!Arrays.equals(states, changed)) {
						vk.setKeysVisibility(changed);
						showSaveVkAlert(true);
					}
				}).show();
	}

	private void showSaveVkAlert(boolean keepScreenPreferred) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.CONFIRMATION_REQUIRED);
		builder.setMessage(R.string.pref_vk_save_alert);
		builder.setNegativeButton(android.R.string.no, null);
		AlertDialog dialog = builder.create();

		final VirtualKeyboard vk = ContextHolder.getVk();
		if (vk.isPhone()) {
			AppCompatCheckBox cb = new AppCompatCheckBox(this);
			cb.setText(R.string.opt_save_screen_params);
			cb.setChecked(keepScreenPreferred);

			TypedValue out = new TypedValue();
			getTheme().resolveAttribute(androidx.appcompat.R.attr.dialogPreferredPadding, out, true);
			int paddingH = getResources().getDimensionPixelOffset(out.resourceId);
			int paddingT = getResources().getDimensionPixelOffset(androidx.appcompat.R.dimen.abc_dialog_padding_top_material);
			dialog.setView(cb, paddingH, paddingT, paddingH, 0);

			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) -> {
				if (cb.isChecked()) {
					vk.saveScreenParams();
				}
				vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
			});
		} else {
			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) ->
					ContextHolder.getVk().onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM));
		}
		dialog.show();
	}

	private void showSetLayoutDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.layout_switch)
				.setSingleChoiceItems(R.array.PREF_VK_TYPE_ENTRIES, vk.getLayout(), null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					vk.setLayout(((AlertDialog) d).getListView().getCheckedItemPosition());
					if (vk.isPhone()) {
						setOrientation(ORIENTATION_PORTRAIT);
					} else {
						setOrientation(microLoader.getOrientation());
					}
				});
		builder.show();
	}

	private void showLimitFpsDialog() {
		EditText editText = new EditText(this);
		editText.setHint(R.string.unlimited);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		editText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
		editText.setMaxLines(1);
		editText.setSingleLine(true);
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_LIMIT_FPS)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					Editable text = editText.getText();
					int fps = 0;
					try {
						fps = TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text.toString().trim());
					} catch (NumberFormatException ignored) {
					}
					microLoader.setLimitFps(fps);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.reset, ((d, which) -> microLoader.setLimitFps(-1)))
				.show();
	}

	@Override
	public boolean onContextItemSelected(@NonNull MenuItem item) {
		if (current instanceof Form) {
			((Form) current).contextMenuItemSelected(item);
		} else if (current instanceof List) {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			((List) current).contextMenuItemSelected(item, info.position);
		}

		return super.onContextItemSelected(item);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		ContextHolder.notifyOnActivityResult(requestCode, resultCode, data);
	}

	public String getAppName() {
		return appName;
	}

	private class SetCurrentEvent extends SimpleEvent {
		private final Displayable current;
		private final Displayable next;

		private SetCurrentEvent(Displayable current, Displayable next) {
			this.current = current;
			this.next = next;
		}

		@Override
		public void process() {
			closeOptionsMenu();
			if (current != null) {
				current.clearDisplayableView();
			}
			if (next instanceof Alert) {
				return;
			}
			binding.displayableContainer.removeAllViews();
			ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
			LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) binding.toolbar.getLayoutParams();
			hideSystemUI();
			actionBar.hide();
			layoutParams.height = 0;
			binding.toolbar.setVisibility(View.GONE);
			binding.overlayView.setLocation(0, 0);
			binding.toolbar.setLayoutParams(layoutParams);
			invalidateOptionsMenu();
			if (next != null) {
				binding.displayableContainer.addView(next.getDisplayableView());
			}
		}
	}

	@Override
	protected void onDestroy() {
		binding = null;
		super.onDestroy();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 1) {
			synchronized (LocationProviderImpl.permissionLock) {
				LocationProviderImpl.permissionLock.notify();
			}
			LocationProviderImpl.permissionResult = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		}
	}
}
