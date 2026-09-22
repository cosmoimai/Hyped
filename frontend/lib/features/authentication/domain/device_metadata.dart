class DeviceMetadata {
  const DeviceMetadata({required this.deviceName, required this.appVersion});

  final String deviceName;
  final String appVersion;
}

abstract interface class DeviceMetadataProvider {
  Future<DeviceMetadata> load();
}
