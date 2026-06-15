## 1.0.12

* **Dart:** Added optional `notificationOnly` parameter to `show()`. When `true`, only the notification is posted and the full-screen Activity is not launched (user must tap notification to open). When `false` (default), the plugin posts the notification and launches the full-screen Activity so the booking card appears on the lock screen immediately. Use `notificationOnly: true` when the app was in foreground and the user locked the screen; use default when the app is in background and device is locked.
* **Android:** `handleShow()` reads `notificationOnly`; when true, skips `startActivity()` after posting the notification.

## 1.0.11

* **Android:** When the user unlocks (e.g. Face ID) while the lock-screen booking Activity is visible, the plugin now sets `flutter.show_booking_overlay_on_launch` in SharedPreferences and launches the main app with `FLAG_ACTIVITY_CLEAR_TOP`, so the host app can show the regular overlay instead of leaving the user on the lock-screen UI.

## 1.0.10

* **Android:** Pass payload in the full-screen Intent as JSON (`EXTRA_PAYLOAD_JSON`) so the lock-screen Activity has booking data when started in a new process (e.g. user taps notification after app was killed). This fixes the "only loading indicator" issue where `getPayload()` returned null and the booking card never appeared.
* **Android:** Restore payload from Intent in `LockscreenAlertActivity` before `super.onCreate()` so the payload is available when the Flutter engine first calls `getPayload()`.
* **Android:** Always attempt to start the full-screen Activity when `show()` is called (not only when `device_locked` is true), so the booking card shows immediately when possible (e.g. app in foreground or right after lock).

## 1.0.9

* **Android:** When the device is locked (`device_locked` in SharedPreferences), launch the full-screen Activity directly after posting the notification so the booking card appears immediately (like a WhatsApp call) without the user tapping the notification.

## 1.0.8

* **Android:** Register a `BroadcastReceiver` for `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT` in the plugin. Writes `device_locked` (true/false) to Flutter SharedPreferences (`FlutterSharedPreferences`, key `flutter.device_locked`) so the host app's background service can show lock-screen UI when the device is locked and overlay when unlocked, even if the user locked the device after the app went to background.

## 1.0.7

* **Android:** Fix Java compilation: rename duplicate variable `flags` to `wakeLockFlags` in `handleShow()`.
* **Android:** Fix lambda capture: use final reference for WakeLock in `Handler.postDelayed` callback.

## 1.0.6

* **Android:** Set SharedPreferences key `flutter_lockscreen_alert_activity_visible` (in FlutterSharedPreferences) when the lock-screen Activity is shown and clear it when destroyed, so the host app can close its overlay and avoid showing two booking cards (overlay + lock-screen UI).
* **Android:** Acquire a brief WakeLock when posting the full-screen notification so the screen starts waking before the activity launches.

## 1.0.5

* **Android:** Acquire `WakeLock` (SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP) when the lock-screen Activity is shown so the screen fully wakes on OEMs (e.g. Xiaomi/MIUI) that otherwise only show a brief "breath" or edge light.
* **Android:** Add `FLAG_KEEP_SCREEN_ON` and window flags for show-when-locked / turn-screen-on so the screen stays on while the booking UI is visible.
* **Android:** Use an opaque dark window background and fullscreen theme so the Activity is visible on lock screen; transparent window could result in no visible UI on some devices.

## 1.0.4

* **Android:** Fix Java compilation when host app uses compileSdk &lt; 27: use literal flag values for show-when-locked and turn-screen-on instead of `Intent` constants that were added in API 27.

## 1.0.3

* **Android:** Cancel the notification in `LockscreenAlertActivity.onStart()` so the user sees only the full-screen booking UI and not a notification tile when the lock screen wakes.
* **Android:** Use `NotificationManager.IMPORTANCE_MAX` for the alert channel so the full-screen intent fires immediately (screen wake, activity launch).
* **Android:** Add `FLAG_ACTIVITY_SHOW_WHEN_LOCKED` and `FLAG_ACTIVITY_TURN_SCREEN_ON` to the full-screen intent (API 27+) so the screen wakes and the activity shows over the lock screen without requiring a tap on a notification.

## 1.0.2

* **Android:** Fix resource linking by removing `windowShowWhenLocked` and `windowTurnScreenOn` from theme XML entirely; the Activity already sets these programmatically in `onCreate()` (API 27+). Fixes "style attribute not found" when merging resources.
* **Android:** Fix Java compilation: `provideFlutterEngine(Context)` is now `public` to match the Flutter embedding `Host` interface.

## 1.0.1

* **Android:** Fix resource linking error when app `minSdkVersion` is below 27. Moved `windowShowWhenLocked` and `windowTurnScreenOn` into `values-v27/styles.xml` so the base theme does not reference API-27-only attributes. Lock-screen behaviour unchanged on API 27+.

## 1.0.0

* Initial release.
* Android: full-screen intent notification and lock-screen Activity with Flutter UI.
* Dart API: `show`, `dismiss`, `getPayload`, `notifyDismissed`, `notifyAccepted`, `onAction`, `isSupported`.
* iOS: not implemented.
