/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import org.lineageos.settings.display.DcDimmingTileService;
import org.lineageos.settings.display.DisplaySettingsActivity;
import org.lineageos.settings.hbm.HBMActivity;
import org.lineageos.settings.hbm.HBMModeTileService;

/** Routes a QS tile long-press to the matching settings page. */
public class TilePreferencesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ComponentName component =
                getIntent().getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
        final String className = component != null ? component.getClassName() : null;
        Class<?> target = null;

        if (className != null) {
            if (className.equals(HBMModeTileService.class.getName())) {
                target = HBMActivity.class;
            } else if (className.equals(DcDimmingTileService.class.getName())) {
                target = DisplaySettingsActivity.class;
            }
        }

        if (target != null) {
            startActivity(new Intent(this, target));
        }
        finish();
    }
}
