/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2018 Nikita Shakarun
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

package ru.playsoftware.j2meloader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewConfiguration;
import android.view.KeyEvent;
import androidx.preference.PreferenceManager;

import java.io.File;

import ru.playsoftware.j2meloader.applist.AppsListFragment;
import ru.playsoftware.j2meloader.base.BaseActivity;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.woesss.j2me.installer.InstallerDialog;

import static ru.playsoftware.j2meloader.util.Constants.PREF_TOOLBAR;

public class MainActivity extends BaseActivity {
	private SharedPreferences preferences;
	private final ru.playsoftware.j2meloader.applist.LibraryTriggers libraryTriggers =
			new ru.playsoftware.j2meloader.applist.LibraryTriggers();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		FileUtils.initWorkDir(new File(Config.getEmulatorDir()));
		if (savedInstanceState == null) {
			Intent intent = getIntent();
			Uri uri = null;
			if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
				uri = intent.getData();
			}
			AppsListFragment fragment = AppsListFragment.newInstance(uri);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.container, fragment).commit();
		}
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (!preferences.contains(PREF_TOOLBAR)) {
			boolean enable = !ViewConfiguration.get(this).hasPermanentMenuKey();
			preferences.edit().putBoolean(PREF_TOOLBAR, enable).apply();
		}
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (!hasFocus) libraryTriggers.reset();
		if (hasFocus && ru.playsoftware.j2meloader.input.DeviceControls.connected(this)) {
			android.view.WindowManager.LayoutParams attributes = getWindow().getAttributes();
			attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			getWindow().setAttributes(attributes);
			androidx.core.view.WindowInsetsControllerCompat bars =
					androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
			bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int key = event.getKeyCode();
		if (key == KeyEvent.KEYCODE_BUTTON_L2 || key == KeyEvent.KEYCODE_BUTTON_R2) {
			int step = libraryTriggers.key(event.getDeviceId(), key == KeyEvent.KEYCODE_BUTTON_R2,
					event.getAction() == KeyEvent.ACTION_DOWN);
			stepLibraryPlatform(step);
			return true;
		}
		if (event.getRepeatCount() > 0 && (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1
				|| key == KeyEvent.KEYCODE_BUTTON_X || key == KeyEvent.KEYCODE_BUTTON_Y || key == KeyEvent.KEYCODE_BUTTON_START)) return true;
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			androidx.fragment.app.Fragment fragment = getSupportFragmentManager()
					.findFragmentById(R.id.container);
			if (fragment instanceof AppsListFragment
					&& ((AppsListFragment) fragment).handleControllerKey(event.getKeyCode())) {
				return true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	private void stepLibraryPlatform(int step) {
		androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
		if (step != 0 && fragment instanceof AppsListFragment && fragment.getView() != null)
			((AppsListFragment) fragment).stepPlatform(step);
	}
	@Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
		if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK
				&& event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
			float left = Math.max(event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER), event.getAxisValue(android.view.MotionEvent.AXIS_BRAKE));
			float right = Math.max(event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER), event.getAxisValue(android.view.MotionEvent.AXIS_GAS));
			stepLibraryPlatform(libraryTriggers.axis(event.getDeviceId(), false, left));
			stepLibraryPlatform(libraryTriggers.axis(event.getDeviceId(), true, right));
		}
		return super.dispatchGenericMotionEvent(event);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		Uri uri = intent.getData();
		if (uri != null) {
			ru.playsoftware.j2meloader.catalog.AdditionalGames.install(this, getSupportFragmentManager(), uri, true, true);
		}
	}
}
