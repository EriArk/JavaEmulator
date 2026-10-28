/*
 *  Copyright 2020 Yury Kharchenko
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

package ru.playsoftware.j2meloader.config;

import android.util.SparseIntArray;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.ArrayList;

import javax.microedition.lcdui.keyboard.KeyMapper;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.util.SparseIntArrayAdapter;

public class ProfileModel {
	public static final int VERSION = 4;
	/** True if this is a new profile (not yet saved to file) */
	public final transient boolean isNew;

	public transient File dir;
	transient ProfileModel previewBaseline;

	@SerializedName("Version")
	public int version;

	@SerializedName("ScreenWidth")
	public int screenWidth;

	@SerializedName("ScreenHeight")
	public int screenHeight;

	@SerializedName("ScreenBackgroundColor")
	public int screenBackgroundColor;

	@SerializedName("ScreenScaleRatio")
	public int screenScaleRatio;

	@SerializedName("Orientation")
	public int orientation;

	@SerializedName("ScreenRotation")
	public int screenRotation;

	@SerializedName("ScreenScaleToFit")
	public boolean screenScaleToFit;

	@SerializedName("ScreenKeepAspectRatio")
	public boolean screenKeepAspectRatio;

	@SerializedName("ScreenScaleType")
	public int screenScaleType;

	@SerializedName("ScreenGravity")
	public int screenGravity;

	@SerializedName("ScreenFilter")
	public boolean screenFilter;
	@SerializedName("RetroShader")
	public int retroShader;
	public int getRetroShader() { return retroShader >= 1 && retroShader <= 2 ? retroShader : 0; }
	@SerializedName("FsrUpscaling")
	public boolean fsrUpscaling;
	@SerializedName("FsrSharpness")
	public int fsrSharpness = 50;
	@SerializedName("FrameInterpolation")
	public boolean frameInterpolation;
	@SerializedName("MophunCpuBudget")
	public int mophunCpuBudget = 100;
	@SerializedName("MophunCaptureEnhanced")
	public boolean mophunCaptureEnhanced = true;

	@SerializedName("ScreenEffect")
	public int screenEffect;
	@SerializedName("ScreenEffectIntensity")
	public int screenEffectIntensity = 70;
	@SerializedName("ScreenshotWithEffect")
	public boolean screenshotWithEffect;

	@SerializedName("ImmediateMode")
	public boolean immediateMode;

	@SerializedName("HwAcceleration")
	public boolean hwAcceleration;

	@SerializedName("GraphicsMode")
	public int graphicsMode;

	@SerializedName("Shader")
	public ShaderInfo shader;

	@SerializedName("ParallelRedrawScreen")
	public boolean parallelRedrawScreen;

	@SerializedName("ShowFps")
	public boolean showFps;

	@SerializedName("FpsLimit")
	public int fpsLimit;
	// Null preserves the native engines' historical default; zero explicitly means unlimited.
	@SerializedName("NativeFpsLimit")
	public Integer nativeFpsLimit;
	@SerializedName("EmulationSpeed")
	public int emulationSpeed = 100;
	@SerializedName("FastForwardSpeed")
	public int fastForwardSpeed = 200;
	@SerializedName("FastForwardButton")
	public int fastForwardButton;
	@SerializedName("FastForwardToggle")
	public boolean fastForwardToggle;
	@SerializedName("FastForwardOnScreen")
	public boolean fastForwardOnScreen = true;

	public int getEmulationSpeed() {
		return ru.playsoftware.j2meloader.runtime.EmulationClock.normalize(emulationSpeed);
	}

	public int getNativeFpsLimit() {
		return nativeFpsLimit == null ? 60 : Math.max(0, Math.min(240, nativeFpsLimit));
	}

	@SerializedName("ForceFullscreen")
	public boolean forceFullscreen;

	@SerializedName("FontSizeSmall")
	public int fontSizeSmall;

	@SerializedName("FontSizeMedium")
	public int fontSizeMedium;

	@SerializedName("FontSizeLarge")
	public int fontSizeLarge;

	@SerializedName("FontApplyDimensions")
	public boolean fontApplyDimensions;

	@SerializedName("FontAntiAlias")
	public boolean fontAA;

	@SerializedName("TouchInput")
	public boolean touchInput;

	@SerializedName("ShowKeyboard")
	public boolean showKeyboard;

	@SerializedName("VirtualKeyboardType")
	public int vkType;

	@SerializedName("touchLayout")
	public Integer touchLayout;
	@SerializedName("touchKeypadOverlay")
	public boolean touchKeypadOverlay;
	@SerializedName("touchSize")
	public int touchSize;
	@SerializedName("touchTheme")
	public int touchTheme;
	@SerializedName("touchOpacity")
	public int touchOpacity = 100;
	@SerializedName("gameHandleVisible")
	public boolean gameHandleVisible = true;
	@SerializedName("gameShortcutOpacity")
	public int gameShortcutOpacity = 100;
	@SerializedName("gameHandlePortrait")
	public ru.playsoftware.j2meloader.input.TouchPlacement gameHandlePortrait;
	@SerializedName("gameHandleLandscape")
	public ru.playsoftware.j2meloader.input.TouchPlacement gameHandleLandscape;
	@SerializedName("touchPortraitReach")
	public float touchPortraitReach;
	@SerializedName("touchLandscapeReach")
	public float touchLandscapeReach;
	@SerializedName("touchPlacements")
	public java.util.HashMap<String, ru.playsoftware.j2meloader.input.TouchPlacement> touchPlacements;

	@SerializedName("ButtonShape")
	public int vkButtonShape;

	@SerializedName("VirtualKeyboardAlpha")
	public int vkAlpha;

	@SerializedName("VirtualKeyboardForceOpacity")
	public boolean vkForceOpacity;

	@SerializedName("VirtualKeyboardFeedback")
	public boolean vkFeedback;

	@SerializedName("VirtualKeyboardDelay")
	public int vkHideDelay;

	@SerializedName("VirtualKeyboardColorBackground")
	public int vkBgColor;

	@SerializedName("VirtualKeyboardColorBackgroundSelected")
	public int vkBgColorSelected;

	@SerializedName("VirtualKeyboardColorForeground")
	public int vkFgColor;

	@SerializedName("VirtualKeyboardColorForegroundSelected")
	public int vkFgColorSelected;

	@SerializedName("VirtualKeyboardColorOutline")
	public int vkOutlineColor;

	@SerializedName("Layout")
	public int keyCodesLayout;

	@JsonAdapter(SparseIntArrayAdapter.class)
	@SerializedName("KeyCodeMap")
	public SparseIntArray keyCodeMap;

	@JsonAdapter(SparseIntArrayAdapter.class)
	@SerializedName("KeyMappings")
	public SparseIntArray keyMappings;

	@SerializedName("ActiveKeyMappingProfile")
	public int activeKeyMappingProfile;

	@SerializedName("KeyMappingProfiles")
	public ArrayList<KeyMappingProfile> keyMappingProfiles;

	@SerializedName("SystemProperties")
	public String systemProperties;

	@SerializedName("CompatibilityTested")
	public boolean compatibilityTested;

	@SerializedName("CompatibilityProfile")
	public String compatibilityProfile;

	@SerializedName("CompatibilityScore")
	public int compatibilityScore;
	@SerializedName("compatibilityConfidence")
	public String compatibilityConfidence;
	@SerializedName("compatibilityReasons")
	public String compatibilityReasons;
	@SerializedName("suggestedCompatibilityProfile")
	public String suggestedCompatibilityProfile;
	@SerializedName("detectedScreenWidth")
	public int detectedScreenWidth;
	@SerializedName("detectedScreenHeight")
	public int detectedScreenHeight;

	public static class KeyMappingProfile {
		@SerializedName("Name")
		public String name;

		@JsonAdapter(SparseIntArrayAdapter.class)
		@SerializedName("Mappings")
		public SparseIntArray mappings;

		@SuppressWarnings("unused")
		public KeyMappingProfile() {
		}

		public KeyMappingProfile(String name, SparseIntArray mappings) {
			this.name = name;
			this.mappings = mappings;
		}
	}

	@SuppressWarnings("unused") // Gson uses default constructor if present
	public ProfileModel() {
		isNew = false;
	}

	@SerializedName("ControlMode")
	public int controlMode;
	@SerializedName("TouchVisibility")
	public Integer touchVisibility;

	public int getTouchVisibility() {
		// Old Phone profiles may deliberately hide their keypad. Old Handheld profiles
		// disabled both switches; those use automatic detection in the unified app.
		if (touchVisibility == null) return !showKeyboard && touchInput ? 2 : 0;
		return Math.max(0, Math.min(2, touchVisibility));
	}

	public ProfileModel(File dir) {
		this.dir = dir;
		isNew = true;
		version = VERSION;
		screenWidth = 240;
		screenHeight = 320;
		screenBackgroundColor = 0x101218;
		screenScaleType = 1;
		screenGravity = ru.playsoftware.j2meloader.input.DeviceControls.connected(ContextHolder.getAppContext()) ? 2 : 1;
		screenScaleRatio = 100;
		orientation = 1;
		screenScaleToFit = true;
		screenKeepAspectRatio = true;
		graphicsMode = 1;

		// Zero selects the existing resolution-based font sizes after preparation.
		fontSizeSmall = 0;
		fontSizeMedium = 0;
		fontSizeLarge = 0;
		fontAA = true;

		showKeyboard = true;
		touchVisibility = 0;
		touchLayout = 0;
		touchInput = true;

		vkButtonShape = VirtualKeyboard.ROUND_RECT_SHAPE;
		vkAlpha = 64;

		vkBgColor = 0x20242C;
		vkFgColor = 0xE6EDF3;
		vkBgColorSelected = 0xFFB62E;
		vkFgColorSelected = 0x101218;
		vkOutlineColor = 0x3E4652;
		systemProperties = ContextHolder.getAssetAsString("defaults/system.props");
		keyMappingProfiles = KeyMapper.createBuiltInProfiles();
		activeKeyMappingProfile = 0;
	}

	public void ensureKeyMappingProfiles() {
		if (keyMappingProfiles != null && keyMappingProfiles.size() > 0) {
			if (activeKeyMappingProfile < 0 || activeKeyMappingProfile >= keyMappingProfiles.size()) {
				activeKeyMappingProfile = 0;
			}
			return;
		}
		keyMappingProfiles = KeyMapper.createBuiltInProfiles();
		if (keyMappings != null) {
			keyMappingProfiles.add(new KeyMappingProfile("Custom", keyMappings.clone()));
			activeKeyMappingProfile = keyMappingProfiles.size() - 1;
		} else {
			activeKeyMappingProfile = 0;
		}
	}

	public SparseIntArray getActiveKeyMappings() {
		ensureKeyMappingProfiles();
		KeyMappingProfile profile = keyMappingProfiles.get(activeKeyMappingProfile);
		return profile.mappings;
	}

	public int ensureCustomKeyMappingProfile() {
		ensureKeyMappingProfiles();
		for (int i = 0, size = keyMappingProfiles.size(); i < size; i++) {
			KeyMappingProfile profile = keyMappingProfiles.get(i);
			if ("Custom".equals(profile.name)) {
				activeKeyMappingProfile = i;
				return i;
			}
		}
		SparseIntArray current = getActiveKeyMappings();
		SparseIntArray mappings = current == null ? KeyMapper.getDefaultKeyMap() : current.clone();
		keyMappingProfiles.add(new KeyMappingProfile("Custom", mappings));
		activeKeyMappingProfile = keyMappingProfiles.size() - 1;
		return activeKeyMappingProfile;
	}

	public void setActiveKeyMappingProfile(int index) {
		ensureKeyMappingProfiles();
		if (index >= 0 && index < keyMappingProfiles.size()) {
			activeKeyMappingProfile = index;
			keyMappings = getActiveKeyMappings();
		}
	}

	public void setActiveKeyMappings(SparseIntArray mappings) {
		ensureKeyMappingProfiles();
		keyMappingProfiles.get(activeKeyMappingProfile).mappings = mappings;
		keyMappings = mappings;
	}
}
