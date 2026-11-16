/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.settings.charging;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class ChargingModeFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private ListPreference mChargingMode;
    private ChargingModeUtils mChargingModeUtils;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.charging_modes);

        mChargingModeUtils = new ChargingModeUtils(requireContext());
        mChargingMode = findPreference(ChargingModeUtils.KEY_PROFILE);

        if (mChargingMode == null) {
            return;
        }

        if (!ChargingModeUtils.isSupported()) {
            mChargingMode.setEnabled(false);
            mChargingMode.setSummary(R.string.charging_mode_unsupported);
            return;
        }

        mChargingMode.setValue(mChargingModeUtils.getProfile());
        mChargingMode.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!ChargingModeUtils.KEY_PROFILE.equals(preference.getKey())) {
            return false;
        }

        final boolean applied = mChargingModeUtils.setProfile((String) newValue);
        if (!applied) {
            Toast.makeText(requireContext(), R.string.charging_mode_error,
                    Toast.LENGTH_SHORT).show();
        }
        return applied;
    }
}
