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
*/

package org.lineageos.settings.thermal;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.R;

public class ThermalTileService extends TileService {

    private ThermalUtils mThermalUtils;

    @Override
    public void onCreate() {
        super.onCreate();
        mThermalUtils = new ThermalUtils(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        int nextMode = (mThermalUtils.getQsMode() + 1) % 3;
        mThermalUtils.setQsMode(nextMode);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setState(Tile.STATE_ACTIVE);

        switch (mThermalUtils.getQsMode()) {
            case ThermalUtils.QS_MODE_DEFAULT:
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_thermal_default));
                tile.setLabel(getString(R.string.thermal_qs_default));
                tile.setSubtitle(getString(R.string.thermal_qs_default));
                break;
            case ThermalUtils.QS_MODE_PERFORMANCE:
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_thermal_performance));
                tile.setLabel(getString(R.string.thermal_qs_performance));
                tile.setSubtitle(getString(R.string.thermal_qs_performance));
                break;
            case ThermalUtils.QS_MODE_BATTERY:
            default:
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_thermal_battery));
                tile.setLabel(getString(R.string.thermal_qs_battery));
                tile.setSubtitle(getString(R.string.thermal_qs_battery));
                break;
        }

        tile.updateTile();
    }
}
