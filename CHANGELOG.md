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
