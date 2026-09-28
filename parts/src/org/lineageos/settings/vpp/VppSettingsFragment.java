/*
 * Copyright (C) 2024-2026 The LineageOS Project
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

package org.lineageos.settings.vpp;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;
import org.lineageos.settings.vpp.preferences.IconAppListPreference;
import org.lineageos.settings.vpp.preferences.IconAppListPreferenceDialogFragment;

import java.util.Set;

public class VppSettingsFragment extends SettingsBasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG_DIALOG = "androidx.preference.PreferenceFragmentCompat.DIALOG";

    private SwitchPreferenceCompat mS2hPreference;
    private SwitchPreferenceCompat mMemcPreference;
    private IconAppListPreference mVideoAppsPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.screen_enhance_engine_settings, rootKey);

        mS2hPreference = findPreference(VppUtils.PREF_S2H_ENABLE);
        mMemcPreference = findPreference(VppUtils.PREF_MEMC_ENABLE);
        mVideoAppsPreference = findPreference(VppUtils.PREF_VIDEO_APPS);

        if (mVideoAppsPreference != null) {
            mVideoAppsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Set<?>) {
                    int size = ((Set<?>) newValue).size();
                    updateAppListSummary(size);
                }
                VppUtils.updateState(getContext());
                return true;
            });
        }

        updateAppListSummary();
        VppUtils.startService(requireContext());
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof IconAppListPreference) {
            IconAppListPreferenceDialogFragment dialogFragment =
                    IconAppListPreferenceDialogFragment.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), TAG_DIALOG);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateAppListSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (VppUtils.PREF_VIDEO_APPS.equals(key)) {
            updateAppListSummary();
        }
        VppUtils.updateState(getContext());
    }

    private void updateAppListSummary() {
        if (mVideoAppsPreference == null || getContext() == null) {
            return;
        }
        Set<String> values = mVideoAppsPreference.getValues();
        int size = values != null ? values.size() : 0;
        updateAppListSummary(size);
    }

    private void updateAppListSummary(int size) {
        if (mVideoAppsPreference == null || getContext() == null) {
            return;
        }
        if (size == 0) {
            mVideoAppsPreference.setSummary(R.string.vpp_select_apps_summary_none);
        } else {
            mVideoAppsPreference.setSummary(getString(R.string.vpp_select_apps_summary_count, size));
        }
    }
}
