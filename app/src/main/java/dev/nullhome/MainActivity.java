package dev.nullhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class MainActivity extends Activity {

    private static final String TAG = "NullHome";
    private static final String BUILD = "drawer-v3.1-clean-scroll";
    private static final String EXTRA_OPEN_DRAWER = "dev.nullhome.OPEN_DRAWER";

    private static final class Entry {
        final String label;
        final ComponentName component;

        Entry(String label, ComponentName component) {
            this.label = label;
            this.component = component;
        }
    }

    private final ArrayList<Entry> apps = new ArrayList<>();

    private float downX;
    private float downY;
    private boolean trackingGesture;
    private boolean gestureTriggered;
    private boolean drawerOpen;

    private View blackView;

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private void hideSystemUI() {
        final Window w = getWindow();

        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);

            WindowInsetsController c = w.getInsetsController();

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
            w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void showBlackHome() {
        drawerOpen = false;
        apps.clear();

        if (blackView == null) {
            blackView = new View(this);
            blackView.setBackgroundColor(Color.BLACK);
        }

        setContentView(blackView);
        hideSystemUI();
    }

    private void openSettingsFallback() {
        try {
            Intent settings = new Intent(Settings.ACTION_SETTINGS);
            startActivity(settings);
        } catch (Throwable ignored) {
        }
    }

    private void showFailure(String text) {
        drawerOpen = true;

        TextView failure = new TextView(this);
        failure.setBackgroundColor(Color.BLACK);
        failure.setTextColor(Color.WHITE);
        failure.setTextSize(16);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(20), dp(20), dp(20), dp(20));
        failure.setText(
            text
            + "\n\nTap to open Settings"
            + "\nBack to return home"
        );

        failure.setOnClickListener(v -> openSettingsFallback());

        setContentView(failure);
        hideSystemUI();
    }

    private void showDrawer() {
        if (drawerOpen) {
            return;
        }

        drawerOpen = true;
        apps.clear();

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
            Log.e(TAG, BUILD + " app query failed", t);
            showFailure("APP QUERY FAILED");
            return;
        }

        if (resolved == null || resolved.isEmpty()) {
            Log.e(TAG, BUILD + " no app results");
            showFailure("NO LAUNCHABLE APPS FOUND");
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

        Collections.sort(
            apps,
            (a, b) -> collator.compare(a.label, b.label)
        );

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

        /*
         * Strip every stock ListView visual effect. On the forced
         * 216x468 / 90dpi software-rendered window, Samsung/Material's
         * selector + scroll cache can look like a translucent black veil
         * while dragging. The drawer should draw only black + white text.
         */
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setDrawSelectorOnTop(false);
        list.setScrollingCacheEnabled(false);
        list.setAnimationCacheEnabled(false);
        list.setWillNotCacheDrawing(true);
        list.setVerticalFadingEdgeEnabled(false);
        list.setHorizontalFadingEdgeEnabled(false);
        list.setFadingEdgeLength(0);
        list.setVerticalScrollBarEnabled(false);
        list.setHorizontalScrollBarEnabled(false);
        list.setSmoothScrollbarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        list.setSoundEffectsEnabled(false);
        list.setHapticFeedbackEnabled(false);

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
                    android.view.ViewGroup parent) {

                TextView row;

                if (convertView instanceof TextView) {
                    row = (TextView) convertView;
                } else {
                    row = new TextView(MainActivity.this);
                    row.setTextColor(Color.WHITE);
                    row.setTextSize(17);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setSingleLine(true);
                    row.setPadding(dp(18), 0, dp(12), 0);
                    row.setMinHeight(dp(50));
                    row.setBackgroundColor(Color.BLACK);
                    row.setSoundEffectsEnabled(false);
                    row.setHapticFeedbackEnabled(false);
                }

                row.setText(apps.get(position).label);
                return row;
            }
        });

        list.setOnItemClickListener(
            (parent, view, position, id) -> {
                Entry entry = apps.get(position);
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

                showBlackHome();
            }
        );

        setContentView(list);
        hideSystemUI();
    }

    private void triggerDrawerFromGesture(float x, float y) {
        float dx = x - downX;
        float upward = downY - y;

        if (
            !gestureTriggered
            && upward >= dp(32)
            && upward > Math.abs(dx) * 1.10f
        ) {
            gestureTriggered = true;
            trackingGesture = false;

            Log.i(
                TAG,
                BUILD + " gesture-up dx=" + dx + " dy=" + upward
            );

            getWindow().getDecorView().post(this::showDrawer);
        }
    }

    private void handleTestIntent(Intent intent) {
        if (
            intent != null
            && intent.getBooleanExtra(EXTRA_OPEN_DRAWER, false)
        ) {
            getWindow().getDecorView().post(this::showDrawer);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );

        showBlackHome();
        handleTestIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showBlackHome();
        handleTestIntent(intent);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (drawerOpen) {
            return super.dispatchTouchEvent(event);
        }

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                trackingGesture = true;
                gestureTriggered = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (trackingGesture) {
                    triggerDrawerFromGesture(
                        event.getX(),
                        event.getY()
                    );
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (trackingGesture) {
                    triggerDrawerFromGesture(
                        event.getX(),
                        event.getY()
                    );
                }
                trackingGesture = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                trackingGesture = false;
                return true;
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onStop() {
        if (drawerOpen) {
            showBlackHome();
        }

        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);

        if (focus) {
            hideSystemUI();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            showBlackHome();
        }
    }
}
