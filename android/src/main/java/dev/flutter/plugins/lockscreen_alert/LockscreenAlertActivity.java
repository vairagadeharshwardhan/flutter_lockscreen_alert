package dev.flutter.plugins.lockscreen_alert;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineGroup;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.plugin.common.MethodChannel;

import io.flutter.FlutterInjector;

/** Activity shown on the lock screen; embeds Flutter UI with the alert payload. */
public class LockscreenAlertActivity extends FlutterActivity {

    static final String EXTRA_ALERT_ID = "alert_id";

    private int alertId;
    private FlutterEngine lockScreenEngine;
    private MethodChannel alertChannel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        super.onCreate(savedInstanceState);

        alertId = getIntent().getIntExtra(EXTRA_ALERT_ID, -1);
        if (alertId == -1) {
            finish();
            return;
        }
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        alertChannel = new MethodChannel(
                flutterEngine.getDartExecutor(),
                LockscreenAlertConstants.CHANNEL);
        alertChannel.setMethodCallHandler((call, result) -> {
            if ("getPayload".equals(call.method)) {
                Map<String, Object> payload = FlutterLockscreenAlertPlugin.getPayload(alertId);
                result.success(payload);
            } else if ("notifyDismissed".equals(call.method)) {
                onUserDismissed();
                result.success(null);
            } else if ("notifyAccepted".equals(call.method)) {
                onUserAccepted();
                result.success(null);
            } else {
                result.notImplemented();
            }
        });
    }

    @NonNull
    @Override
    protected FlutterEngine provideFlutterEngine(@NonNull Context context) {
        FlutterEngineGroup group = new FlutterEngineGroup(context);
        DartExecutor.DartEntrypoint entrypoint = new DartExecutor.DartEntrypoint(
                FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                LockscreenAlertConstants.ENTRYPOINT);
        lockScreenEngine = group.createAndRunEngine(context, entrypoint);
        return lockScreenEngine;
    }

    private void onUserDismissed() {
        FlutterLockscreenAlertPlugin.notifyMainApp("dismissed", null);
        finishAndCleanup();
    }

    private void onUserAccepted() {
        Map<String, Object> payload = FlutterLockscreenAlertPlugin.getPayload(alertId);
        FlutterLockscreenAlertPlugin.notifyMainApp("accepted", payload);
        finishAndCleanup();
    }

    private void finishAndCleanup() {
        FlutterLockscreenAlertPlugin.removePayload(alertId);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(alertId);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (alertChannel != null) {
            alertChannel.setMethodCallHandler(null);
        }
        super.onDestroy();
    }
}
