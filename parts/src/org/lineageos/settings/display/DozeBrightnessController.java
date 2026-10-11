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
import android.view.Display;

public class DozeBrightnessController {
    private static final String TAG = "DozeBrightnessController";
    private static final boolean DEBUG = true;

    public static final String KEY_DOZE_BRIGHTNESS = "doze_brightness_mode";

    public static final String SENSOR_STRING_AOD = "xiaomi.sensor.aod";
    public static final int SENSOR_TYPE_AOD = 33171029;
    public static final String SENSOR_STRING_POCKET = "xiaomi.sensor.oem_pocket";
    public static final int SENSOR_TYPE_POCKET = 33171095;

    private static final float DOZE_LUX_THRESHOLD_HIGH = 25.0f;
    private static final float DOZE_LUX_THRESHOLD_LOW = 10.0f;

    private static final long DEBOUNCE_DELAY_BRIGHT_MS = 1200;
    private static final long DEBOUNCE_DELAY_DARK_MS = 2500;

    private final Context mContext;
    private final Handler mHandler;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;
    private final Sensor mAodSensor;
    private final Sensor mPocketSensor;

    private boolean mAodSensorRegistered = false;
    private boolean mPocketSensorRegistered = false;
    private boolean mDozeSensorRegistered = false;

    private boolean mIsDozing = false;
    private boolean mIsInPocket = false;
    private boolean mFirstSensorReport = false;
    private float mLastLux = -1.0f;
    private float mLastAodValue = -1.0f;
    private int mCurrentDozeMode = -1;
    private int mPendingDozeMode = -1;

