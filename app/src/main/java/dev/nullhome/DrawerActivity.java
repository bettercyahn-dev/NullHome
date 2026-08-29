package dev.nullhome;

import android.app.Activity;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DrawerActivity extends Activity {

    private final ArrayList<LauncherActivityInfo> apps =
            new ArrayList<>();

    private LauncherApps launcherApps;

    private int dp(float value) {
        return (int) (
            value *
            getResources().getDisplayMetrics().density
            + 0.5f
        );
    }

    private void hideSystemUI() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);

            WindowInsetsController c =
                    getWindow().getInsetsController();

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
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        hideSystemUI();

        launcherApps =
            (LauncherApps)
                getSystemService(Context.LAUNCHER_APPS_SERVICE);

        List<LauncherActivityInfo> found =
            launcherApps.getActivityList(
                null,
                Process.myUserHandle()
            );

        if (found != null) {
            for (LauncherActivityInfo info : found) {

                if (!getPackageName().equals(
                        info.getApplicationInfo().packageName)) {

                    apps.add(info);
                }
            }
        }

        final Collator collator =
                Collator.getInstance();

        Collections.sort(
            apps,
            (a, b) -> collator.compare(
                a.getLabel().toString(),
                b.getLabel().toString()
            )
        );

        ListView list = new ListView(this);

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
            public LauncherActivityInfo getItem(int position) {
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

                TextView text;

                if (convertView instanceof TextView) {
                    text = (TextView) convertView;

                } else {
                    text =
                        new TextView(DrawerActivity.this);

                    text.setTextColor(Color.WHITE);
                    text.setTextSize(16);
                    text.setGravity(Gravity.CENTER_VERTICAL);
                    text.setSingleLine(true);

                    text.setPadding(
                        dp(20),
                        0,
                        dp(12),
                        0
                    );

                    text.setMinHeight(dp(52));
                    text.setBackgroundColor(Color.BLACK);
                }

                text.setText(
                    apps.get(position).getLabel()
                );

                return text;
            }
        });

        list.setOnItemClickListener(
            (parent, view, position, id) -> {

                LauncherActivityInfo info =
                        apps.get(position);

                try {
                    launcherApps.startMainActivity(
                        info.getComponentName(),
                        info.getUser(),
                        null,
                        null
                    );

                } catch (Throwable ignored) {
                    return;
                }

                finishAndRemoveTask();

                /*
                 * Drawer is isolated in :drawer.
                 * NullHome HOME remains alive.
                 */
                Process.killProcess(Process.myPid());
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
}
