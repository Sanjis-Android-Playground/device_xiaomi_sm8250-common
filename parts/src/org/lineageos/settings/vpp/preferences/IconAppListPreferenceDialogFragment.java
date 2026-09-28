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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IconAppListPreferenceDialogFragment extends PreferenceDialogFragmentCompat {
    private static final String TAG = "IconAppListDialog";
    private static final boolean DEBUG = false;

    private final List<AppInfo> mAppList = new ArrayList<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private AppListAdapter mAdapter;
    private RecyclerView mRecyclerView;

    public static IconAppListPreferenceDialogFragment newInstance(String key) {
        IconAppListPreferenceDialogFragment fragment = new IconAppListPreferenceDialogFragment();
        Bundle bundle = new Bundle(1);
        bundle.putString(ARG_KEY, key);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_app_list, null);
        mRecyclerView = view.findViewById(R.id.app_list_recycler_view);
        mAdapter = new AppListAdapter(mAppList);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setAdapter(mAdapter);

        IconAppListPreference preference = (IconAppListPreference) getPreference();
        Set<String> selectedApps = preference != null ? preference.getValues() : Collections.emptySet();
        loadApps(selectedApps);
        return view;
    }

    private void loadApps(final Set<String> selectedApps) {
        mExecutor.execute(() -> {
            Context context = getContext();
            if (context == null) {
                return;
            }

            PackageManager pm = context.getPackageManager();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, 0);
            Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(pm));

            List<AppInfo> apps = new ArrayList<>();
            for (ResolveInfo info : resolveInfos) {
                if (info.activityInfo == null) {
                    continue;
                }
                Drawable icon = info.loadIcon(pm);
                String label = info.loadLabel(pm).toString();
                String packageName = info.activityInfo.packageName;
                boolean isSelected = selectedApps != null && selectedApps.contains(packageName);
                apps.add(new AppInfo(icon, label, packageName, isSelected));
            }

            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    mAppList.clear();
                    mAppList.addAll(apps);
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            Set<String> selectedApps = new HashSet<>();
            for (AppInfo app : mAppList) {
                if (app.isSelected) {
                    selectedApps.add(app.packageName);
                }
            }
            if (DEBUG) {
                Log.d(TAG, "onDialogClosed: saving " + selectedApps.size() + " apps: " + selectedApps);
            }
            IconAppListPreference preference = (IconAppListPreference) getPreference();
            if (preference != null) {
                preference.setValues(selectedApps);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    private static class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private final List<AppInfo> mApps;

        AppListAdapter(List<AppInfo> apps) {
            this.mApps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            final AppInfo app = mApps.get(position);
            holder.icon.setImageDrawable(app.icon);
            holder.name.setText(app.name);
            holder.checkbox.setChecked(app.isSelected);

            View.OnClickListener clickListener = v -> {
                app.isSelected = !app.isSelected;
                holder.checkbox.setChecked(app.isSelected);
            };

            holder.itemView.setOnClickListener(clickListener);
            holder.checkbox.setOnClickListener(clickListener);
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final CheckBox checkbox;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.icon = itemView.findViewById(R.id.app_icon);
                this.name = itemView.findViewById(R.id.app_name);
                this.checkbox = itemView.findViewById(R.id.app_checkbox);
            }
        }
    }
}
