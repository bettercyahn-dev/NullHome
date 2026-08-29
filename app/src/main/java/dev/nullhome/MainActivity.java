package dev.nullhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public final class MainActivity extends Activity {

    private float downX;
    private float downY;

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

    private void openSettingsFallback() {
        try {
            Intent settings = new Intent(Settings.ACTION_SETTINGS);
            startActivity(settings);
        } catch (Throwable ignored) {
        }
    }

    private void openDrawer() {
        try {
            Intent i = new Intent(this, DrawerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            /*
             * Keep DrawerActivity in the same HOME task.
             * This avoids the previous extra-task/process lifecycle race.
             */
            startActivity(i);
            overridePendingTransition(0, 0);

        } catch (Throwable failure) {
            openSettingsFallback();
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );

        View root = new View(this);
        root.setBackgroundColor(Color.BLACK);

        root.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    return true;

                case MotionEvent.ACTION_UP: {
                    float dx = event.getX() - downX;
                    float upward = downY - event.getY();

                    if (
                        upward >= dp(48)
                        && upward > Math.abs(dx) * 1.15f
                    ) {
                        openDrawer();
                    }

                    return true;
                }

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }

            return true;
        });

        setContentView(root);
        hideSystemUI();
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
