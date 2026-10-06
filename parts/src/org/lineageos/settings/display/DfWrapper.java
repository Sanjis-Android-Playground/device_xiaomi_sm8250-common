/*
 * Copyright (C) 2023 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

import android.os.IHwBinder.DeathRecipient;
import android.util.Log;

import vendor.xiaomi.hardware.displayfeature.V1_0.IDisplayFeature;

public class DfWrapper {

    private static final String TAG = "DisplayFeatureWrapper";
    private static IDisplayFeature mDisplayFeature;

    private static DeathRecipient mDeathRecipient = (cookie) -> {
        Log.d(TAG, "serviceDied");
        mDisplayFeature = null;
    };

    public static IDisplayFeature getDisplayFeature() {
        if (mDisplayFeature == null) {
            Log.d(TAG, "getDisplayFeature: mDisplayFeature=null");
            try {
                mDisplayFeature = IDisplayFeature.getService();
                mDisplayFeature.asBinder().linkToDeath(mDeathRecipient, 0);
            } catch (Exception e) {
                Log.e(TAG, "getDisplayFeature failed!", e);
            }
        }
        return mDisplayFeature;
    }

    public static final int FEATURE_ACNORMAL = 0;
    public static final int FEATURE_INCREASED_CONTRAST = 1;
    public static final int FEATURE_STANDARD = 2;
    public static final int FEATURE_EXPERT = 26;
    public static final int FEATURE_TRUE_TONE = 32;

    public static final int EXPERT_GAMUT = 0;
    public static final int EXPERT_R = 1;
    public static final int EXPERT_G = 2;
    public static final int EXPERT_B = 3;
    public static final int EXPERT_HUE = 4;
    public static final int EXPERT_SATURATION = 5;
    public static final int EXPERT_VALUE = 6;
    public static final int EXPERT_CONTRAST = 7;
    public static final int EXPERT_GAMMA = 8;
    public static final int EXPERT_CLEAR = 9;
    public static final int EXPERT_RESTORE = 10;
    public static final int EXPERT_DISABLE = 11;

    public static boolean setDisplayFeature(int mode, int value, int cookie) {
        return setDisplayFeature(new DfParams(mode, value, cookie));
    }

    public static boolean setDisplayFeature(DfParams params) {
        final IDisplayFeature displayFeature = getDisplayFeature();
        if (displayFeature == null) {
            Log.e(TAG, "setDisplayFeatureParams: displayFeature is null!");
            return false;
        }
        Log.d(TAG, "setDisplayFeatureParams: " + params);
        try {
            displayFeature.setFeature(0, params.mode, params.value, params.cookie);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "setDisplayFeatureParams failed!", e);
            return false;
        }
    }

    public static void sendMessage(int index, int value) {
        sendMessage(index, value, "");
    }

    public static void sendMessage(int index, int value, String cmd) {
        final IDisplayFeature displayFeature = getDisplayFeature();
        if (displayFeature == null) {
            Log.e(TAG, "sendMessage: displayFeature is null!");
            return;
        }
        Log.d(TAG, "sendMessage: index=" + index + ", value=" + value + ", cmd=" + cmd);
        try {
            displayFeature.sendMessage(index, value, cmd != null ? cmd : "");
        } catch (Exception e) {
            Log.e(TAG, "sendMessage failed! index=" + index + ", value=" + value, e);
        }
    }

    public static class DfParams {
        /* displayfeature parameters */
        final int mode, value, cookie;

        public DfParams(int mode, int value, int cookie) {
            this.mode = mode;
            this.value = value;
            this.cookie = cookie;
        }

        public String toString() {
            return "DisplayFeatureParams(" + mode + ", " + value + ", " + cookie + ")";
        }
    }
}
