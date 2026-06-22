import 'dart:async';

import 'package:flutter/services.dart';

/// Default Dart entrypoint name for the lock-screen alert UI.
///
/// Your app must register this entrypoint (e.g. in [main.dart]) with
/// `@pragma('vm:entry-point')` and run a [WidgetsApp] that builds your
/// alert UI. The payload is available via [LockscreenAlert.getPayload].
const String kLockscreenAlertEntrypoint = 'lockscreenAlertMain';

/// Flutter plugin for showing critical alert UI on the lock screen.
///
/// On Android, uses [full-screen intents](https://developer.android.com/develop/ui/views/notifications/full-screen-intent)
/// so your Flutter UI appears over the lock screen when the device is locked.
/// Requires [USE_FULL_SCREEN_INTENT] and (on API 33+) [POST_NOTIFICATIONS].
///
/// ## Setup
///
/// 1. Add a top-level entrypoint in your app (e.g. in [main.dart]):
///
/// ```dart
/// @pragma('vm:entry-point')
/// void lockscreenAlertMain() {
///   WidgetsFlutterBinding.ensureInitialized();
///   runApp(MyLockscreenAlertApp()); // build your UI using LockscreenAlert.getPayload()
/// }
/// ```
///
/// 2. Call [show] when you need to display the alert (e.g. on new booking):
///
/// ```dart
/// LockscreenAlert.show(
///   payload: {'id': '123', 'from': 'A', 'to': 'B'},
///   notificationTitle: 'New booking',
///   notificationBody: 'Tap to view',
/// );
/// ```
///
/// 3. Listen to [onAction] to handle dismiss/accept from the lock-screen UI.
class LockscreenAlert {
  LockscreenAlert._();

  static const MethodChannel _channel =
      MethodChannel('dev.flutter.plugins.lockscreen_alert/channel');

  static final StreamController<LockscreenAlertAction> _actionController =
      StreamController<LockscreenAlertAction>.broadcast();

  static bool _handlerSet = false;

  static void Function(Map<String, dynamic> payload)? _onShow;
  static void Function()? _onReset;

  /// Set by your lock-screen entrypoint. Called (native→Dart) when a real alert
  /// must be shown on a PRE-WARMED engine that has been idling — load the
  /// payload, render the card, start sound. On the cold path the engine starts
  /// with the payload already available via [getPayload], so guard for both.
  static set onShow(void Function(Map<String, dynamic> payload)? cb) {
    _onShow = cb;
    _ensureHandler();
  }

  /// Set by your lock-screen entrypoint. Called (native→Dart) just before the
  /// Activity finishes so a REUSED cached engine returns to its idle/blank state.
  static set onReset(void Function()? cb) {
    _onReset = cb;
    _ensureHandler();
  }

  static void _ensureHandler() {
    if (_handlerSet) return;
    _handlerSet = true;
    _channel.setMethodCallHandler((MethodCall call) async {
      if (call.method == 'onAction') {
        final String? action = call.arguments is Map
            ? (call.arguments as Map)['action'] as String?
            : null;
        final Map<String, dynamic>? data = call.arguments is Map
            ? (call.arguments as Map)['data'] as Map<String, dynamic>?
            : null;
        if (action != null) {
          LockscreenAlert._handleAction(
            action,
            data != null ? Map<String, dynamic>.from(data) : null,
          );
        }
      } else if (call.method == 'onShow') {
        final a = call.arguments;
        final map = a is Map
            ? Map<String, dynamic>.from(
                a.map((k, v) => MapEntry(k.toString(), v)))
            : <String, dynamic>{};
        _onShow?.call(map);
      } else if (call.method == 'onReset') {
        _onReset?.call();
      }
      return null;
    });
  }

  /// Stream of user actions from the lock-screen alert (e.g. dismissed, accepted).
  static Stream<LockscreenAlertAction> get onAction {
    _ensureHandler();
    return _actionController.stream;
  }

  /// Optional: set the Dart entrypoint name (default [kLockscreenAlertEntrypoint]).
  /// Must be called before [show] if you use a custom entrypoint.
  static String entrypoint = kLockscreenAlertEntrypoint;

  /// Shows the lock-screen alert with [payload].
  ///
  /// [payload] is passed to your Dart entrypoint and can be read with [getPayload].
  /// [notificationTitle] and [notificationBody] are used for the system
  /// notification that triggers the full-screen intent (visible in shade).
  ///
  /// When [notificationOnly] is true, only the notification is posted; the
  /// full-screen Activity is not launched (user must tap notification to open).
  /// Use true when the app was in foreground and the user locked the screen.
  /// Use false (default) when the app is in background and device is locked,
  /// so the booking card shows automatically on the lock screen.
  ///
  /// Returns the notification/alert id, or null on failure.
  static Future<int?> show({
    required Map<String, dynamic> payload,
    String? notificationTitle,
    String? notificationBody,
    String? notificationChannelId,
    String? notificationChannelName,
    bool notificationOnly = false,
  }) async {
    try {
      final id = await _channel.invokeMethod<int?>('show', <String, dynamic>{
        'payload': payload,
        'notificationTitle': notificationTitle ?? 'Alert',
        'notificationBody': notificationBody ?? 'Tap to open',
        'notificationChannelId': notificationChannelId ?? 'lockscreen_alert',
        'notificationChannelName':
            notificationChannelName ?? 'Critical Alerts',
        'notificationOnly': notificationOnly,
      });
      return id;
    } on PlatformException catch (_) {
      return null;
    }
  }

