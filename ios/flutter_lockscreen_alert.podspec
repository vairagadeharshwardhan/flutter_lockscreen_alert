Pod::Spec.new do |s|
  s.name             = 'flutter_lockscreen_alert'
  s.version          = '1.0.0'
  s.summary          = 'Flutter plugin to show critical alert UI on the lock screen (Android).'
  s.description      = <<-DESC
  Flutter plugin to show critical alert UI on the device lock screen using
  Android full-screen intents. iOS is not implemented.
                       DESC
  s.homepage         = 'https://github.com/vairagadeharshwardhan/flutter_lockscreen_alert'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'vairagadeharshwardhan' => 'https://github.com/vairagadeharshwardhan' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
