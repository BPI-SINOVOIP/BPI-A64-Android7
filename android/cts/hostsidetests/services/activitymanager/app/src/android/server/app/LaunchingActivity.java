package android.server.app;

import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;

/**
 * Activity that launches another activities when new intent is received.
 */
public class LaunchingActivity extends Activity {
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        Intent newIntent = new Intent();
        String targetActivity = extras.getString("target_activity");
        if (targetActivity != null) {
            String packageName = getApplicationContext().getPackageName();
            newIntent.setComponent(new ComponentName(packageName,
                    packageName + "." + targetActivity));
        } else {
            newIntent.setClass(this, TestActivity.class);
        }

        if (extras.getBoolean("launch_to_the_side")) {
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT);
            if (extras.getBoolean("multiple_task")) {
                newIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
            }
            if (extras.getBoolean("random_data")) {
                Uri data = new Uri.Builder()
                        .path(String.valueOf(System.currentTimeMillis()))
                        .build();
                newIntent.setData(data);
            }
        } else {
            // We're all set, just launch.
        }

        startActivity(newIntent);
    }
}
