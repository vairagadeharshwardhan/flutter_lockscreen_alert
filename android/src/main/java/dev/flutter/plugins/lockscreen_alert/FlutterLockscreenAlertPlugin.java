package dev.flutter.plugins.lockscreen_alert;

import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.FlutterEngineGroup;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** Flutter plugin for showing critical alert UI on the lock screen. */
public class FlutterLockscreenAlertPlugin implements FlutterPlugin, MethodCallHandler {

    private static final String TAG = "LockscreenAlert";
    /** Same SharedPreferences name and key prefix as Flutter shared_preferences plugin. */
    private static final String PREFS_NAME = "FlutterSharedPreferences";
    private static final String KEY_DEVICE_LOCKED = "flutter.device_locked";

    private Context applicationContext;
    private MethodChannel channel;
    private BroadcastReceiver lockStateReceiver;
    private static io.flutter.plugin.common.BinaryMessenger mainAppMessenger;
    private static int nextNotificationId = LockscreenAlertConstants.NOTIFICATION_ID_BASE;

    /** Payloads for active alerts, keyed by notification id. */
    static final Map<Integer, Map<String, Object>> payloads = new HashMap<>();

    static int storePayload(Map<String, Object> payload) {
        int id = nextNotificationId++;
        payloads.put(id, payload);
        return id;
    }

    static Map<String, Object> getPayload(int id) {
        return payloads.get(id);
    }

    static void removePayload(int id) {
        payloads.remove(id);
    }

    /** Notify the main app of an action (dismissed/accepted). */
    static void notifyMainApp(String action, Map<String, Object> data) {
        if (mainAppMessenger == null) return;
        MethodChannel ch = new MethodChannel(mainAppMessenger, LockscreenAlertConstants.CHANNEL);
        Map<String, Object> args = new HashMap<>();
        args.put("action", action);
        args.put("data", data);
        ch.invokeMethod("onAction", args, null);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        applicationContext = binding.getApplicationContext();
        mainAppMessenger = binding.getBinaryMessenger();
        channel = new MethodChannel(binding.getBinaryMessenger(), LockscreenAlertConstants.CHANNEL);
        channel.setMethodCallHandler(this);
        registerLockStateReceiver();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        unregisterLockStateReceiver();
        channel.setMethodCallHandler(null);
        mainAppMessenger = null;
    }

    private static final String KEY_ACTIVITY_VISIBLE = "flutter_lockscreen_alert_activity_visible";
    private static final String KEY_SHOW_BOOKING_OVERLAY_ON_LAUNCH = "flutter.show_booking_overlay_on_launch";

    /** Updates device_locked in Flutter SharedPreferences so background service knows lock state. */
    private void registerLockStateReceiver() {
        lockStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (context == null || intent == null) return;
                String action = intent.getAction();
                if (action == null) return;
                // SCREEN_OFF → locked. SCREEN_ON / USER_PRESENT → trust the LIVE
                // keyguard, so the flag flips back to false even on devices that
                // never broadcast ACTION_USER_PRESENT (no secure lock / OEM quirk)
                // instead of being stuck `true` and forcing FSI while unlocked.
                final boolean locked = Intent.ACTION_SCREEN_OFF.equals(action)
                        ? true
                        : isDeviceLockedLive();
                try {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_DEVICE_LOCKED, locked)
                            .apply();
                    Log.d(TAG, "device_locked=" + locked + " (" + action + ")");
                    // When user unlocks (e.g. Face ID) while lock-screen booking is visible, bring main app to front
                    // with overlay so they see the regular overlay instead of staying on lock-screen UI.
                    if (Intent.ACTION_USER_PRESENT.equals(action)) {
                        boolean activityVisible = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getBoolean(KEY_ACTIVITY_VISIBLE, false);
                        if (activityVisible) {
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean(KEY_SHOW_BOOKING_OVERLAY_ON_LAUNCH, true)
                                    .apply();
                            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                context.startActivity(launch);
                                Log.d(TAG, "USER_PRESENT: launched main app with CLEAR_TOP for overlay");
                            }
                            // Cleanly finish + RESET the live lock-screen alert now that
                            // we're unlocked and handing off to the in-app overlay. This
                            // invokes onReset on the cached engine (stops the looping FSI
                            // sound) while the Activity is still alive — so the alert sound
                            // does NOT keep playing in the cached lock-screen isolate and
                            // overlap the over-apps overlay's own sound (the "double
                            // sound"). No-op if nothing is live; the Activity's onDestroy
                            // failsafe also resets if the OS reclaimed it first.
                            LockscreenAlertActivity.finishLiveInstanceFromHost();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write device_locked or launch main app", e);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.registerReceiver(lockStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            applicationContext.registerReceiver(lockStateReceiver, filter);
        }
        Log.d(TAG, "LockStateReceiver registered");
    }