  /// Dismisses the lock-screen alert (and its notification) by [id].
  /// If [id] is null, dismisses the most recently shown alert.
  static Future<bool> dismiss({int? id}) async {
    try {
      final result = await _channel.invokeMethod<bool>('dismiss', {'id': id});
      return result == true;
    } on PlatformException {
      return false;
    }
  }

  /// Call this from your lock-screen entrypoint to get the payload passed to [show].
  /// Returns null if not running in the lock-screen context.
  static Future<Map<String, dynamic>?> getPayload() async {
    try {
      final result = await _channel.invokeMethod<Map<Object?, Object?>>('getPayload');
      if (result == null) return null;
      return Map<String, dynamic>.from(
        result.map((k, v) => MapEntry(k as String, v)),
      );
    } on PlatformException {
      return null;
    }
  }

  /// Call from your lock-screen UI when the user dismisses the alert (e.g. close button).
  static Future<void> notifyDismissed() async {
    try {
      await _channel.invokeMethod('notifyDismissed');
    } on PlatformException {
      // ignore
    }
  }

  /// Call from your lock-screen UI when the user accepts (e.g. swipe to accept).
  static Future<void> notifyAccepted() async {
    try {
      await _channel.invokeMethod('notifyAccepted');
    } on PlatformException {
      // ignore
    }
  }

  /// Android 14+ (API 34): whether the app may currently fire full-screen
  /// intents. On API 34+ this is a user-grantable permission that is NOT
  /// auto-granted to non-call/alarm apps — if it returns false, the lock-screen
  /// takeover silently demotes to a heads-up notification. Returns true on
  /// older Android and non-Android platforms.
  static Future<bool> canUseFullScreenIntent() async {
    try {
      final r = await _channel.invokeMethod<bool>('canUseFullScreenIntent');
      return r ?? true;
    } on PlatformException {
      return true;
    }
  }

  /// LIVE keyguard state — the authoritative source for deciding "lock-screen
  /// full-screen alert vs over-apps overlay" when a booking arrives. Prefer this
  /// over the cached `device_locked` SharedPreferences flag, which can get stuck
  /// `true` on devices that never broadcast `ACTION_USER_PRESENT` (no secure
  /// lock / OEM quirk) — causing the FSI to fire even while the phone is
  /// unlocked. Returns false on failure / non-Android.
  static Future<bool> isDeviceLocked() async {
    try {
      final r = await _channel.invokeMethod<bool>('isDeviceLocked');
      return r ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Opens the Android 14+ "Full-screen notifications" settings screen for this
  /// app so the user can grant the permission. Call this from onboarding when
  /// [canUseFullScreenIntent] is false. No-op (returns true) below API 34.
  static Future<bool> requestFullScreenIntentPermission() async {
    try {
      final r = await _channel
          .invokeMethod<bool>('requestFullScreenIntentPermission');
      return r ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Whether the platform supports lock-screen alerts (Android with full-screen intent).
  static Future<bool> isSupported() async {
    try {
      final result = await _channel.invokeMethod<bool>('isSupported');
      return result == true;
    } on PlatformException {
      return false;
    }
  }

  /// Pre-warm and cache the lock-screen Flutter engine NOW (e.g. the moment the
  /// driver goes online) so a later alert attaches an already-warm engine and
  /// paints in well under a second, instead of cold-starting a fresh engine +
  /// registering every plugin (~5 s) at the instant the booking arrives.
  /// Idempotent and safe to call repeatedly. Android only; no-op elsewhere.
  static Future<void> warmUp() async {
    try {
      await _channel.invokeMethod('warmUp');
    } on PlatformException {
      // ignore
    }
  }

  /// Disposes the action stream. Call when your app is shutting down.
  static void dispose() {
    _actionController.close();
  }

  /// Internal: called by native code to push actions to [onAction].
  static void _handleAction(String action, Map<Object?, Object?>? data) {
    if (!_actionController.isClosed) {
      _actionController.add(LockscreenAlertAction(
        action: action,
        data: data != null
            ? Map<String, dynamic>.from(
                data.map((k, v) => MapEntry(k as String, v)))
            : null,
      ));
    }
  }
}

/// User action from the lock-screen alert.
class LockscreenAlertAction {
  const LockscreenAlertAction({required this.action, this.data});

  final String action; // 'dismissed' | 'accepted'
  final Map<String, dynamic>? data;
}
