/// Payload passed to the lock-screen alert UI.
///
/// Use [LockscreenAlert.show] with a [Map] that will be serialized and
/// passed to your Dart entrypoint (e.g. booking data, call info).
@Deprecated('Use Map<String, dynamic> directly with LockscreenAlert.show()')
class LockscreenAlertPayload {
  const LockscreenAlertPayload({required this.data, this.title, this.body});

  final Map<String, dynamic> data;
  final String? title;
  final String? body;
}
