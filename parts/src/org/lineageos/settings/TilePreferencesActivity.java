/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import org.lineageos.settings.dirac.DiracActivity;
import org.lineageos.settings.dirac.DiracTileService;
import org.lineageos.settings.display.DcDimmingSettingsActivity;
import org.lineageos.settings.display.DcDimmingTileService;
import org.lineageos.settings.hbm.HBMActivity;
import org.lineageos.settings.hbm.HBMModeTileService;
import org.lineageos.settings.refreshrate.RefreshActivity;
import org.lineageos.settings.thermal.ThermalActivity;
import org.lineageos.settings.thermal.ThermalTileService;

/**
 * Routes the long-press action of our Quick Settings tiles to the matching
 * settings screen.
 */
public class TilePreferencesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComponentName component = getIntent().getParcelableExtra(
                Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
        if (component == null) {
            finish();
            return;
        }

        Class<?> target = null;
        String className = component.getClassName();

        if (ThermalTileService.class.getName().equals(className)) {
            target = ThermalActivity.class;
        } else if (HBMModeTileService.class.getName().equals(className)) {
            target = HBMActivity.class;
        } else if (DcDimmingTileService.class.getName().equals(className)) {
            target = DcDimmingSettingsActivity.class;
        } else if (RefreshRateTileService.class.getName().equals(className)) {
            target = RefreshActivity.class;
        } else if (DiracTileService.class.getName().equals(className)) {
            target = DiracActivity.class;
        }

        if (target != null) {
            startActivity(new Intent(this, target));
        }

        finish();
    }
}
