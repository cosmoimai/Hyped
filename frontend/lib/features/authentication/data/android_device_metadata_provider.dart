import 'package:device_info_plus/device_info_plus.dart';
import 'package:hyped/features/authentication/domain/device_metadata.dart';
import 'package:package_info_plus/package_info_plus.dart';

class AndroidDeviceMetadataProvider implements DeviceMetadataProvider {
  AndroidDeviceMetadataProvider(this._deviceInfo);

  final DeviceInfoPlugin _deviceInfo;

  @override
  Future<DeviceMetadata> load() async {
    final device = await _deviceInfo.androidInfo;
    final package = await PackageInfo.fromPlatform();
    final name = '${device.manufacturer} ${device.model}'.trim();
    return DeviceMetadata(
      deviceName: _bounded(name.isEmpty ? 'Android device' : name, 80),
      appVersion: _bounded('${package.version}+${package.buildNumber}', 64),
    );
  }

  String _bounded(String value, int length) =>
      value.length <= length ? value : value.substring(0, length);
}