    private void unregisterLockStateReceiver() {
        if (lockStateReceiver != null) {
            try {
                applicationContext.unregisterReceiver(lockStateReceiver);
            } catch (Exception ignored) { }
            lockStateReceiver = null;
            Log.d(TAG, "LockStateReceiver unregistered");
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "show":
                handleShow(call, result);
                break;
            case "dismiss":
                handleDismiss(call, result);
                break;
            case "isSupported":
                result.success(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                break;
            case "canUseFullScreenIntent":
                result.success(canUseFullScreenIntent());
                break;
            case "isDeviceLocked":
                result.success(isDeviceLockedLive());
                break;
            case "requestFullScreenIntentPermission":
                result.success(requestFullScreenIntentPermission());
                break;
            case "warmUp":
                handleWarmUp(result);
                break;
            default:
                result.notImplemented();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleShow(MethodCall call, Result result) {
        Map<String, Object> args = call.arguments();
        if (args == null) {
            result.error("INVALID_ARGS", "payload required", null);
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) args.get("payload");
        if (payload == null) {
            result.error("INVALID_ARGS", "payload required", null);
            return;
        }
        String title = (String) args.get("notificationTitle");
        String body = (String) args.get("notificationBody");
        String channelId = (String) args.get("notificationChannelId");
        String channelName = (String) args.get("notificationChannelName");
        Object notificationOnlyObj = args.get("notificationOnly");
        boolean notificationOnly = Boolean.TRUE.equals(notificationOnlyObj);
        if (title == null) title = "Alert";
        if (body == null) body = "Tap to open";
        if (channelId == null) channelId = LockscreenAlertConstants.CHANNEL_ID_DEFAULT;
        if (channelName == null) channelName = "Critical Alerts";

        int id = storePayload(payload);
        createChannel(channelId, channelName);
        NotificationManager nm = (NotificationManager)
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            removePayload(id);
            result.error("SERVICE", "NotificationManager null", null);
            return;
        }

        Intent fullScreenIntent = new Intent(applicationContext, LockscreenAlertActivity.class);
        int intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            intentFlags |= 0x00080000; // FLAG_ACTIVITY_SHOW_WHEN_LOCKED (API 27)
            intentFlags |= 0x00040000; // FLAG_ACTIVITY_TURN_SCREEN_ON (API 27)
        }
        fullScreenIntent.setFlags(intentFlags);
        fullScreenIntent.putExtra(LockscreenAlertActivity.EXTRA_ALERT_ID, id);
        // Pass payload in Intent so the Activity has it when started in a new process (e.g. user taps notification after app was killed).
        String payloadJson = payloadToJson(payload);
        if (payloadJson != null) {
            fullScreenIntent.putExtra(LockscreenAlertActivity.EXTRA_PAYLOAD_JSON, payloadJson);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                applicationContext, id, fullScreenIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setContentIntent(fullScreenPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setTimeoutAfter(0);
        }
        PowerManager.WakeLock briefWakeLock = null;
        try {
            PowerManager pm = (PowerManager) applicationContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                int wakeLockFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                briefWakeLock = pm.newWakeLock(wakeLockFlags, "flutter_lockscreen_alert:show");
                briefWakeLock.acquire(3000); // 3 s max; activity will acquire its own
            }
        } catch (Exception ignored) { }
        try {
            nm.notify(id, builder.build());
            // When notificationOnly is false (e.g. app in background, device locked), launch the
            // full-screen Activity immediately so the booking card shows on the lock screen without a tap.
            // When notificationOnly is true (app was in foreground, user locked), only the notification
            // is shown; user taps to open.
            if (!notificationOnly) {
                try {
                    applicationContext.startActivity(fullScreenIntent);
                } catch (Exception e) {
                    Log.w(TAG, "Could not launch full-screen activity directly (user may tap notification)", e);
                }
            }
            result.success(id);
            if (briefWakeLock != null) {
                final PowerManager.WakeLock wlToRelease = briefWakeLock;
                Handler h = new Handler(Looper.getMainLooper());
                h.postDelayed(() -> {
                    try {
                        if (wlToRelease != null && wlToRelease.isHeld()) wlToRelease.release();
                    } catch (Exception ignored) { }
                }, 2500);
            }
        } catch (SecurityException e) {
            if (briefWakeLock != null && briefWakeLock.isHeld()) try { briefWakeLock.release(); } catch (Exception ignored) { }
            removePayload(id);
            Log.e(TAG, "Full-screen intent permission or notification permission denied", e);
            result.error("PERMISSION", e.getMessage(), null);
        }
    }

    private void handleDismiss(MethodCall call, Result result) {
        Integer id = call.argument("id");
        if (id != null) {
            removePayload(id);
            NotificationManager nm = (NotificationManager)
                    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(id);
        } else {
            for (Integer nid : payloads.keySet()) {
                NotificationManager nm = (NotificationManager)
                        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(nid);
            }
            payloads.clear();
        }
        // Also FINISH the live lock-screen Activity if one is showing — lets the
        // host app close the FSI on unlock and hand off cleanly to the in-app
        // overlay (no lingering card, no duplicate alert sound). No-op otherwise.
        LockscreenAlertActivity.finishLiveInstanceFromHost();
        result.success(true);
    }

    /**
     * Pre-warm the lock-screen Flutter engine and cache it so a later alert
     * attaches an already-warm engine (paints in <1s) instead of cold-starting
     * the engine + registering every plugin (~5s) at the worst possible moment.
     * Idempotent. Runs the same ENTRYPOINT; the Dart UI idles (no payload) until
     * the Activity sends "onShow". Call when the driver goes online.
     */
    private void handleWarmUp(Result result) {
        try {
            if (FlutterEngineCache.getInstance()
                    .get(LockscreenAlertConstants.CACHED_ENGINE_TAG) != null) {
                result.success(true); // already warm
                return;
            }
            FlutterEngineGroup group = new FlutterEngineGroup(applicationContext);
            DartExecutor.DartEntrypoint entrypoint = new DartExecutor.DartEntrypoint(
                    FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                    LockscreenAlertConstants.ENTRYPOINT);
            FlutterEngine engine = group.createAndRunEngine(applicationContext, entrypoint);
            FlutterEngineCache.getInstance()
                    .put(LockscreenAlertConstants.CACHED_ENGINE_TAG, engine);
            Log.d(TAG, "Lock-screen engine pre-warmed and cached");
            result.success(true);
        } catch (Exception e) {
            Log.e(TAG, "warmUp failed", e);
            result.success(false);
        }
    }

    /**
     * LIVE lock state at the moment of asking — the authoritative source for the
     * "FSI vs over-apps overlay" routing decision. The cached `device_locked`
     * flag (written from ACTION_SCREEN_OFF / ACTION_USER_PRESENT) can be stale on
     * devices that never broadcast ACTION_USER_PRESENT (no secure lock, or OEM
     * quirk), leaving it stuck `true` and routing to FSI even while unlocked.
     * KeyguardManager.isKeyguardLocked() reflects the true keyguard state now.
     */
    private boolean isDeviceLockedLive() {
        try {
            KeyguardManager km = (KeyguardManager)
                    applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Android 14 (API 34) gate: full-screen intents are no longer auto-granted to
     * non-call/alarm apps. Returns whether this app may currently fire an FSI.
     * Pre-34 it's granted by holding USE_FULL_SCREEN_INTENT in the manifest.
     */
    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager)
                    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true;
    }

    /**
     * Opens the Android 14+ "full-screen notifications" settings screen for this
     * app so the user can grant the permission. No-op (returns true) below API 34.
     */
    private boolean requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                intent.setData(Uri.parse("package:" + applicationContext.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                applicationContext.startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not open full-screen-intent settings", e);
                return false;
            }
        }
        return true;
    }

    /** Serialize payload to JSON so it can be passed in the Intent (survives process restart). */
    private static String payloadToJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            JSONObject j = new JSONObject();
            for (Map.Entry<String, Object> e : payload.entrySet()) {
                Object v = e.getValue();
                if (v == null) {
                    j.put(e.getKey(), JSONObject.NULL);
                } else {
                    j.put(e.getKey(), v);
                }
            }
            return j.toString();
        } catch (Exception e) {
            Log.e(TAG, "payloadToJson failed", e);
            return null;
        }
    }

    private void createChannel(String channelId, String channelName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_MAX);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            // Guaranteed haptic + heads-up on EVERY device — critical where the
            // FSI Activity is occluded behind the keyguard (OEM "Show on lock
            // screen" OFF), so the notification itself is the alert. IMPORTANCE_MAX
            // already carries the default sound; make the vibration explicit and
            // insistent. NOTE: a channel's importance/sound/vibration are LOCKED
            // after first creation, so this shapes FRESH installs; existing
            // installs already have a MAX channel (default sound + vibration).
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 400, 250, 400, 250, 600});
            channel.enableLights(true);
            NotificationManager nm = (NotificationManager)
                    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
