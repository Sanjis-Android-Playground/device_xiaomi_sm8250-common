/*
* Copyright (C) 2016 The OmniROM Project
* Copyright (C) 2018-2021 crDroid Android Project
* Copyright (C) 2019-2022 Evolution X Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;
import org.lineageos.settings.display.DisplayNodes;
import org.lineageos.settings.display.DisplayUtils;

public class HBMFragment extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener {

    private SwitchPreferenceCompat mHBMPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.hbm_settings);

        mHBMPreference = (SwitchPreferenceCompat) findPreference(HBMManager.PREF_HBM);
        if (mHBMPreference != null) {
            mHBMPreference.setOnPreferenceChangeListener(this);
        }

        SwitchPreferenceCompat autoHbm = (SwitchPreferenceCompat) findPreference(HBMManager.PREF_AUTO_HBM);
        if (autoHbm != null) {
            autoHbm.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = requireContext();

        if (HBMManager.PREF_HBM.equals(preference.getKey())) {
            final boolean enabled = (Boolean) newValue;
            if (enabled && HBMManager.isDcDimmingEnabled(context)) {
                getPrefs().edit().putBoolean(DisplayNodes.getDcDimmingEnableKey(), false).apply();
                DisplayUtils.setDcDimming(false);
            }
            final boolean success = HBMManager.setEnabled(context, HBMManager.Owner.MANUAL, enabled);
            if (success) {
                HBMModeTileService.requestUpdate(context);
            }
            return success;
        }

        if (HBMManager.PREF_AUTO_HBM.equals(preference.getKey())) {
            final boolean enabled = (Boolean) newValue;
            getPrefs().edit().putBoolean(HBMManager.PREF_AUTO_HBM, enabled).apply();
            final Intent service = new Intent(context, AutoHBMService.class);
            if (enabled) {
                context.startServiceAsUser(service, UserHandle.CURRENT);
            } else {
                context.stopServiceAsUser(service, UserHandle.CURRENT);
            }
            return true;
        }

        return false;
    }

    private SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext());
    }
}
