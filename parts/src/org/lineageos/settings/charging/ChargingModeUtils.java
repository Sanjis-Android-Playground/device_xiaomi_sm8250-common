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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

/**
 * Controls the PM8150B charging current through the qcom-battery restricted
 * charging interface.
 *
 * {@code restrict_cur} takes the fast charge current (FCC) in µA and only
 * takes effect while {@code restrict_chg} is 1. Enabling the restriction also
 * votes the parallel charger off, which is what makes the lower modes actually
 * slower than a normal charger negotiation.
 */
public final class ChargingModeUtils {
    private static final String TAG = "ChargingModeUtils";

    public static final String KEY_ENABLED = "charging_limiter";
    public static final String KEY_PROFILE = "charging_limiter_profile";

    public static final String PROFILE_SLOW = "slow";
    public static final String PROFILE_MEDIUM = "medium";
    public static final String PROFILE_FAST = "fast";

    private static final String NODE_CURRENT = "/sys/class/qcom-battery/restrict_cur";
    private static final String NODE_ENABLE = "/sys/class/qcom-battery/restrict_chg";

    /* FCC caps in µA, calibrated on a 33 W charger. */
    private static final int CURRENT_SLOW_UA = 2000000;
    private static final int CURRENT_MEDIUM_UA = 4500000;

    private final SharedPreferences mPrefs;

    public ChargingModeUtils(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isSupported() {
        return FileUtils.isFileReadable(NODE_CURRENT) && FileUtils.isFileWritable(NODE_ENABLE);
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(KEY_ENABLED, true);
    }

    public String getProfile() {
        final String profile = mPrefs.getString(KEY_PROFILE, PROFILE_MEDIUM);
        return isValidProfile(profile) ? profile : PROFILE_MEDIUM;
    }

    public boolean setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        return apply(enabled, getProfile());
    }

    public boolean setProfile(String profile) {
        if (!isValidProfile(profile)) {
            return false;
        }
        mPrefs.edit().putString(KEY_PROFILE, profile).apply();
        return apply(isEnabled(), profile);
    }

    /** Re-applies the saved state, used after boot. */
    public boolean restore() {
        return apply(isEnabled(), getProfile());
    }

    private boolean apply(boolean enabled, String profile) {
        if (!enabled || PROFILE_FAST.equals(profile)) {
            // Release the restriction, the charger negotiates its own maximum.
            return writeValue(NODE_ENABLE, 0);
        }

        final int current = PROFILE_SLOW.equals(profile)
                ? CURRENT_SLOW_UA
                : CURRENT_MEDIUM_UA;

        if (!writeValue(NODE_CURRENT, current)) {
            return false;
        }
        return writeValue(NODE_ENABLE, 1);
    }

    private static boolean writeValue(String node, int value) {
        final boolean written = FileUtils.writeLine(node, String.valueOf(value));
        if (!written) {
            Log.e(TAG, "Failed to write " + value + " to " + node);
        }
        return written;
    }

    private static boolean isValidProfile(String profile) {
        return PROFILE_SLOW.equals(profile)
                || PROFILE_MEDIUM.equals(profile)
                || PROFILE_FAST.equals(profile);
    }
}
