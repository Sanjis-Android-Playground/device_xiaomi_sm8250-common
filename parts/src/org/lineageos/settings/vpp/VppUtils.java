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

import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.Set;

public final class VppUtils {
    private static final String TAG = "VppUtils";
    private static final boolean DEBUG = false;

    public static final String PREF_S2H_ENABLE = "screen_enhance_engine_s2h_enable";
    public static final String PREF_MEMC_ENABLE = "screen_enhance_engine_memc_enable";
    public static final String PREF_VIDEO_APPS = "vpp_video_apps";

    public static final String PROP_VIDEO_VPP = "debug.media.video.vpp";
    public static final String PROP_VIDEO_FRC = "debug.media.video.frc";
    public static final String PROP_VPP_ENABLE = "debug.media.vpp.enable";

    private VppUtils() {
        // Utility class
    }

    public static boolean isS2hEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_S2H_ENABLE, false);
    }

    public static boolean isMemcEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_MEMC_ENABLE, false);
    }

    public static Set<String> getVideoApps(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_VIDEO_APPS, Collections.emptySet());
    }

    public static void startService(Context context) {
        try {
            context.startServiceAsUser(new Intent(context, VppService.class), UserHandle.CURRENT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start VppService", e);
        }
    }

    public static void applyVppProps(boolean s2hEnabled, boolean memcEnabled) {
        boolean vppEnabled = s2hEnabled || memcEnabled;
        try {
            SystemProperties.set(PROP_VIDEO_VPP, Boolean.toString(s2hEnabled));
            SystemProperties.set(PROP_VIDEO_FRC, Boolean.toString(memcEnabled));
            SystemProperties.set(PROP_VPP_ENABLE, Boolean.toString(vppEnabled));
            if (DEBUG) {
                Log.d(TAG, "applyVppProps: vpp=" + s2hEnabled + ", frc=" + memcEnabled
                        + ", enable=" + vppEnabled);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set VPP properties", e);
        }
    }

    public static void handleForegroundAppChange(Context context, String packageName) {
        if (context == null || packageName == null) {
            return;
        }
        if (getVideoApps(context).contains(packageName)) {
            applyVppProps(isS2hEnabled(context), isMemcEnabled(context));
        } else {
            applyVppProps(false, false);
        }
    }

    public static void updateState(Context context) {
        try {
            ActivityTaskManager.RootTaskInfo focusedRootTaskInfo =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            if (focusedRootTaskInfo != null && focusedRootTaskInfo.topActivity != null) {
                handleForegroundAppChange(context, focusedRootTaskInfo.topActivity.getPackageName());
            } else {
                applyVppProps(false, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update VPP state from focused task info", e);
        }
    }
}
