package dev.nullhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
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

    private static final class Entry {
        final String label;
        final ComponentName component;

        Entry(String label, ComponentName component) {
            this.label = label;
            this.component = component;
        }
    }

    private final ArrayList<Entry> apps = new ArrayList<>();
    private float downY;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideSystemUI() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }
    }

    private void terminateDrawerSoon() {
        final int pid = Process.myPid();
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> Process.killProcess(pid),
            750
        );
    }

    private void closeDrawer() {
        finishAndRemoveTask();
        terminateDrawerSoon();
    }

    private void showFailure(String text) {
        Log.e(TAG, text);

        TextView failure = new TextView(this);
        failure.setBackgroundColor(Color.BLACK);
        failure.setTextColor(Color.WHITE);
        failure.setTextSize(16);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(20), dp(20), dp(20), dp(20));
        failure.setText(text + "\n\nTap to close");
        failure.setOnClickListener(v -> closeDrawer());
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
            resolved = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
        } catch (Throwable t) {
            Log.e(TAG, "query failed", t);
            showFailure("APP QUERY FAILED");
            return;
        }

        if (resolved == null) {
            showFailure("NO APP RESULTS");
            return;
        }

        final HashSet<String> seen = new HashSet<>();

        for (ResolveInfo ri : resolved) {
            if (ri == null || ri.activityInfo == null) continue;

            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;

            if (pkg == null || cls == null) continue;
            if (getPackageName().equals(pkg)) continue;
            if (!ri.activityInfo.enabled) continue;
            if (ri.activityInfo.applicationInfo == null ||
                !ri.activityInfo.applicationInfo.enabled) continue;

            ComponentName component = new ComponentName(pkg, cls);
            String flattened = component.flattenToString();
            if (!seen.add(flattened)) continue;

            CharSequence cs;
            try {
                cs = ri.loadLabel(pm);
            } catch (Throwable ignored) {
                cs = null;
            }

            String label = (cs == null || cs.length() == 0)
                ? pkg
                : cs.toString();

            apps.add(new Entry(label, component));
        }

        final Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label, b.label));

        Log.i(TAG, "apps=" + apps.size());

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
            public View getView(int position, View convertView,
                                android.view.ViewGroup parent) {
                TextView row;
                if (convertView instanceof TextView) {
                    row = (TextView) convertView;
                } else {
                    row = new TextView(DrawerActivity.this);
                    row.setTextColor(Color.WHITE);
                    row.setTextSize(16);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setSingleLine(true);
                    row.setPadding(dp(18), 0, dp(12), 0);
                    row.setMinHeight(dp(48));
                    row.setBackgroundColor(Color.BLACK);
                }
                row.setText(apps.get(position).label);
                return row;
            }
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = apps.get(position);

            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(entry.component);
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            );

            try {
                startActivity(launch);
            } catch (Throwable t) {
                Log.e(TAG, "launch failed: " + entry.component, t);
                if (view instanceof TextView) {
                    ((TextView) view).setText("Launch failed — press Back");
                }
                return;
            }

            finishAndRemoveTask();
            terminateDrawerSoon();
        });

        list.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downY = event.getY();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    float down = event.getY() - downY;
                    if (list.getFirstVisiblePosition() == 0 && down >= dp(72)) {
                        closeDrawer();
                        return true;
                    }
                    break;
            }
            return false;
        });

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
        if (focus) hideSystemUI();
    }

    @Override
    public void onBackPressed() {
        closeDrawer();
    }
}
