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

    private SwitchPreferenceCompat mDcDimmingPreference;
    private String DC_DIMMING_ENABLE_KEY;
    private SwitchPreferenceCompat mTrueTonePreference;

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
        return true;
    }
}
