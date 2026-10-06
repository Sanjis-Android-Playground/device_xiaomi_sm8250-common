/*
 * Copyright (C) 2023 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

import static android.provider.Settings.System.DISPLAY_COLOR_MODE;
import static org.lineageos.settings.display.DfWrapper.DfParams;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import org.lineageos.settings.utils.FileUtils;

import java.util.Map;

public class ColorService extends Service {
    private static final String TAG = "ColorService";
    private static final boolean DEBUG = true;

    public static final String DISP_PARAM_NODE = "/sys/class/drm/card0-DSI-1/disp_param";
    public static final String DISPPARAM_GIR_OFF = "0xf0000000";

    public static final String KEY_TRUE_TONE = TrueToneController.KEY_TRUE_TONE;
    public static final String KEY_SUNLIGHT_MODE = SunlightController.KEY_SUNLIGHT_MODE;

    private static final int DEFAULT_COLOR_MODE = SystemProperties.getInt(
            "persist.sys.sf.native_mode", 258);

    private static final DfParams STANDARD_PARAMS = new DfParams(
            DfWrapper.FEATURE_ACNORMAL, 2, 255);

    /* color mode -> displayfeature (mode, value, cookie) */
    private static final Map<Integer, DfParams> COLOR_MAP = Map.of(
        258 /* Vivid */, STANDARD_PARAMS,
        256 /* Saturated */, new DfParams(DfWrapper.FEATURE_INCREASED_CONTRAST, 2, 255),
        257 /* Original */, new DfParams(DfWrapper.FEATURE_STANDARD, 2, 255),
        266 /* expert-native */, new DfParams(DfWrapper.FEATURE_EXPERT, 0, 0),
        269 /* expert-wcg-srgb */, new DfParams(DfWrapper.FEATURE_EXPERT, 1, 0),
        268 /* expert-dcip3 */, new DfParams(DfWrapper.FEATURE_EXPERT, 2, 0),
        267 /* expert-srgb */, new DfParams(DfWrapper.FEATURE_EXPERT, 3, 0)
    );

    private Handler mHandler = new Handler();
    private AmbientDisplayConfiguration mAmbientConfig;
    private boolean mScreenOn = true;
    private boolean mIsDozing = false;
    private int mLastAppliedColorMode = -1;

    private TrueToneController mTrueToneController;
    private SunlightController mSunlightController;

    private final ContentObserver mSettingObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            dlog("SettingObserver: onChange");
            setCurrentColorMode();
        }
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dlog("onReceive: " + intent.getAction());
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    mScreenOn = true;
                    if (mSunlightController != null) {
                        mSunlightController.onScreenStateChanged(mScreenOn, mIsDozing);
                    }
                    if (mIsDozing) {
                        mIsDozing = false;
                        mHandler.removeCallbacksAndMessages(null);
                        mHandler.postDelayed(() -> {
                            dlog("Was in AOD, restore color mode");
                            setCurrentColorMode();
                        }, 100);
                    }
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOn = false;
                    if (mSunlightController != null) {
                        mSunlightController.onScreenStateChanged(mScreenOn, mIsDozing);
                    }
                    mHandler.removeCallbacksAndMessages(null);
                    if (!mAmbientConfig.alwaysOnEnabled(UserHandle.USER_CURRENT)) {
                        dlog("AOD is not enabled");
                        mIsDozing = false;
                        break;
                    }
                    mIsDozing = true;
                    dlog("Entered AOD, set color mode to standard");
                    DfWrapper.setDisplayFeature(STANDARD_PARAMS);
                    mLastAppliedColorMode = -1;
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        dlog("onCreate");
        mAmbientConfig = new AmbientDisplayConfiguration(this);
        mTrueToneController = new TrueToneController(this, mHandler);
        mSunlightController = new SunlightController(this, mHandler);
        mSunlightController.onScreenStateChanged(mScreenOn, mIsDozing);

        getContentResolver().registerContentObserver(Settings.System.getUriFor(DISPLAY_COLOR_MODE),
                    false, mSettingObserver, UserHandle.USER_CURRENT);
        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        // Ensure legacy display_color_adjustment is reset so it does not conflict with DisplayFeature PCC
        final String adj = Settings.System.getStringForUser(getContentResolver(),
                "display_color_adjustment", UserHandle.USER_CURRENT);
        if (adj != null && !adj.isEmpty() && !"1.0 1.0 1.0".equals(adj)) {
            dlog("onCreate: clearing legacy display_color_adjustment (" + adj + ")");
            Settings.System.putStringForUser(getContentResolver(),
                    "display_color_adjustment", "1.0 1.0 1.0", UserHandle.USER_CURRENT);
        }

        setCurrentColorMode();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dlog("onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        dlog("onDestroy");
        if (mTrueToneController != null) {
            mTrueToneController.onDestroy();
        }
        if (mSunlightController != null) {
            mSunlightController.onDestroy();
        }
        getContentResolver().unregisterContentObserver(mSettingObserver);
        unregisterReceiver(mScreenStateReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, ColorService.class), UserHandle.CURRENT);
    }

    private static boolean isExpertMode(int mode) {
        return mode == 266 || mode == 269 || mode == 268 || mode == 267;
    }

    public static void setGirOff() {
        if (FileUtils.fileExists(DISP_PARAM_NODE)) {
            dlog("setGirOff: writing " + DISPPARAM_GIR_OFF + " to " + DISP_PARAM_NODE);
            FileUtils.writeLine(DISP_PARAM_NODE, DISPPARAM_GIR_OFF);
        }
    }

    private void setCurrentColorMode() {
        if (mIsDozing) {
            dlog("setCurrentColorMode: skip in AOD");
            return;
        }
        final int colorMode = Settings.System.getIntForUser(getContentResolver(),
                DISPLAY_COLOR_MODE, DEFAULT_COLOR_MODE, UserHandle.USER_CURRENT);
        if (!COLOR_MAP.containsKey(colorMode)) {
            Log.e(TAG, "setCurrentColorMode: " + colorMode + " is not in colorMap!");
            return;
        }
        final DfParams params = COLOR_MAP.get(colorMode);
        dlog("setCurrentColorMode: " + colorMode + ", params=" + params
                + " (lastApplied=" + mLastAppliedColorMode + ")");

        if (mLastAppliedColorMode != -1 && mLastAppliedColorMode != colorMode) {
            dlog("setCurrentColorMode: tearing down previous mode " + mLastAppliedColorMode);
            if (mLastAppliedColorMode == 256) {
                DfWrapper.setDisplayFeature(DfWrapper.FEATURE_INCREASED_CONTRAST, 0, 0);
            } else if (mLastAppliedColorMode == 257 || mLastAppliedColorMode == 269) {
                DfWrapper.setDisplayFeature(DfWrapper.FEATURE_ACNORMAL, 0, 0);
                if (mLastAppliedColorMode == 269 && !isExpertMode(colorMode)) {
                    DfWrapper.setDisplayFeature(DfWrapper.FEATURE_EXPERT, 0, DfWrapper.EXPERT_DISABLE);
                }
            } else if (mLastAppliedColorMode == 258) {
                DfWrapper.setDisplayFeature(DfWrapper.FEATURE_ACNORMAL, 0, 0);
            } else if (isExpertMode(mLastAppliedColorMode) && !isExpertMode(colorMode)) {
                DfWrapper.setDisplayFeature(DfWrapper.FEATURE_EXPERT, 0, DfWrapper.EXPERT_DISABLE);
            }
        }

        if (colorMode == 257 && !isExpertMode(mLastAppliedColorMode)) {
            dlog("setCurrentColorMode: pre-transitioning via expert mode for D65 white point");
            DfWrapper.setDisplayFeature(DfWrapper.FEATURE_EXPERT, 1, DfWrapper.EXPERT_GAMUT);
        }

        DfWrapper.setDisplayFeature(params);

        if (colorMode == 266) {
            setGirOff();
            mHandler.postDelayed(ColorService::setGirOff, 50);
        }

        if (colorMode == 257 || colorMode == 269) {
            DfWrapper.sendMessage(39, colorMode);
        }

        mLastAppliedColorMode = colorMode;
        if (mTrueToneController != null) {
            mTrueToneController.update();
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
