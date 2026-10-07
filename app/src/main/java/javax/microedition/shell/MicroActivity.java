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
import android.hardware.input.InputManager;
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
import java.util.HashSet;
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
import ru.playsoftware.j2meloader.diagnostics.LaunchDiagnostics;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.input.ControllerMapperView;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.playsoftware.j2meloader.util.Screenshots;

public class MicroActivity extends AppCompatActivity {
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;
	private static final int QUICK_MAP_HOLD_MS = 350;
	private static final int QUICK_SETTINGS_HIDE_MS = 5000;

	private Displayable current;
	private boolean visible;
	private boolean screenshotPending;
	private boolean actionBarEnabled;
	private boolean statusBarEnabled;
	private MicroLoader microLoader;
	private String appName;
	private InputMethodManager inputMethodManager;
	private int menuKey;
	private String appPath;
	private ControllerMapperView controllerMapper;
	private ru.playsoftware.j2meloader.input.GameMenuDialog gameMenu;
	private final HashSet<Integer> overlayHeldKeys = new HashSet<>();
	private InputManager inputManager;
	private final ControllerInput controllerInput = new ControllerInput(KeyMapper::getInputMapping,
			new ControllerInput.Sink() {
				public void press(int key) { if (current instanceof Canvas) ((Canvas) current).postKeyPressed(key); }
				public void release(int key) { if (current instanceof Canvas) ((Canvas) current).postKeyReleased(key); }
				public void repeat(int key) { if (current instanceof Canvas) ((Canvas) current).postKeyRepeated(key); }
			});
	private final InputManager.InputDeviceListener controllerListener = new InputManager.InputDeviceListener() {
		public void onInputDeviceAdded(int id) { }
		public void onInputDeviceChanged(int id) { controllerInput.removeDevice(id); }
		public void onInputDeviceRemoved(int id) { controllerInput.removeDevice(id); }
	};
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
			LaunchDiagnostics.record(this, "process_started", appName, appPath);
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
			if (vk.isPhone() && orientation == ORIENTATION_DEFAULT) {
				orientation = ORIENTATION_PORTRAIT;
			}
		}
		setOrientation(BuildConfig.HANDHELD_MODE ? ORIENTATION_LANDSCAPE : orientation);
		menuKey = microLoader.getMenuKeyCode();
		setupQuickSettingsOverlay();
		binding.gameScreenshotButton.setOnClickListener(v -> takeScreenshot());
		binding.gameMenuButton.setVisibility(BuildConfig.HANDHELD_MODE ? View.GONE : View.VISIBLE);
		binding.gameMenuButton.setOnClickListener(v -> openOptionsMenu());
		binding.gameDisplayButton.setVisibility(BuildConfig.HANDHELD_MODE ? View.GONE : View.VISIBLE);
		binding.gameDisplayButton.setOnClickListener(v -> {
			if (binding.quickSettingsOverlay.getVisibility() == View.VISIBLE) hideQuickSettingsOverlay();
			else { showQuickSettingsOverlay(); binding.quickScreenOptions.setVisibility(View.VISIBLE); }
		});
		inputManager = (InputManager) getSystemService(INPUT_SERVICE);
		inputManager.registerInputDeviceListener(controllerListener, quickSettingsHandler);
		inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

		try {
			loadMIDlet();
			LaunchDiagnostics.record(this, "midlet_loaded", appName, null);
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
		updateScreenshotButton();
		if (!isQuickMapVisible() && (gameMenu == null || !gameMenu.isShowing())) MidletThread.resumeApp();
	}

	@Override
	public void onPause() {
		controllerInput.clear();
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null) vk.cancel();
		visible = false;
		hideSoftInput();
		cancelQuickMapTrigger();
		hideQuickSettingsOverlay();
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
		if (!hasFocus) {
			controllerInput.clear();
			VirtualKeyboard vk = ContextHolder.getVk();
			if (vk != null) vk.cancel();
		}
		if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
				current instanceof Canvas && !isQuickMapVisible()) {
			hideSystemUI();
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void setOrientation(int orientation) {
		if (BuildConfig.HANDHELD_MODE) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
			return;
		}
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
		synchronized (controllerInput) {
			controllerInput.clear();
			ViewHandler.postEvent(new SetCurrentEvent(current, displayable));
			current = displayable;
		}
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
		int key = event.getKeyCode();
		if (!isQuickMapVisible() && overlayHeldKeys.contains(key)) {
			if (event.getAction() == KeyEvent.ACTION_UP) overlayHeldKeys.remove(key);
			return true;
		}
		if (isQuickMapVisible()) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) overlayHeldKeys.add(key);
			else if (event.getAction() == KeyEvent.ACTION_UP) overlayHeldKeys.remove(key);
			if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT && selectQuickMapOpened) {
				if (event.getAction() == KeyEvent.ACTION_UP) {
					selectQuickMapOpened = false;
					cancelQuickMapTrigger();
				}
				return true;
			}
			if (!controllerMapper.handleKey(event)) super.dispatchKeyEvent(event);
			return true;
		}
		if (handleQuickMapKeyEvent(event)) {
			return true;
		}
		if (current instanceof Canvas && event.getKeyCode() != menuKey
				&& (KeyEvent.isGamepadButton(event.getKeyCode()) || ControllerInput.direction(event.getKeyCode()) != 0)) {
			controllerInput.key(event.getDeviceId(), event.getKeyCode(),
					event.getAction() == KeyEvent.ACTION_DOWN, event.getRepeatCount() != 0);
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
			if (event.getAction() == MotionEvent.ACTION_MOVE) controllerMapper.captureAxes(event);
			return true;
		}
		if (current instanceof Canvas && event.getAction() == MotionEvent.ACTION_MOVE
				&& isFromSource(event, InputDevice.SOURCE_JOYSTICK)) {
			controllerInput.axes(event.getDeviceId(), getCenteredAxis(event, MotionEvent.AXIS_X),
					getCenteredAxis(event, MotionEvent.AXIS_Y), getCenteredAxis(event, MotionEvent.AXIS_HAT_X),
					getCenteredAxis(event, MotionEvent.AXIS_HAT_Y),
					Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
					Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)));
			return true;
		}
		return super.dispatchGenericMotionEvent(event);
	}

	private boolean handleQuickMapKeyEvent(KeyEvent event) {
		if (binding == null || microLoader == null) {
			return false;
		}
		int keyCode = event.getKeyCode();
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

	private void setupQuickSettingsOverlay() {
		binding.quickSettingsOverlay.setOnClickListener(v -> hideQuickSettingsOverlay());
		binding.quickSettingsQuality.setOnClickListener(v -> {
			microLoader.cycleDisplayPreset();
			applyRuntimeDisplaySettings(false);
		});
		binding.quickSettingsScreen.setOnClickListener(v -> {
			boolean show = binding.quickScreenOptions.getVisibility() != View.VISIBLE;
			binding.quickScreenOptions.setVisibility(show ? View.VISIBLE : View.GONE);
			scheduleQuickSettingsHide();
		});
		binding.quickSettingsControls.setOnClickListener(v -> {
			hideQuickSettingsOverlay();
			if (BuildConfig.HANDHELD_MODE) showQuickMapOverlay();
			else openOptionsMenu();
		});
		binding.quickScreenAuto.setOnClickListener(v -> applyQuickScreen(0, 0));
		binding.quickScreen176.setOnClickListener(v -> applyQuickScreen(176, 220));
		binding.quickScreen240.setOnClickListener(v -> applyQuickScreen(240, 320));
		binding.quickScreenLand.setOnClickListener(v -> applyQuickScreen(320, 240));
		binding.quickScreen480.setOnClickListener(v -> applyQuickScreen(480, 800));
		binding.quickScreenSwap.setOnClickListener(v -> applyQuickScreen(-1, -1));
		binding.quickScreenRotate.setOnClickListener(v -> applyImageRotation(microLoader.getScreenRotation() + 90));
		updateQuickSettingsLabels();
	}

	private void applyQuickScreen(int width, int height) {
		if (width == 0) {
			microLoader.restoreDetectedScreenSize();
		} else if (width < 0) {
			microLoader.rotateScreen();
		} else {
			microLoader.setScreenSize(width, height);
		}
		binding.quickScreenOptions.setVisibility(View.GONE);
		applyRuntimeDisplaySettings(false);
	}

	private void applyImageRotation(int degrees) {
		if (!(current instanceof Canvas)) return;
		if (!microLoader.setScreenRotation(degrees)) {
			Toast.makeText(this, R.string.display_save_failed, Toast.LENGTH_LONG).show();
			return;
		}
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null) vk.cancel();
		applyRuntimeDisplaySettings(false);
	}

	public void showQuickSettingsOverlay() {
		if (binding == null || microLoader == null || isQuickMapVisible()
				|| binding.displayableContainer.getChildCount() == 0) {
			return;
		}
		updateQuickSettingsLabels();
		binding.quickSettingsOverlay.setVisibility(View.VISIBLE);
		binding.gameDisplayButton.setVisibility(View.GONE);
		binding.gameMenuButton.setVisibility(View.GONE);
		updateScreenshotButton();
		scheduleQuickSettingsHide();
	}

	private void hideQuickSettingsOverlay() {
		if (binding == null) {
			return;
		}
		quickSettingsHandler.removeCallbacks(hideQuickSettingsRunnable);
		binding.quickScreenOptions.setVisibility(View.GONE);
		binding.quickSettingsOverlay.setVisibility(View.GONE);
		binding.gameDisplayButton.setVisibility(BuildConfig.HANDHELD_MODE ? View.GONE : View.VISIBLE);
		binding.gameMenuButton.setVisibility(BuildConfig.HANDHELD_MODE ? View.GONE : View.VISIBLE);
		updateScreenshotButton();
	}

	private void updateScreenshotButton() {
		if (binding == null) return;
		boolean show = current instanceof Canvas && Screenshots.isButtonVisible(this)
				&& binding.quickSettingsOverlay.getVisibility() != View.VISIBLE && !isQuickMapVisible();
		binding.gameScreenshotButton.setVisibility(show ? View.VISIBLE : View.GONE);
		binding.gameScreenshotButton.setEnabled(!screenshotPending);
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
		String[] presetLabels = getResources().getStringArray(R.array.quick_display_preset_entries);
		int preset = clampIndex(microLoader.getDisplayPreset(), presetLabels.length);
		binding.quickSettingsQuality.setText(getString(R.string.quick_settings_view)
				+ "\n" + presetLabels[preset]);
		binding.quickSettingsScreen.setText(getString(R.string.quick_settings_screen)
				+ "\n" + microLoader.getScreenWidth() + "x" + microLoader.getScreenHeight());
		binding.quickSettingsControls.setText(R.string.quick_settings_controls);
		binding.quickScreenRotate.setText(getString(R.string.quick_rotation_value, microLoader.getScreenRotation()));
		binding.quickScreenRotate.setEnabled(current instanceof Canvas);
	}

	private int clampIndex(int index, int size) {
		if (index < 0 || index >= size) {
			return 0;
		}
		return index;
	}

	private void showQuickMapOverlay() {
		if (binding == null || microLoader == null || isQuickMapVisible()
				|| binding.displayableContainer.getChildCount() == 0) {
			return;
		}
		controllerInput.clear();
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null) vk.cancel();
		hideQuickSettingsOverlay();
		MidletThread.pauseApp();
		controllerMapper = new ControllerMapperView(this, appName,
				microLoader.getKeyMappingProfiles(), microLoader.getActiveKeyMappingProfile(),
				new ControllerMapperView.Listener() {
					public void save(ArrayList<ProfileModel.KeyMappingProfile> profiles, int active) {
						controllerInput.clear();
						if (microLoader.saveControllerProfiles(profiles, active)) {
							menuKey = microLoader.getMenuKeyCode();
							hideQuickMapOverlay();
						} else Toast.makeText(MicroActivity.this, R.string.mapper_save_failed, Toast.LENGTH_LONG).show();
					}
					public void cancel() { hideQuickMapOverlay(); }
				});
		binding.quickMapOverlay.removeAllViews();
		binding.quickMapOverlay.addView(controllerMapper, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		binding.quickMapOverlay.setVisibility(View.VISIBLE);
		updateScreenshotButton();
		hideSystemUI();
	}

	private void hideQuickMapOverlay() {
		cancelQuickMapTrigger();
		controllerInput.clear();
		binding.quickMapOverlay.setVisibility(View.GONE);
		binding.quickMapOverlay.removeAllViews();
		controllerMapper = null;
		updateScreenshotButton();
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

	private boolean isQuickMapVisible() {
		return binding != null && controllerMapper != null && binding.quickMapOverlay.getVisibility() == View.VISIBLE;
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

	@Override
	public void openOptionsMenu() {
		if (microLoader == null || isQuickMapVisible() || (gameMenu != null && gameMenu.isShowing())) return;
		cancelQuickMapTrigger();
		controllerInput.clear();
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null) vk.cancel();
		hideQuickSettingsOverlay();
		MidletThread.pauseApp();
		gameMenu = new ru.playsoftware.j2meloader.input.GameMenuDialog(this, appName);
		gameMenu.setOnDismissListener(d -> {
			if (visible && !isQuickMapVisible()) MidletThread.resumeApp();
			hideSystemUI();
		});
		gameMenu.show();
		showGameMenuPage();
	}

	private void showGameMenuPage() {
		gameMenu.page("Game menu");
		gameMenu.action("Resume", android.R.drawable.ic_media_play, () -> gameMenu.dismiss());
		gameMenu.action("Display", R.drawable.ic_quick_size, this::showDisplayPage);
		gameMenu.action("Controller mapping", R.drawable.ic_action_keyboard, () -> {
			showQuickMapOverlay(); gameMenu.dismiss();
		});
		if (!BuildConfig.HANDHELD_MODE && ContextHolder.getVk() != null) {
			gameMenu.action("Touch controls", R.drawable.ic_baseline_tune_24, this::showTouchPage);
		}
		gameMenu.action("More", android.R.drawable.ic_menu_more, this::showMorePage);
		gameMenu.action("Exit game", android.R.drawable.ic_menu_close_clear_cancel, () -> {
			gameMenu.dismiss(); showExitConfirmation();
		});
	}

	private void showDisplayPage() {
		gameMenu.page("Display");
		if (current instanceof Canvas) gameMenu.choice(getString(R.string.quick_rotate_image),
				new String[]{"0\u00b0", "90\u00b0", "180\u00b0", "270\u00b0"},
				microLoader.getScreenRotation() / 90, index -> applyImageRotation(index * 90));
		String[] labels = getResources().getStringArray(R.array.quick_display_preset_entries);
		gameMenu.choice("Look", labels, clampIndex(microLoader.getDisplayPreset(), labels.length), index -> {
			microLoader.applyDisplayPreset(index); applyRuntimeDisplaySettings(false);
		});
		gameMenu.action("Screen: " + microLoader.getScreenWidth() + " x " + microLoader.getScreenHeight(),
				R.drawable.ic_quick_size, () -> {
			gameMenu.page("Game resolution");
			gameMenu.action("Auto",0,()-> { applyQuickScreen(0,0); showDisplayPage(); });
			gameMenu.action("176 x 220",0,()-> { applyQuickScreen(176,220); showDisplayPage(); });
			gameMenu.action("240 x 320",0,()-> { applyQuickScreen(240,320); showDisplayPage(); });
			gameMenu.action("320 x 240",0,()-> { applyQuickScreen(320,240); showDisplayPage(); });
			gameMenu.action("480 x 800",0,()-> { applyQuickScreen(480,800); showDisplayPage(); });
			gameMenu.action("Back",android.R.drawable.ic_media_previous,this::showDisplayPage);
		});
		if (!BuildConfig.HANDHELD_MODE) {
			gameMenu.action("Rotate device layout",R.drawable.ic_quick_orientation,()-> {
				microLoader.setOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
						? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT);
				setOrientation(microLoader.getOrientation());
				gameMenu.dismiss();
			});
		}
		gameMenu.action("Back",android.R.drawable.ic_media_previous,this::showGameMenuPage);
	}

	private void showMorePage() {
		gameMenu.page("More");
		gameMenu.toggle(getString(R.string.screenshot_button), Screenshots.isButtonVisible(this), value -> {
			Screenshots.setButtonVisible(this, value);
			updateScreenshotButton();
		});
		if (current instanceof Canvas) gameMenu.action("Screenshot", R.drawable.ic_action_screenshot, () -> {
			gameMenu.dismiss(); takeScreenshot();
		});
		gameMenu.action("System keyboard", R.drawable.ic_action_keyboard, () -> {
			gameMenu.dismiss();
			inputMethodManager.toggleSoftInputFromWindow(binding.displayableContainer.getWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
		});
		gameMenu.action("Frame rate limit", R.drawable.ic_quick_quality, () -> {
			gameMenu.dismiss(); showLimitFpsDialog();
		});
		gameMenu.action("Save log", android.R.drawable.ic_menu_save, this::saveLog);
		gameMenu.action("Back", android.R.drawable.ic_media_previous, this::showGameMenuPage);
	}

	private void showTouchPage() {
		ProfileModel p = microLoader.getTouchSettings();
		gameMenu.page("Touch controls");
		int layout = p.touchLayout == null ? 2 : p.touchLayout;
		String[] layouts = {"Phone", "Gamepad", "Legacy"};
		gameMenu.choice("Layout", layouts, Math.max(0,Math.min(2,layout)), index -> {
			p.touchLayout = index; applyTouchSettings(); showTouchPage();
		});
		if (layout == 2) {
			gameMenu.action("Legacy layout editor", R.drawable.ic_baseline_tune_24, this::showLegacyTouchPage);
			gameMenu.action("Back",android.R.drawable.ic_media_previous,this::showGameMenuPage);
			return;
		}
		gameMenu.choice("Size", new String[]{"Standard","Large","Extra large"},Math.max(0,Math.min(2,p.touchSize)), index -> {
			p.touchSize = index; applyTouchSettings();
		});
		gameMenu.opacity(p.touchOpacity, value -> {
			p.touchOpacity = value; applyTouchSettings();
		});
		boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		float reach = landscape ? p.touchLandscapeReach : p.touchPortraitReach;
		gameMenu.choice("Position", new String[]{"Bottom","Raised"},reach > 0 ? 1 : 0, index -> {
			if (landscape) p.touchLandscapeReach = index;
			else p.touchPortraitReach = index;
			applyTouchSettings();
		});
		gameMenu.action("Back",android.R.drawable.ic_media_previous,this::showGameMenuPage);
	}

	private void applyTouchSettings() {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null) vk.refreshTouchLayout();
		if (!microLoader.saveTouchSettings()) Toast.makeText(this, R.string.mapper_save_failed, Toast.LENGTH_LONG).show();
	}

	private void showLegacyTouchPage() {
		gameMenu.page("Legacy controls");
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF) {
			gameMenu.action("Finish editing", android.R.drawable.ic_menu_save, () -> {
				gameMenu.dismiss(); handleVkOptions(R.id.action_layout_edit_finish);
			});
		} else {
			gameMenu.action("Move buttons", R.drawable.ic_baseline_tune_24, () -> {
				gameMenu.dismiss(); handleVkOptions(R.id.action_layout_edit_mode);
			});
			gameMenu.action("Resize buttons", R.drawable.ic_quick_size, () -> {
				gameMenu.dismiss(); handleVkOptions(R.id.action_layout_scale_mode);
			});
		}
		gameMenu.action("Choose layout", R.drawable.ic_action_keyboard, () -> { gameMenu.dismiss(); showSetLayoutDialog(); });
		gameMenu.action("Visible buttons", R.drawable.ic_list, () -> { gameMenu.dismiss(); showHideButtonDialog(); });
		gameMenu.action("Back", android.R.drawable.ic_media_previous, this::showTouchPage);
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
				int orientation = microLoader.getOrientation();
				if (orientation == ORIENTATION_DEFAULT && vk != null && vk.isPhone()) orientation = ORIENTATION_PORTRAIT;
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
		if (!(current instanceof Canvas) || screenshotPending || !visible) return;
		screenshotPending = true;
		updateScreenshotButton();
		microLoader.takeScreenshot((Canvas) current, new SingleObserver<String>() {
			@Override
			public void onSubscribe(@NonNull Disposable d) {
			}

			@Override
			public void onSuccess(@NonNull String s) {
				screenshotPending = false;
				updateScreenshotButton();
				Toast.makeText(getApplicationContext(), R.string.screenshot_gallery_saved, Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onError(@NonNull Throwable e) {
				screenshotPending = false;
				updateScreenshotButton();
				e.printStackTrace();
				Toast.makeText(getApplicationContext(), R.string.screenshot_failed, Toast.LENGTH_SHORT).show();
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
			updateScreenshotButton();
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
		cancelQuickMapTrigger();
		if (gameMenu != null) {
			gameMenu.setOnDismissListener(null);
			gameMenu.dismiss();
		}
		quickSettingsHandler.removeCallbacksAndMessages(null);
		if (inputManager != null) inputManager.unregisterInputDeviceListener(controllerListener);
		controllerInput.clear();
		if (isFinishing()) {
			LaunchDiagnostics.record(this, "clean_exit", appName, null);
		}
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
