package dev.nullhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public final class TopEdgeGuardService extends AccessibilityService {

    private static final String TAG = "NullHomeGuard";
    private static final String BUILD = "top-edge-guard-v1";

    private WindowManager wm;
    private View guard;

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        );

        if (id != 0) {
            try {
                return getResources().getDimensionPixelSize(id);
            } catch (Throwable ignored) {
            }
        }

        return dp(24);
    }

    private void installGuard() {
        if (guard != null) {
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (wm == null) {
            Log.e(TAG, BUILD + " no WindowManager");
            return;
        }

        final int h = Math.max(statusBarHeight(), dp(24));

        View v = new View(this);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setClickable(true);
        v.setFocusable(false);
        v.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        );

        v.setOnTouchListener((view, event) -> {
            final int action = event.getActionMasked();

            if (
                action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
            ) {
                return true;
            }

            return true;
        });

        WindowManager.LayoutParams lp =
            new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            );

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 0;
        lp.setTitle("NullHomeTopEdgeGuard");

        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
            lp.setFitInsetsSides(0);
        }

        try {
            wm.addView(v, lp);
            guard = v;

            Log.i(
                TAG,
                "build=" + BUILD
                    + " installed height=" + h
            );
        } catch (Throwable t) {
            Log.e(TAG, BUILD + " addView failed", t);
        }
    }

    private void removeGuard() {
        View v = guard;
        guard = null;

        if (v == null || wm == null) {
            return;
        }

        try {
            wm.removeViewImmediate(v);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        try {
            AccessibilityServiceInfo info = getServiceInfo();

            if (info != null) {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
                info.notificationTimeout = 0;
                info.flags = 0;
                setServiceInfo(info);
            }
        } catch (Throwable t) {
            Log.w(TAG, BUILD + " setServiceInfo failed", t);
        }

        installGuard();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        /*
         * Intentionally empty. This service does not inspect app contents.
         * Its only job is to own a trusted accessibility overlay along the
         * top edge and consume the initial touch stream used to pull down
         * the notification shade.
         */
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        removeGuard();
        super.onDestroy();
    }
}
