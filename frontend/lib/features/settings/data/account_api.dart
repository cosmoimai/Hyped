import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

class AccountProfile {
  const AccountProfile(
    this.displayName,
    this.activeDevices,
    this.ownedRooms,
    this.joinedRooms,
  );
  final String displayName;
  final int activeDevices;
  final int ownedRooms;
  final int joinedRooms;
  factory AccountProfile.fromJson(Map<String, dynamic> json) {
    final limits = json['limits'] as Map;
    return AccountProfile(
      json['displayName'] as String,
      limits['activeDevices'] as int,
      limits['ownedActiveRooms'] as int,
      limits['joinedActiveRooms'] as int,
    );
  }
}

class ActiveDevice {
  const ActiveDevice(
    this._sessionId,
    this.name,
    this.platform,
    this.lastUsedAt,
    this.isCurrent,
  );
  final String _sessionId;
  final String name;
  final String platform;
  final DateTime lastUsedAt;
  final bool isCurrent;
  factory ActiveDevice.fromJson(Map<String, dynamic> json) => ActiveDevice(
    json['sessionId'] as String,
    (json['deviceName'] as String)
        .replaceAll(RegExp(r'[\x00-\x1f\x7f]'), '')
        .trim(),
    json['platform'] as String,
    DateTime.parse(json['lastUsedAt'] as String).toUtc(),
    json['isCurrent'] == true,
  );
  @override
  String toString() => 'ActiveDevice[REDACTED]';
}

class DeletionReadiness {
  const DeletionReadiness(this.ownedRooms, this.joinedRoomCount);
  final List<OwnedAccountRoom> ownedRooms;
  final int joinedRoomCount;
  bool get ready => ownedRooms.isEmpty && joinedRoomCount == 0;
  factory DeletionReadiness.fromJson(Map<String, dynamic> json) =>
      DeletionReadiness(
        (json['ownedRooms'] as List)
            .map(
              (item) => OwnedAccountRoom(
                item['roomId'] as String,
                item['title'] as String,
              ),
            )
            .toList(),
        json['joinedRoomCount'] as int,
      );
}

class OwnedAccountRoom {
  const OwnedAccountRoom(this.roomId, this.title);
  final String roomId;
  final String title;
}

class AccountApi {
  AccountApi(this.dio);
  final Dio dio;
  Future<AccountProfile> profile() async {
    try {
      return AccountProfile.fromJson(
        (await dio.get<Map<String, dynamic>>('/me')).data!,
      );
    } on DioException catch (error) {
      throw AccountApiException.from(error);
    }
  }

  Future<List<ActiveDevice>> devices() async {
    try {
      final response = await dio.get<Map<String, dynamic>>('/auth/sessions');
      final items = (response.data!['items'] as List)
          .map(
            (item) =>
                ActiveDevice.fromJson(Map<String, dynamic>.from(item as Map)),
          )
          .toList();
      items.sort((a, b) => b.lastUsedAt.compareTo(a.lastUsedAt));
      return items;
    } on DioException catch (error) {
      throw AccountApiException.from(error);
    }
  }

  Future<void> revoke(ActiveDevice device, String expectedSession) async {
    try {
      await dio.delete<void>(
        '/auth/sessions/${device._sessionId}',
        options: Options(extra: {'expectedSessionId': expectedSession}),
      );
    } on DioException catch (error) {
      throw AccountApiException.from(error);
    }
  }

  Future<DeletionReadiness> readiness() async {
    try {
      return DeletionReadiness.fromJson(
        (await dio.get<Map<String, dynamic>>('/me/deletion-readiness')).data!,
      );
    } on DioException catch (error) {
      throw AccountApiException.from(error);
    }
  }

  Future<void> delete(String recentToken, String expectedSession) async {
    try {
      await dio.post<void>(
        '/me/deletion-requests',
        data: {'recentFirebaseIdToken': recentToken},
        options: Options(
          headers: {'Idempotency-Key': const Uuid().v4()},
          extra: {'expectedSessionId': expectedSession},
        ),
      );
    } on DioException catch (error) {
      throw AccountApiException.from(error);
    }
  }
}

class AccountApiException implements Exception {
  const AccountApiException(this.code, [this.status]);
  final String? code;
  final int? status;
  factory AccountApiException.from(DioException error) => AccountApiException(
    error.response?.data is Map
        ? (error.response!.data as Map)['code'] as String?
        : null,
    error.response?.statusCode,
  );
  @override
  String toString() => 'Account request failed';
}