    private final Runnable mApplyDozeModeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsDozing || mIsInPocket) {
                mPendingDozeMode = -1;
                return;
            }
            if (mPendingDozeMode != -1 && mCurrentDozeMode != mPendingDozeMode) {
                dlog("Apply debounced mode: " + mPendingDozeMode);
                mCurrentDozeMode = mPendingDozeMode;
                DfWrapper.setDozeBrightness(mPendingDozeMode);
            }
            mPendingDozeMode = -1;
        }
    };

    private void scheduleDozeBrightness(int targetMode) {
        if (mCurrentDozeMode == targetMode) {
            if (mPendingDozeMode != -1) {
                dlog("scheduleDozeBrightness: already in mode " + targetMode + ", cancelling pending " + mPendingDozeMode);
                mHandler.removeCallbacks(mApplyDozeModeRunnable);
                mPendingDozeMode = -1;
            }
            return;
        }

        if (mPendingDozeMode == targetMode) {
            return;
        }

        mPendingDozeMode = targetMode;
        mHandler.removeCallbacks(mApplyDozeModeRunnable);

        long delay = (targetMode == DfWrapper.DOZE_BRIGHTNESS_HBM)
                ? DEBOUNCE_DELAY_BRIGHT_MS
                : DEBOUNCE_DELAY_DARK_MS;

        dlog("scheduleDozeBrightness: schedule mode=" + targetMode + " in " + delay + "ms");
        mHandler.postDelayed(mApplyDozeModeRunnable, delay);
    }

    public static boolean isSupported() {
        return SystemProperties.getBoolean("ro.vendor.display.doze_brightness.enable", false);
    }

    private final SensorEventListener mAodSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final float val = event.values[0];
            mLastAodValue = val;
            if (!mIsDozing || mIsInPocket) return;
            int targetMode;
            if (val == 4.0f) {
                // Bright environment -> HBM (170 nits)
                targetMode = DfWrapper.DOZE_BRIGHTNESS_HBM;
            } else if (val == 5.0f || val == 3.0f) {
                // Dark environment or timeout -> LBM (10 nits)
                targetMode = DfWrapper.DOZE_BRIGHTNESS_LBM;
            } else {
                return;
            }

            if (mFirstSensorReport) {
                mFirstSensorReport = false;
                mHandler.removeCallbacks(mApplyDozeModeRunnable);
                mPendingDozeMode = -1;
                if (mCurrentDozeMode != targetMode) {
                    dlog("mAodSensorListener (initial): val=" + val + " -> mode=" + targetMode);
                    mCurrentDozeMode = targetMode;
                    DfWrapper.setDozeBrightness(targetMode);
                }
                return;
            }

            scheduleDozeBrightness(targetMode);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final SensorEventListener mPocketSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!mIsDozing) return;
            final float val = event.values[0];
            if (val == 0.0f) {
                // In pocket
                if (!mIsInPocket) {
                    dlog("mPocketSensorListener: IN POCKET detected (0.0), suppressing AOD to LBM");
                    mIsInPocket = true;
                    mHandler.removeCallbacks(mApplyDozeModeRunnable);
                    mPendingDozeMode = -1;
                    mCurrentDozeMode = DfWrapper.DOZE_BRIGHTNESS_LBM;
                    DfWrapper.setDozeBrightness(DfWrapper.DOZE_BRIGHTNESS_LBM);
                }
            } else if (val == 5.0f) {
                // Out of pocket
                if (mIsInPocket) {
                    dlog("mPocketSensorListener: OUT OF POCKET detected (5.0), resuming AOD");
                    mIsInPocket = false;
                    if (mLastAodValue == 4.0f) {
                        scheduleDozeBrightness(DfWrapper.DOZE_BRIGHTNESS_HBM);
                    } else if (mLastAodValue == 5.0f || mLastAodValue == 3.0f) {
                        scheduleDozeBrightness(DfWrapper.DOZE_BRIGHTNESS_LBM);
                    } else {
                        applyAdaptiveDozeBrightness();
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final SensorEventListener mDozeLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!mIsDozing || mIsInPocket) return;
            final float lux = event.values[0];
            mLastLux = lux;
            int targetMode;
            if (lux >= DOZE_LUX_THRESHOLD_HIGH) {
                targetMode = DfWrapper.DOZE_BRIGHTNESS_HBM;
            } else if (lux <= DOZE_LUX_THRESHOLD_LOW) {
                targetMode = DfWrapper.DOZE_BRIGHTNESS_LBM;
            } else {
                return;
            }

            if (mFirstSensorReport) {
                mFirstSensorReport = false;
                mHandler.removeCallbacks(mApplyDozeModeRunnable);
                mPendingDozeMode = -1;
                if (mCurrentDozeMode != targetMode) {
                    dlog("mDozeLightSensorListener (initial fallback): lux=" + lux + " -> mode=" + targetMode);
                    mCurrentDozeMode = targetMode;
                    DfWrapper.setDozeBrightness(targetMode);
                }
                return;
            }

            scheduleDozeBrightness(targetMode);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final ContentObserver mSettingObserver;

    public DozeBrightnessController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        if (!isSupported()) {
            mSensorManager = null;
            mLightSensor = null;
            mAodSensor = null;
            mPocketSensor = null;
            mSettingObserver = null;
            return;
        }

        mSensorManager = mContext.getSystemService(SensorManager.class);
        if (mSensorManager != null) {
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            mAodSensor = findAodSensor();
            mPocketSensor = findPocketSensor();
        } else {
            mLightSensor = null;
            mAodSensor = null;
            mPocketSensor = null;
        }

        mSettingObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, android.net.Uri uri) {
                dlog("SettingObserver: onChange: " + uri);
                if (mIsDozing) {
                    stopDozeBrightnessControl();
                    startDozeBrightnessControl();
                }
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(KEY_DOZE_BRIGHTNESS),
                false, mSettingObserver, UserHandle.USER_CURRENT);
    }

    public void onDisplayStateChanged(int state) {
        if (!isSupported()) {
            return;
        }
        switch (state) {
            case Display.STATE_DOZE:
            case Display.STATE_DOZE_SUSPEND:
                if (!mIsDozing) {
                    mIsDozing = true;
                    dlog("Entered AOD, start doze brightness control");
                    startDozeBrightnessControl();
                }
                break;
            case Display.STATE_OFF:
                if (mIsDozing) {
                    dlog("Display completely OFF, stopping AOD control");
                    mIsDozing = false;
                    stopDozeBrightnessControl();
                    DfWrapper.setDozeBrightness(DfWrapper.DOZE_TO_NORMAL);
                }
                break;
            case Display.STATE_ON:
                dlog("Exited AOD / Screen ON: restore DOZE_TO_NORMAL (0)");
                stopDozeBrightnessControl();
                DfWrapper.setDozeBrightness(DfWrapper.DOZE_TO_NORMAL);
                mIsDozing = false;
                break;
            default:
                break;
        }
    }

    public boolean isDozing() {
        return mIsDozing;
    }

    private Sensor findAodSensor() {
        if (mSensorManager == null) return null;
        Sensor s = mSensorManager.getDefaultSensor(SENSOR_TYPE_AOD, true);
        if (s != null) return s;
        s = mSensorManager.getDefaultSensor(SENSOR_TYPE_AOD, false);
        if (s != null) return s;
        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (SENSOR_STRING_AOD.equals(sensor.getStringType()) || sensor.getType() == SENSOR_TYPE_AOD) {
                return sensor;
            }
        }
        return null;
    }

    private Sensor findPocketSensor() {
        if (mSensorManager == null) return null;
        Sensor s = mSensorManager.getDefaultSensor(SENSOR_TYPE_POCKET, true);
        if (s != null) return s;
        s = mSensorManager.getDefaultSensor(SENSOR_TYPE_POCKET, false);
        if (s != null) return s;
        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (SENSOR_STRING_POCKET.equals(sensor.getStringType()) || sensor.getType() == SENSOR_TYPE_POCKET) {
                return sensor;
            }
        }
        return null;
    }

    private void applyAdaptiveDozeBrightness() {
        int targetMode = DfWrapper.DOZE_BRIGHTNESS_LBM;
        if (mLastLux >= DOZE_LUX_THRESHOLD_HIGH) {
            targetMode = DfWrapper.DOZE_BRIGHTNESS_HBM;
        }
        mCurrentDozeMode = targetMode;
        dlog("applyAdaptiveDozeBrightness: initial mode=" + targetMode + " (mLastLux=" + mLastLux + ")");
        DfWrapper.setDozeBrightness(targetMode);
    }

    private void startDozeBrightnessControl() {
        mIsInPocket = false;
        mLastAodValue = -1.0f;
        mFirstSensorReport = true;
        final int userPref = Settings.System.getIntForUser(mContext.getContentResolver(),
                KEY_DOZE_BRIGHTNESS, 0, UserHandle.USER_CURRENT);
        if (userPref == 1) {
            dlog("startDozeBrightnessControl: forced high (170 nits)");
            mCurrentDozeMode = DfWrapper.DOZE_BRIGHTNESS_HBM;
            DfWrapper.setDozeBrightness(DfWrapper.DOZE_BRIGHTNESS_HBM);
        } else if (userPref == 2) {
            dlog("startDozeBrightnessControl: forced low (10 nits)");
            mCurrentDozeMode = DfWrapper.DOZE_BRIGHTNESS_LBM;
            DfWrapper.setDozeBrightness(DfWrapper.DOZE_BRIGHTNESS_LBM);
        } else {
            dlog("startDozeBrightnessControl: adaptive mode started (mLastLux=" + mLastLux + ")");
            applyAdaptiveDozeBrightness();

            if (mSensorManager != null) {
                if (mAodSensor != null && !mAodSensorRegistered) {
                    mSensorManager.registerListener(mAodSensorListener, mAodSensor,
                            SensorManager.SENSOR_DELAY_NORMAL, mHandler);
                    mAodSensorRegistered = true;
                    dlog("startDozeBrightnessControl: registered mAodSensor");
                } else if (mLightSensor != null && !mDozeSensorRegistered) {
                    mSensorManager.registerListener(mDozeLightSensorListener, mLightSensor,
                            SensorManager.SENSOR_DELAY_NORMAL, mHandler);
                    mDozeSensorRegistered = true;
                    dlog("startDozeBrightnessControl: registered fallback mLightSensor");
                }

                if (mPocketSensor != null && !mPocketSensorRegistered) {
                    mSensorManager.registerListener(mPocketSensorListener, mPocketSensor,
                            SensorManager.SENSOR_DELAY_NORMAL, mHandler);
                    mPocketSensorRegistered = true;
                    dlog("startDozeBrightnessControl: registered mPocketSensor");
                }
            }
        }
    }

    private void stopDozeBrightnessControl() {
        if (mSensorManager != null) {
            if (mAodSensorRegistered) {
                mSensorManager.unregisterListener(mAodSensorListener);
                mAodSensorRegistered = false;
            }
            if (mDozeSensorRegistered) {
                mSensorManager.unregisterListener(mDozeLightSensorListener);
                mDozeSensorRegistered = false;
            }
            if (mPocketSensorRegistered) {
                mSensorManager.unregisterListener(mPocketSensorListener);
                mPocketSensorRegistered = false;
            }
        }
        mHandler.removeCallbacks(mApplyDozeModeRunnable);
        mPendingDozeMode = -1;
        mIsInPocket = false;
        mCurrentDozeMode = -1;
        mLastAodValue = -1.0f;
        mFirstSensorReport = false;
    }

    public void onDestroy() {
        stopDozeBrightnessControl();
        if (mSettingObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
