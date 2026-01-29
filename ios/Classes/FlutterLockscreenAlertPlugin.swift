import Flutter
import UIKit

/// iOS stub: lock-screen alert is not implemented. Use CallKit for similar behavior.
public class FlutterLockscreenAlertPlugin: NSObject, FlutterPlugin {

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "dev.flutter.plugins.lockscreen_alert/channel",
            binaryMessenger: registrar.messenger()
        )
        let instance = FlutterLockscreenAlertPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "isSupported":
            result(false)
        case "show":
            result(FlutterError(code: "UNAVAILABLE", message: "Lock-screen alert is not implemented on iOS. Use CallKit for critical alerts.", details: nil))
        case "dismiss":
            result(true)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
}
