# Publish flutter_lockscreen_alert to pub.dev

## 1. Prerequisites

- Flutter/Dart SDK in your PATH.
- Logged in to pub.dev: run `dart pub login` if needed (browser auth).

## 2. Validate (dry-run)

From the **package root** (this directory):

```bash
cd path/to/flutter_lockscreen_alert
dart pub publish --dry-run
```

Fix any errors or warnings before publishing.

## 3. Publish

```bash
dart pub publish
```

- Confirm the package summary when prompted (`y`).
- If the package name is taken or you don’t have permission, you’ll need to use a different name or the correct publisher account.

## 4. After publish

- Bump the Lozy app’s dependency from path to version:

  In `lozy/pubspec.yaml` change:

  ```yaml
  flutter_lockscreen_alert:
    path: ./flutter_lockscreen_alert
  ```

  to:

  ```yaml
  flutter_lockscreen_alert: ^1.0.8
  ```

- Run `flutter pub get` in the Lozy app.

## Current version

- **1.0.8** (set in `pubspec.yaml`).
- Changelog: `CHANGELOG.md` (device_locked BroadcastReceiver in 1.0.8).
