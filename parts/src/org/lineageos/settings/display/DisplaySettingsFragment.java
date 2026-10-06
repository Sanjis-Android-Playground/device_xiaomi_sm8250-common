/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.display;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.settings.R;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

public class DisplaySettingsFragment extends SettingsBasePreferenceFragment implements
        OnPreferenceChangeListener {

    public static final String KEY_TRUE_TONE = "screen_true_color";
    public static final String KEY_SUNLIGHT_MODE = "sunlight_mode";

    private SwitchPreferenceCompat mDcDimmingPreference;
    private String DC_DIMMING_ENABLE_KEY;
    private SwitchPreferenceCompat mTrueTonePreference;
    private SwitchPreferenceCompat mSunlightModePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        DC_DIMMING_ENABLE_KEY = DisplayNodes.getDcDimmingEnableKey();

        addPreferencesFromResource(R.xml.display_settings);
        mDcDimmingPreference = (SwitchPreferenceCompat) findPreference(DC_DIMMING_ENABLE_KEY);
        if (DisplayUtils.isDcSupported()) {
            mDcDimmingPreference.setOnPreferenceChangeListener(this);
        } else {
            getPreferenceScreen().removePreference(mDcDimmingPreference);
        }

        mTrueTonePreference = (SwitchPreferenceCompat) findPreference(KEY_TRUE_TONE);
        if (mTrueTonePreference != null) {
            if (TrueToneController.isSupported(getContext())) {
                mTrueTonePreference.setChecked(Settings.System.getIntForUser(
                        requireContext().getContentResolver(), KEY_TRUE_TONE, 0, UserHandle.USER_CURRENT) != 0);
                mTrueTonePreference.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(mTrueTonePreference);
            }
        }

        mSunlightModePreference = (SwitchPreferenceCompat) findPreference(KEY_SUNLIGHT_MODE);
        if (mSunlightModePreference != null) {
            if (SunlightController.isSupported()) {
                final boolean autoBrightness = Settings.System.getIntForUser(
                        requireContext().getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE, 0, UserHandle.USER_CURRENT)
                        == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
                mSunlightModePreference.setChecked(Settings.System.getIntForUser(
                        requireContext().getContentResolver(), KEY_SUNLIGHT_MODE, 0, UserHandle.USER_CURRENT) != 0);
                mSunlightModePreference.setEnabled(!autoBrightness);
                mSunlightModePreference.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(mSunlightModePreference);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DC_DIMMING_ENABLE_KEY.equals(preference.getKey())) {
            return DisplayUtils.setDcDimming((Boolean) newValue);
        }
        if (KEY_TRUE_TONE.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            Settings.System.putIntForUser(requireContext().getContentResolver(),
                    KEY_TRUE_TONE, enabled ? 1 : 0, UserHandle.USER_CURRENT);
            DfWrapper.setDisplayFeature(DfWrapper.FEATURE_TRUE_TONE, enabled ? 1 : 0, 0);
            return true;
        }
        if (KEY_SUNLIGHT_MODE.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            Settings.System.putIntForUser(requireContext().getContentResolver(),
                    KEY_SUNLIGHT_MODE, enabled ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        }
        return true;
    }
}
