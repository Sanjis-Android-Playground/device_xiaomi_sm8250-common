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

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DisplayNodes;

public class HBMModeTileService extends TileService {

    public static void requestUpdate(Context context) {
        try {
            requestListeningState(context,
                    new ComponentName(context, HBMModeTileService.class));
        } catch (Exception ignored) {
        }
    }

    private void updateUi(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateUi(HBMManager.isEnabled());
    }

    @Override
    public void onClick() {
        super.onClick();
        final boolean enabled = !HBMManager.isEnabled();
        if (HBMManager.setEnabled(this, HBMManager.Owner.MANUAL, enabled)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(DisplayNodes.getHbmEnableKey(), enabled).apply();
        }
        updateUi(HBMManager.isEnabled());
    }
}
