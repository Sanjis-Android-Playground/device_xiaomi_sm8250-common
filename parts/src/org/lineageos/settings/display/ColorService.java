/*
 * Copyright (C) 2023-2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.display;

import static android.provider.Settings.System.DISPLAY_COLOR_MODE;
import static org.lineageos.settings.display.DfWrapper.DfParams;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import org.lineageos.settings.utils.FileUtils;

import java.util.Map;

public class ColorService extends Service {
    private static final String TAG = "ColorService";
    private static final boolean DEBUG = true;

    public static final String DISP_PARAM_NODE = "/sys/class/drm/card0-DSI-1/disp_param";
    public static final String DISPPARAM_GIR_OFF = "0xf0000000";

    // Forwarding keys for external callers
    public static final String KEY_TRUE_TONE = TrueToneController.KEY_TRUE_TONE;
    public static final String KEY_SUNLIGHT_MODE = SunlightController.KEY_SUNLIGHT_MODE;
    public static final String KEY_DOZE_BRIGHTNESS = DozeBrightnessController.KEY_DOZE_BRIGHTNESS;

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

    private final Handler mHandler = new Handler();
    private DisplayManager mDisplayManager;

    private boolean mScreenOn = true;
    private boolean mIsDozing = false;
    private int mLastAppliedColorMode = -1;

    // Decoupled controllers
    private TrueToneController mTrueToneController;
    private SunlightController mSunlightController;
    private DozeBrightnessController mDozeBrightnessController;

    private final Runnable mRestoreColorModeRunnable = () -> {
        dlog("Was in AOD, restore color mode");
        setCurrentColorMode();
    };

    private final ContentObserver mSettingObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, android.net.Uri uri) {
            dlog("SettingObserver: onChange: " + uri);
            setCurrentColorMode();
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY || mDisplayManager == null) return;
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return;
            final int state = display.getState();
            dlog("onDisplayChanged: state=" + state);
            handleDisplayStateChange(state);
        }
    };

    private void handleDisplayStateChange(int state) {
        switch (state) {
            case Display.STATE_DOZE:
            case Display.STATE_DOZE_SUSPEND:
                mScreenOn = false;
                if (!mIsDozing) {
                    mIsDozing = true;
                    dlog("Entered AOD, set color mode to standard");
                    DfWrapper.setDisplayFeature(STANDARD_PARAMS);
                    mLastAppliedColorMode = -1;
                }
                break;
            case Display.STATE_OFF:
                mScreenOn = false;
                mIsDozing = false;
                break;
            case Display.STATE_ON:
                mScreenOn = true;
                if (mIsDozing) {
                    mIsDozing = false;
                    mHandler.removeCallbacks(mRestoreColorModeRunnable);
                    mHandler.postDelayed(mRestoreColorModeRunnable, 100);
                }
                break;
            default:
                break;
        }

        if (mSunlightController != null) {
            mSunlightController.onScreenStateChanged(mScreenOn, mIsDozing);
        }
        if (mDozeBrightnessController != null) {
            mDozeBrightnessController.onDisplayStateChanged(state);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dlog("onCreate");
        mDisplayManager = getSystemService(DisplayManager.class);

        // Initialize decoupled controllers
        mTrueToneController = new TrueToneController(this, mHandler);
        mSunlightController = new SunlightController(this, mHandler);
        mDozeBrightnessController = new DozeBrightnessController(this, mHandler);

        if (mDisplayManager != null) {
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
            Display defaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (defaultDisplay != null) {
                mScreenOn = (defaultDisplay.getState() == Display.STATE_ON);
                mSunlightController.onScreenStateChanged(mScreenOn, mIsDozing);
            }
        }

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(DISPLAY_COLOR_MODE),
                false, mSettingObserver, UserHandle.USER_CURRENT);

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
        mHandler.removeCallbacks(mRestoreColorModeRunnable);

        if (mTrueToneController != null) {
            mTrueToneController.onDestroy();
        }
        if (mSunlightController != null) {
            mSunlightController.onDestroy();
        }
        if (mDozeBrightnessController != null) {
            mDozeBrightnessController.onDestroy();
        }

        if (mDisplayManager != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
        getContentResolver().unregisterContentObserver(mSettingObserver);
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
