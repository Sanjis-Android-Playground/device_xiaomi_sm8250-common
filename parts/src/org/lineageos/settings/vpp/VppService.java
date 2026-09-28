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
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class VppService extends Service {
    private static final String TAG = "VppService";
    private static final boolean DEBUG = false;

    private IActivityTaskManager mActivityTaskManager;
    private String mPreviousApp = "";

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                VppUtils.applyVppProps(false, false);
                mPreviousApp = "";
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                VppUtils.updateState(context);
            }
        }
    };

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            try {
                if (mActivityTaskManager != null) {
                    ActivityTaskManager.RootTaskInfo focusedRootTaskInfo =
                            mActivityTaskManager.getFocusedRootTaskInfo();
                    if (focusedRootTaskInfo != null && focusedRootTaskInfo.topActivity != null) {
                        String packageName = focusedRootTaskInfo.topActivity.getPackageName();
                        if (packageName.equals(mPreviousApp)) {
                            return;
                        }
                        mPreviousApp = packageName;
                        VppUtils.handleForegroundAppChange(VppService.this, packageName);
                    }
                }
            } catch (Exception e) {
                if (DEBUG) {
                    Log.e(TAG, "Error in onTaskStackChanged", e);
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register TaskStackListener", e);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);

        VppUtils.updateState(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        VppUtils.updateState(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            }
        } catch (Exception ignored) {
        }

        try {
            unregisterReceiver(mIntentReceiver);
        } catch (Exception ignored) {
        }

        VppUtils.applyVppProps(false, false);
    }
}
