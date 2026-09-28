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

package org.lineageos.settings.vpp.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;

import androidx.preference.DialogPreference;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IconAppListPreference extends DialogPreference {
    private static final String TAG = "IconAppListPreference";
    private static final boolean DEBUG = false;

    private Set<String> mValues = new HashSet<>();

    public IconAppListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    public void setValues(Set<String> values) {
        Set<String> newValues = values != null ? new HashSet<>(values) : new HashSet<>();
        if (callChangeListener(newValues)) {
            mValues = newValues;
            persistStringSet(newValues);
            notifyChanged();
            if (DEBUG) {
                Log.d(TAG, "setValues: persisted " + newValues.size() + " apps: " + newValues);
            }
        }
    }

    public Set<String> getValues() {
        Set<String> persistedStringSet = getPersistedStringSet(null);
        if (persistedStringSet != null) {
            mValues = new HashSet<>(persistedStringSet);
        }
        return mValues != null ? mValues : Collections.emptySet();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return Collections.emptySet();
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        Set<String> persistedStringSet = getPersistedStringSet(null);
        if (persistedStringSet != null) {
            mValues = new HashSet<>(persistedStringSet);
        } else if (defaultValue instanceof Set<?>) {
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) defaultValue;
            mValues = new HashSet<>(set);
        } else {
            mValues = new HashSet<>();
        }
        if (DEBUG) {
            Log.d(TAG, "onSetInitialValue: loaded " + mValues.size() + " apps: " + mValues);
        }
    }
}
