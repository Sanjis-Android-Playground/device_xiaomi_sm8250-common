/*
 * Copyright (C) 2023-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public class TrueToneController {
    private static final String TAG = "TrueToneController";
    private static final boolean DEBUG = true;

    public static final String KEY_TRUE_TONE = "screen_true_color";

    private final Context mContext;
    private final Handler mHandler;
    private final ContentObserver mSettingObserver;

    public static boolean isSupported(Context context) {
        if (!SystemProperties.getBoolean("ro.vendor.display.truetone.enable", false)) {
            return false;
        }
        if (context == null) return false;
        SensorManager sensorManager = context.getSystemService(SensorManager.class);
        if (sensorManager == null) return false;
        for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            String stringType = sensor.getStringType();
            String name = sensor.getName() != null ? sensor.getName().toLowerCase() : "";
            if ("xiaomi.sensor.front_cct".equals(stringType)
                    || "xiaomi.sensor.front_cct_strm".equals(stringType)
                    || name.contains("front_cct")
                    || name.contains("front cct")
                    || sensor.getType() == 33171088
                    || sensor.getType() == 33171018) {
                return true;
            }
        }
        return false;
    }

    public TrueToneController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        if (!isSupported(mContext)) {
            mSettingObserver = null;
            return;
        }

        mSettingObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, android.net.Uri uri) {
                dlog("SettingObserver: onChange: " + uri);
                update();
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(KEY_TRUE_TONE),
                false, mSettingObserver, UserHandle.USER_CURRENT);

        update();
    }

    public void update() {
        if (!isSupported(mContext)) {
            return;
        }
        final boolean trueToneEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                KEY_TRUE_TONE, 0, UserHandle.USER_CURRENT) != 0;
        dlog("update: enabled=" + trueToneEnabled);
        DfWrapper.setDisplayFeature(DfWrapper.FEATURE_TRUE_TONE, trueToneEnabled ? 1 : 0, 0);
    }

    public void onDestroy() {
        if (mSettingObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
