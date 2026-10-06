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
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.settings.R;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

public class DisplaySettingsFragment extends SettingsBasePreferenceFragment implements
        OnPreferenceChangeListener {

    private SwitchPreferenceCompat mDcDimmingPreference;
    private String DC_DIMMING_ENABLE_KEY;

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
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DC_DIMMING_ENABLE_KEY.equals(preference.getKey())) {
            return DisplayUtils.setDcDimming((Boolean) newValue);
        }
        return true;
    }
}
