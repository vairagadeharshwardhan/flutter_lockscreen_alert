package dev.flutter.plugins.lockscreen_alert;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** Flutter plugin for showing critical alert UI on the lock screen. */
public class FlutterLockscreenAlertPlugin implements FlutterPlugin, MethodCallHandler {

    private static final String TAG = "LockscreenAlert";

    private Context applicationContext;
    private MethodChannel channel;
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
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        mainAppMessenger = null;
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
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        fullScreenIntent.putExtra(LockscreenAlertActivity.EXTRA_ALERT_ID, id);

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
        try {
            nm.notify(id, builder.build());
            result.success(id);
        } catch (SecurityException e) {
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
        result.success(true);
    }

    private void createChannel(String channelId, String channelName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager)
                    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
