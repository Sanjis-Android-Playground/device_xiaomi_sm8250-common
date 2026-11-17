/*
 * Copyright (C) 2024-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DisplayNodes;
import org.lineageos.settings.utils.FileUtils;

public final class HBMManager {
    private static final String TAG = "HBMManager";

    public static final String PREF_HBM = DisplayNodes.getHbmEnableKey();
    public static final String PREF_AUTO_HBM = "auto_hbm";
    private static final String PREF_SAVED_MODE = "hbm_saved_brightness_mode";
    private static final String PREF_SAVED_BRIGHTNESS = "hbm_saved_brightness";

    private static final String HBM_ENABLE_CMD = "0x10000";
    private static final String HBM_DISABLE_CMD = "0xF0000";
    private static final int MAX_BRIGHTNESS = 255;

    public enum Owner {
        NONE,
        MANUAL,
        AUTO
    }

    private static Owner sOwner = Owner.NONE;

    private HBMManager() {
    }

    public static synchronized boolean isEnabled() {
        return sOwner != Owner.NONE;
    }

    public static synchronized Owner getOwner() {
        return sOwner;
    }

    public static synchronized boolean setEnabled(Context context, Owner owner, boolean enable) {
        return enable ? enable(context, owner) : disable(context, owner);
    }

    private static boolean enable(Context context, Owner owner) {
        if (owner == Owner.NONE) {
            return false;
        }
        if (isDcDimmingEnabled(context)) {
            Log.w(TAG, "refusing to enable HBM with DC dimming on");
            return false;
        }
        if (sOwner == Owner.MANUAL && owner == Owner.AUTO) {
            return false;
        }
        if (sOwner == owner) {
            return true;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (sOwner == Owner.NONE) {
            prefs.edit()
                    .putInt(PREF_SAVED_MODE, Settings.System.getInt(context.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
                    .putInt(PREF_SAVED_BRIGHTNESS, Settings.System.getInt(
                            context.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS))
                    .apply();
        }

        if (!writeHbm(HBM_ENABLE_CMD)) {
            return false;
        }

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS);

        sOwner = owner;
        prefs.edit().putBoolean(PREF_HBM, true).apply();
        return true;
    }

    private static boolean disable(Context context, Owner owner) {
        if (sOwner == Owner.NONE) {
            return true;
        }
        if (owner == Owner.AUTO && sOwner == Owner.MANUAL) {
            return false;
        }

        if (!writeHbm(HBM_DISABLE_CMD)) {
            return false;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int mode = prefs.getInt(PREF_SAVED_MODE, -1);
        if (mode != -1) {
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, mode);
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS,
                        prefs.getInt(PREF_SAVED_BRIGHTNESS, MAX_BRIGHTNESS));
            }
        }
        prefs.edit()
                .remove(PREF_SAVED_MODE)
                .remove(PREF_SAVED_BRIGHTNESS)
                .putBoolean(PREF_HBM, false)
                .apply();

        sOwner = Owner.NONE;
        return true;
    }

    /** Reconcile the stored preference with the hardware on boot. */
    public static synchronized void initializeOnBoot(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_HBM, false)) {
            if (!enable(context, Owner.MANUAL)) {
                prefs.edit().putBoolean(PREF_HBM, false).apply();
            }
        } else {
            writeHbm(HBM_DISABLE_CMD);
            prefs.edit().remove(PREF_SAVED_MODE).remove(PREF_SAVED_BRIGHTNESS).apply();
        }
    }

    public static boolean isDcDimmingEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DisplayNodes.getDcDimmingEnableKey(), false);
    }

    private static boolean writeHbm(String value) {
        final String node = DisplayNodes.getHbmNode();
        if (!FileUtils.fileExists(node)) {
            Log.e(TAG, "HBM node missing: " + node);
            return false;
        }
        return FileUtils.writeLine(node, value);
    }
}
