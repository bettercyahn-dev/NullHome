package dev.nullhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

public final class RecentsBridgeActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            Intent i = new Intent();
            i.setComponent(
                new ComponentName(
                    "com.sec.android.app.launcher",
                    "com.android.quickstep.RecentsActivity"
                )
            );

            i.addFlags(
                Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            );

            startActivity(i);
            overridePendingTransition(0, 0);

        } catch (Throwable ignored) {
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
