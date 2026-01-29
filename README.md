# flutter_lockscreen_alert

A Flutter plugin to show **critical alert UI on the device lock screen** using Android full-screen intents. Use it for incoming calls, bookings, or any time-sensitive alert that must be visible and interactive when the screen is locked.

| Platform | Status |
|----------|--------|
| Android  | ✅ Full-screen intent + lock-screen Activity |
| iOS      | ⏳ Not implemented (CallKit recommended)     |

## Features

- **Lock-screen UI**: Your Flutter widget is shown over the lock screen when the device is locked.
- **Generic API**: Pass any `Map<String, dynamic>` payload (e.g. booking id, from/to, fare) and build your own UI in Dart.
- **Full-screen intent**: Uses Android’s recommended path for critical alerts (notification with `fullScreenIntent`).
- **Stream of actions**: Listen to `LockscreenAlert.onAction` for `dismissed` / `accepted` from your lock-screen UI.

## Setup

### 1. Dependencies

```yaml
dependencies:
  flutter_lockscreen_alert: ^1.0.0
```

### 2. Android

- **Min SDK**: 21  
- **Permissions**: The plugin declares `USE_FULL_SCREEN_INTENT` and `POST_NOTIFICATIONS` (API 33+). On Android 13+, ensure your app requests notification permission if needed.
- **Full-screen intent**: On some devices the user must allow “full screen notifications” for your app (e.g. in Settings → Apps → Your app → Notifications).

### 3. Register the lock-screen entrypoint

Your app must provide a **top-level entrypoint** that Flutter will run when the lock-screen UI is shown. In your `main.dart` (or a file imported by it), add:

```dart
import 'package:flutter_lockscreen_alert/flutter_lockscreen_alert.dart';

@pragma('vm:entry-point')
void lockscreenAlertMain() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Optional: init your app (e.g. theme, localization)
  runApp(MyLockscreenAlertApp());
}

class MyLockscreenAlertApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: LockscreenAlertScreen(),
    );
  }
}

class LockscreenAlertScreen extends StatefulWidget {
  @override
  State<LockscreenAlertScreen> createState() => _LockscreenAlertScreenState();
}

class _LockscreenAlertScreenState extends State<LockscreenAlertScreen> {
  Map<String, dynamic>? _payload;

  @override
  void initState() {
    super.initState();
    _loadPayload();
  }

  Future<void> _loadPayload() async {
    final payload = await LockscreenAlert.getPayload();
    if (mounted) setState(() => _payload = payload);
  }

  @override
  Widget build(BuildContext context) {
    if (_payload == null) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Text('Alert: ${_payload!['title']}'),
            ElevatedButton(
              onPressed: () async {
                await LockscreenAlert.notifyAccepted();
              },
              child: Text('Accept'),
            ),
            TextButton(
              onPressed: () async {
                await LockscreenAlert.notifyDismissed();
              },
              child: Text('Dismiss'),
            ),
          ],
        ),
      ),
    );
  }
}
```

You can replace `LockscreenAlertScreen` with your own widget (e.g. your booking card).

## Usage

### Show an alert on the lock screen

```dart
final id = await LockscreenAlert.show(
  payload: {
    'id': 'booking_123',
    'from': 'Airport',
    'to': 'Downtown',
    'fare': '\$25',
  },
  notificationTitle: 'New booking',
  notificationBody: 'Tap to view',
);
```

### Listen for user actions

```dart
LockscreenAlert.onAction.listen((event) {
  if (event.action == 'accepted') {
    // Open app and navigate to booking, e.g. event.data
  } else if (event.action == 'dismissed') {
    // User closed the alert
  }
});
```

### Dismiss programmatically

```dart
await LockscreenAlert.dismiss(id: id);
```

### Check support

```dart
final supported = await LockscreenAlert.isSupported();
```

## Custom entrypoint name

If you prefer a different entrypoint than `lockscreenAlertMain`, set it before calling `show`:

```dart
LockscreenAlert.entrypoint = 'myCustomEntrypoint';
```

and register that name in your Dart entrypoint (e.g. in `main.dart`):

```dart
@pragma('vm:entry-point')
void myCustomEntrypoint() { ... }
```

You must also pass this name from the host app to the plugin (the plugin’s default is `lockscreenAlertMain`). The Android side currently uses the constant `lockscreenAlertMain`; to use a custom name you’d need to pass it via a plugin method (e.g. `setEntrypoint`) and have the native side use it when starting the engine. For 1.0 we keep the single default entrypoint.

## iOS

This plugin does not implement iOS. For a similar experience on iOS you would use **CallKit** (and optionally PushKit). We may add an iOS implementation in a future release.

## Publishing to pub.dev

1. From the package directory run `dart pub publish --dry-run`, then fix any issues.
2. Run `dart pub publish` when ready.

## License

Apache License 2.0. See [LICENSE](LICENSE).
