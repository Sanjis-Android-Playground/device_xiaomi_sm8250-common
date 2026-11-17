/*
* Copyright (C) 2018 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.lineageos.settings.hbm;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.lineageos.settings.display.DisplayUtils;

public class AutoHBMService extends Service implements SensorEventListener {

    private static final float LUX_THRESHOLD = 20000f;
    private static final long DISABLE_DELAY_MS = 7000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private boolean mActive;
    private boolean mListening;

    private final Runnable mDisableRunnable = () -> {
        mActive = false;
        HBMManager.setEnabled(this, HBMManager.Owner.AUTO, false);
        HBMModeTileService.requestUpdate(this);
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                registerLightSensor();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                unregisterLightSensor();
                mHandler.removeCallbacks(mDisableRunnable);
                mActive = false;
            }
        }
    };

    private void registerLightSensor() {
        if (mListening || mSensorManager == null || mLightSensor == null) {
            return;
        }
        mSensorManager.registerListener(this, mLightSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        mListening = true;
    }

    private void unregisterLightSensor() {
        if (!mListening || mSensorManager == null) {
            return;
        }
        mSensorManager.unregisterListener(this);
        mListening = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = getSystemService(SensorManager.class);
        if (mSensorManager != null) {
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        final PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isInteractive()) {
            registerLightSensor();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenStateReceiver);
        unregisterLightSensor();
        mHandler.removeCallbacks(mDisableRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float lux = event.values[0];

        if (lux < LUX_THRESHOLD) {
            if (mActive) {
                mHandler.removeCallbacks(mDisableRunnable);
                mHandler.postDelayed(mDisableRunnable, DISABLE_DELAY_MS);
            }
            return;
        }

        mHandler.removeCallbacks(mDisableRunnable);
        if (mActive || HBMManager.getOwner() == HBMManager.Owner.MANUAL) {
            return;
        }

        final KeyguardManager km = getSystemService(KeyguardManager.class);
        if ((km != null && km.inKeyguardRestrictedInputMode())
                || HBMManager.isDcDimmingEnabled(this)
                || !DisplayUtils.isHbmSupported()) {
            return;
        }

        if (HBMManager.setEnabled(this, HBMManager.Owner.AUTO, true)) {
            mActive = true;
            HBMModeTileService.requestUpdate(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
