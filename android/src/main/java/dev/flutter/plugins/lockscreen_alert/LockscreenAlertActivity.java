package dev.flutter.plugins.lockscreen_alert;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
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
    /** Intent extra: payload as JSON string so the Activity has booking data when started in a new process. */
    static final String EXTRA_PAYLOAD_JSON = "payload_json";
    /** SharedPreferences key (same as Flutter shared_preferences) so host app can close overlay when this activity is shown. */
    public static final String PREFS_KEY_ACTIVITY_VISIBLE = "flutter_lockscreen_alert_activity_visible";

    private int alertId;
    private FlutterEngine lockScreenEngine;
    private MethodChannel alertChannel;
    private PowerManager.WakeLock wakeLock;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getWindow().addFlags(0x00080000); // FLAG_SHOW_WHEN_LOCKED (API 27)
            getWindow().addFlags(0x00040000); // FLAG_TURN_SCREEN_ON (API 27)
        }
        // Restore payload from Intent before super.onCreate() so it's in the map when Flutter engine calls getPayload().
        alertId = getIntent().getIntExtra(EXTRA_ALERT_ID, -1);
        if (alertId == -1) {
            finish();
            return;
        }
        String payloadJson = getIntent().getStringExtra(EXTRA_PAYLOAD_JSON);
        if (payloadJson != null) {
            Map<String, Object> restored = jsonToPayload(payloadJson);
            if (restored != null) {
                FlutterLockscreenAlertPlugin.payloads.put(alertId, restored);
            }
        }
        super.onCreate(savedInstanceState);
        acquireWakeLock();
        setActivityVisibleFlag(true);
    }

    private static Map<String, Object> jsonToPayload(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject j = new JSONObject(json);
            HashMap<String, Object> map = new HashMap<>();
            Iterator<String> keys = j.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object v = j.opt(k);
                if (v == JSONObject.NULL || v == null) {
                    map.put(k, null);
                } else {
                    map.put(k, v);
                }
            }
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private void setActivityVisibleFlag(boolean visible) {
        try {
            SharedPreferences prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
            prefs.edit().putBoolean(PREFS_KEY_ACTIVITY_VISIBLE, visible).apply();
        } catch (Exception ignored) { }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                int flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                wakeLock = pm.newWakeLock(flags, "flutter_lockscreen_alert:activity");
                wakeLock.acquire(5 * 60 * 1000L); // 5 min max; released in onDestroy
            }
        } catch (Exception ignored) { }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
        } catch (Exception ignored) { }
    }

    /**
     * Cancel the notification as soon as the full-screen UI is shown so the user
     * does not see a notification tile — only the booking card.
     */
    @Override
    protected void onStart() {
        super.onStart();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(alertId);
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
    public FlutterEngine provideFlutterEngine(@NonNull Context context) {
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
        setActivityVisibleFlag(false);
        releaseWakeLock();
        if (alertChannel != null) {
            alertChannel.setMethodCallHandler(null);
        }
        super.onDestroy();
    }
}
