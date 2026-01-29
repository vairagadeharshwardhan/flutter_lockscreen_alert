import 'package:flutter/material.dart';
import 'package:flutter_lockscreen_alert/flutter_lockscreen_alert.dart';

void main() {
  runApp(const MyApp());
}

@pragma('vm:entry-point')
void lockscreenAlertMain() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const LockscreenAlertApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'Lockscreen Alert Example',
      home: HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int? _lastAlertId;

  @override
  void initState() {
    super.initState();
    LockscreenAlert.onAction.listen((event) {
      debugPrint('LockscreenAlert action: ${event.action} ${event.data}');
    });
  }

  Future<void> _showAlert() async {
    final id = await LockscreenAlert.show(
      payload: {
        'id': 'demo_${DateTime.now().millisecondsSinceEpoch}',
        'title': 'Demo alert',
        'from': 'A',
        'to': 'B',
      },
      notificationTitle: 'New alert',
      notificationBody: 'Tap to open',
    );
    if (id != null && mounted) {
      setState(() => _lastAlertId = id);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Lockscreen Alert Example')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('Lock the device, then tap the button.'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _showAlert,
              child: const Text('Show lock-screen alert'),
            ),
            if (_lastAlertId != null) ...[
              const SizedBox(height: 8),
              Text('Last alert id: $_lastAlertId'),
            ],
          ],
        ),
      ),
    );
  }
}

class LockscreenAlertApp extends StatelessWidget {
  const LockscreenAlertApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: LockscreenAlertScreen(),
    );
  }
}

class LockscreenAlertScreen extends StatefulWidget {
  const LockscreenAlertScreen({super.key});

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
    if (_payload == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                _payload!['title']?.toString() ?? 'Alert',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 8),
              Text('From: ${_payload!['from']}'),
              Text('To: ${_payload!['to']}'),
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ElevatedButton(
                    onPressed: () async {
                      await LockscreenAlert.notifyAccepted();
                    },
                    child: const Text('Accept'),
                  ),
                  const SizedBox(width: 16),
                  TextButton(
                    onPressed: () async {
                      await LockscreenAlert.notifyDismissed();
                    },
                    child: const Text('Dismiss'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
