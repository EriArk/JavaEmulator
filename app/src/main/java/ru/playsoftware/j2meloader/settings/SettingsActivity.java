/*
 * Copyright 2017 Nikita Shakarun
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

package ru.playsoftware.j2meloader.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;

import ru.playsoftware.j2meloader.R;

public class SettingsActivity extends CompactSettingsActivity implements
        androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) getSupportFragmentManager().beginTransaction()
				.replace(R.id.settings_content, new SettingsFragment()).commit();
	}

	@Override public boolean onPreferenceStartScreen(androidx.preference.PreferenceFragmentCompat caller,
			androidx.preference.PreferenceScreen screen) {
		Bundle args = new Bundle(); args.putString(androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, screen.getKey());
		SettingsFragment fragment = new SettingsFragment(); fragment.setArguments(args);
		getSupportFragmentManager().beginTransaction().replace(R.id.settings_content, fragment)
				.addToBackStack(screen.getKey()).commit();
		return true;
	}

}
