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

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final Drawable icon;
    public final String name;
    public final String packageName;
    public boolean isSelected;

    public AppInfo(Drawable icon, String name, String packageName, boolean isSelected) {
        this.icon = icon;
        this.name = name;
        this.packageName = packageName;
        this.isSelected = isSelected;
    }
}
