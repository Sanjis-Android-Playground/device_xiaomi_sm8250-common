/*
 * Copyright (C) 2023-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public class SunlightController {
    private static final String TAG = "SunlightController";
    private static final boolean DEBUG = true;

    public static final String KEY_SUNLIGHT_MODE = "sunlight_mode";
    private static final int LUX_TRIGGER = 12000;
    private static final int LUX_EXIT = 5000;
    private static final long EXIT_DEBOUNCE_MS = 3000;

    private final Context mContext;
    private final Handler mHandler;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;

    private boolean mLightSensorRegistered = false;
    private boolean mInSunlightMode = false;
    private boolean mScreenOn = true;
    private boolean mIsDozing = false;
    private float mLastLux = -1.0f;

    public static boolean isSupported() {
        return SystemProperties.getBoolean("ro.vendor.display.sunlight.enable", false);
    }

    private final Runnable mExitSunlightRunnable = () -> {
        if (mInSunlightMode) {
            dlog("Exit debounce expired, turning off sunlight mode");
            mInSunlightMode = false;
            DfWrapper.setDisplayFeature(DfWrapper.FEATURE_SUNLIGHT_SCREEN, 0, 0);
        }
    };

    private final SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final float lux = event.values[0];
            mLastLux = lux;
            if (lux >= LUX_TRIGGER) {
                mHandler.removeCallbacks(mExitSunlightRunnable);
                if (!mInSunlightMode) {
                    dlog("Trigger sunlight mode, lux=" + lux);
                    mInSunlightMode = true;
                    DfWrapper.setDisplayFeature(DfWrapper.FEATURE_SUNLIGHT_SCREEN, 23, 0);
                }
            } else if (lux < LUX_EXIT) {
                if (mInSunlightMode && !mHandler.hasCallbacks(mExitSunlightRunnable)) {
                    dlog("Lux dropped below exit threshold (" + lux + "), scheduling exit");
                    mHandler.postDelayed(mExitSunlightRunnable, EXIT_DEBOUNCE_MS);
                }
            } else {
                if (mInSunlightMode) {
                    mHandler.removeCallbacks(mExitSunlightRunnable);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final ContentObserver mSettingObserver;

    public SunlightController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        if (!isSupported()) {
            mSensorManager = null;
            mLightSensor = null;
            mSettingObserver = null;
            return;
        }

        mSensorManager = mContext.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager != null ? mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;

        mSettingObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, android.net.Uri uri) {
                dlog("SettingObserver: onChange: " + uri);
                update();
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(KEY_SUNLIGHT_MODE),
                false, mSettingObserver, UserHandle.USER_CURRENT);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                false, mSettingObserver, UserHandle.USER_CURRENT);
    }

    public void onScreenStateChanged(boolean screenOn, boolean isDozing) {
        mScreenOn = screenOn;
        mIsDozing = isDozing;
        update();
    }

    public void update() {
        if (!isSupported()) {
            return;
        }
        final boolean sunlightEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                KEY_SUNLIGHT_MODE, 0, UserHandle.USER_CURRENT) != 0;
        final boolean autoBrightness = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, 0, UserHandle.USER_CURRENT)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;

        final boolean shouldMonitor = mScreenOn && !mIsDozing && sunlightEnabled && !autoBrightness;
        dlog("update: shouldMonitor=" + shouldMonitor
                + ", mLightSensorRegistered=" + mLightSensorRegistered);

        if (shouldMonitor) {
            if (!mLightSensorRegistered && mSensorManager != null && mLightSensor != null) {
                mSensorManager.registerListener(mLightSensorListener, mLightSensor,
                        SensorManager.SENSOR_DELAY_NORMAL, mHandler);
                mLightSensorRegistered = true;
            }
        } else {
            mHandler.removeCallbacks(mExitSunlightRunnable);
            if (mLightSensorRegistered && mSensorManager != null) {
                mSensorManager.unregisterListener(mLightSensorListener);
                mLightSensorRegistered = false;
            }
            if (mInSunlightMode) {
                dlog("Stopping sunlight mode as monitoring condition is false");
                mInSunlightMode = false;
                DfWrapper.setDisplayFeature(DfWrapper.FEATURE_SUNLIGHT_SCREEN, 0, 0);
            }
        }
    }

    public float getLastLux() {
        return mLastLux;
    }

    public void onDestroy() {
        mHandler.removeCallbacks(mExitSunlightRunnable);
        if (mLightSensorRegistered && mSensorManager != null) {
            mSensorManager.unregisterListener(mLightSensorListener);
            mLightSensorRegistered = false;
        }
        if (mInSunlightMode) {
            mInSunlightMode = false;
            DfWrapper.setDisplayFeature(DfWrapper.FEATURE_SUNLIGHT_SCREEN, 0, 0);
        }
        if (mSettingObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
