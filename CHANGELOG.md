## 1.0.1

* **Android:** Fix resource linking error when app `minSdkVersion` is below 27. Moved `windowShowWhenLocked` and `windowTurnScreenOn` into `values-v27/styles.xml` so the base theme does not reference API-27-only attributes. Lock-screen behaviour unchanged on API 27+.

## 1.0.0

* Initial release.
* Android: full-screen intent notification and lock-screen Activity with Flutter UI.
* Dart API: `show`, `dismiss`, `getPayload`, `notifyDismissed`, `notifyAccepted`, `onAction`, `isSupported`.
* iOS: not implemented.
