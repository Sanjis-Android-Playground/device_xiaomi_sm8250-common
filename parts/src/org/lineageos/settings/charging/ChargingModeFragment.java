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
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class ChargingModeFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private SwitchPreferenceCompat mChargingLimiter;
    private ListPreference mChargingProfile;
    private ChargingModeUtils mChargingModeUtils;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.charging_modes);

        mChargingModeUtils = new ChargingModeUtils(requireContext());
        mChargingLimiter = findPreference(ChargingModeUtils.KEY_ENABLED);
        mChargingProfile = findPreference(ChargingModeUtils.KEY_PROFILE);

        if (mChargingLimiter == null || mChargingProfile == null) {
            return;
        }

        if (!ChargingModeUtils.isSupported()) {
            mChargingLimiter.setEnabled(false);
            mChargingProfile.setEnabled(false);
            mChargingLimiter.setSummary(R.string.charging_mode_unsupported);
            return;
        }

        mChargingLimiter.setChecked(mChargingModeUtils.isEnabled());
        mChargingProfile.setValue(mChargingModeUtils.getProfile());

        mChargingLimiter.setOnPreferenceChangeListener(this);
        mChargingProfile.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        final boolean applied;
        if (ChargingModeUtils.KEY_ENABLED.equals(key)) {
            applied = mChargingModeUtils.setEnabled((Boolean) newValue);
        } else if (ChargingModeUtils.KEY_PROFILE.equals(key)) {
            applied = mChargingModeUtils.setProfile((String) newValue);
        } else {
            return false;
        }

        if (!applied) {
            Toast.makeText(requireContext(), R.string.charging_mode_error,
                    Toast.LENGTH_SHORT).show();
        }
        return applied;
    }
}
