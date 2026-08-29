package dev.nullhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class DrawerActivity extends Activity {

    private static final String TAG = "NullHomeDrawer";
    private static final String BUILD = "drawer-v2-same-process";

    private static final class Entry {
        final String label;
        final ComponentName component;

        Entry(String label, ComponentName component) {
            this.label = label;
            this.component = component;
        }
    }

    private final ArrayList<Entry> apps = new ArrayList<>();

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void hideSystemUI() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);

            WindowInsetsController c = getWindow().getInsetsController();

            if (c != null) {
                c.hide(
                    WindowInsets.Type.statusBars()
                    | WindowInsets.Type.navigationBars()
                );

                c.setSystemBarsBehavior(
                    WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void closeDrawer() {
        finish();
        overridePendingTransition(0, 0);
    }

    private void openSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            startActivity(i);
            closeDrawer();
        } catch (Throwable t) {
            Log.e(TAG, "settings fallback failed", t);
        }
    }

    private void showFailure(String text) {
        Log.e(TAG, BUILD + " " + text);

        TextView failure = new TextView(this);
        failure.setBackgroundColor(Color.BLACK);
        failure.setTextColor(Color.WHITE);
        failure.setTextSize(16);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(20), dp(20), dp(20), dp(20));
        failure.setText(
            text
            + "\n\nTap to open Settings"
            + "\nBack to close"
        );
        failure.setOnClickListener(v -> openSettings());

        setContentView(failure);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        hideSystemUI();

        final PackageManager pm = getPackageManager();
        final Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        final List<ResolveInfo> resolved;

        try {
            resolved = pm.queryIntentActivities(
                query,
                PackageManager.MATCH_ALL
            );
        } catch (Throwable t) {
            Log.e(TAG, BUILD + " query failed", t);
            showFailure("APP QUERY FAILED");
            return;
        }

        if (resolved == null || resolved.isEmpty()) {
            showFailure("NO APP RESULTS");
            return;
        }

        final HashSet<String> seen = new HashSet<>();

        for (ResolveInfo ri : resolved) {
            if (ri == null || ri.activityInfo == null) {
                continue;
            }

            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;

            if (pkg == null || cls == null) {
                continue;
            }

            if (getPackageName().equals(pkg)) {
                continue;
            }

            if (!ri.activityInfo.enabled || !ri.activityInfo.exported) {
                continue;
            }

            ApplicationInfo appInfo = ri.activityInfo.applicationInfo;

            if (appInfo == null || !appInfo.enabled) {
                continue;
            }

            if ((appInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) {
                continue;
            }

            ComponentName component = new ComponentName(pkg, cls);
            String key = component.flattenToString();

            if (!seen.add(key)) {
                continue;
            }

            CharSequence loaded;

            try {
                loaded = ri.loadLabel(pm);
            } catch (Throwable ignored) {
                loaded = null;
            }

            String label =
                (loaded == null || loaded.length() == 0)
                ? pkg
                : loaded.toString();

            apps.add(new Entry(label, component));
        }

        final Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label, b.label));

        Log.i(
            TAG,
            "build=" + BUILD + " apps=" + apps.size()
        );

        if (apps.isEmpty()) {
            showFailure("0 LAUNCHABLE APPS");
            return;
        }

        final ListView list = new ListView(this);

        list.setBackgroundColor(Color.BLACK);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setCacheColorHint(Color.BLACK);
        list.setFadingEdgeLength(0);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);

        list.setAdapter(new BaseAdapter() {

            @Override
            public int getCount() {
                return apps.size();
            }

            @Override
            public Entry getItem(int position) {
                return apps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(
                int position,
                View convertView,
                android.view.ViewGroup parent
            ) {
                TextView row;

                if (convertView instanceof TextView) {
                    row = (TextView) convertView;
                } else {
                    row = new TextView(DrawerActivity.this);
                    row.setTextColor(Color.WHITE);
                    row.setTextSize(17);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setSingleLine(true);
                    row.setPadding(dp(18), 0, dp(12), 0);
                    row.setMinHeight(dp(50));
                    row.setBackgroundColor(Color.BLACK);
                }

                row.setText(apps.get(position).label);
                return row;
            }
        });

        list.setOnItemClickListener(
            (parent, view, position, id) -> {
                Entry entry = apps.get(position);

                /*
                 * makeMainActivity() produces the normal launcher-style
                 * MAIN/LAUNCHER task flags for the exact component.
                 */
                Intent launch = Intent.makeMainActivity(entry.component);
                launch.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                try {
                    startActivity(launch);
                    overridePendingTransition(0, 0);
                } catch (Throwable t) {
                    Log.e(
                        TAG,
                        BUILD + " launch failed: "
                        + entry.component.flattenToShortString(),
                        t
                    );

                    if (view instanceof TextView) {
                        ((TextView) view).setText(
                            "Launch failed: " + entry.label
                        );
                    }
                    return;
                }

                /*
                 * Close the drawer Activity immediately after Android accepts
                 * the target launch. No process kill, no race, no extra task.
                 */
                closeDrawer();
            }
        );

        setContentView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);

        if (focus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onDestroy() {
        apps.clear();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        closeDrawer();
    }
}
