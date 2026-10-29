import 'dart:convert';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter/services.dart';

class PickedRoomPhoto {
  const PickedRoomPhoto(this.bytes, this.width, this.height);
  final Uint8List bytes;
  final int width;
  final int height;
  @override
  String toString() => 'PickedRoomPhoto[REDACTED]';
}

class RoomPhotoPicker {
  const RoomPhotoPicker();
  Future<PickedRoomPhoto?> pick() async {
    final result = await const MethodChannel(
      'hyped/room_photo',
    ).invokeMapMethod<String, dynamic>('pick');
    if (result == null) return null;
    final bytes = result['bytes'];
    final width = result['width'];
    final height = result['height'];
    if (bytes is! Uint8List ||
        bytes.isEmpty ||
        bytes.length > 1572864 ||
        width is! int ||
        height is! int ||
        width < 1 ||
        height < 1 ||
        width > 2048 ||
        height > 2048) {
      throw const RoomPhotoException();
    }
    return PickedRoomPhoto(bytes, width, height);
  }
}

class RoomPhotoException implements Exception {
  const RoomPhotoException([this.code]);
  final String? code;
  @override
  String toString() => 'Room photo unavailable';
}

class RoomPhotoApi {
  RoomPhotoApi(this.authenticated, [Dio? transfer])
    : transfer =
          transfer ??
          Dio(
            BaseOptions(
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 30),
              sendTimeout: const Duration(seconds: 30),
              followRedirects: false,
            ),
          ) {
    if (identical(authenticated, this.transfer)) {
      throw ArgumentError('A separate object-storage client is required.');
    }
    this.transfer.interceptors.clear(keepImplyContentTypeInterceptor: false);
    this.transfer.options.headers.clear();
  }
  final Dio authenticated;
  // Deliberately interceptor-free. JWTs must never reach object storage.
  final Dio transfer;
  static bool storageUrl(Object? value) {
    if (value is! String || value.length > 8192) return false;
    final uri = Uri.tryParse(value);
    return uri != null &&
        uri.scheme == 'https' &&
        uri.userInfo.isEmpty &&
        !uri.hasPort &&
        uri.fragment.isEmpty &&
        RegExp(
          r'^[a-f0-9]{32}\.r2\.cloudflarestorage\.com$',
        ).hasMatch(uri.host);
  }

  Future<String> upload(
    PickedRoomPhoto photo,
    String session,
    CancelToken cancel,
  ) async {
    try {
      final options = Options(extra: {'expectedSessionId': session});
      final hash = base64Encode(sha256.convert(photo.bytes).bytes);
      final authorization = await authenticated.post<Map<String, dynamic>>(
        '/media/uploads',
        options: options,
        cancelToken: cancel,
        data: {
          'scope': 'ROOM_COVER',
          'fileName': 'room-cover.jpg',
          'contentType': 'image/jpeg',
          'sizeBytes': photo.bytes.length,
          'widthPx': photo.width,
          'heightPx': photo.height,
          'contentSha256': hash,
        },
      );
      final data = authorization.data!;
      final id = data['mediaAssetId'];
      if (id is! String ||
          !RegExp(
            r'^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$',
          ).hasMatch(id)) {
        throw const RoomPhotoException();
      }
      final upload = data['upload'] as Map<String, dynamic>;
      if (!storageUrl(upload['url']) ||
          upload['method'] != 'PUT' ||
          upload['maximumBytes'] != 1572864 ||
          !DateTime.parse(
            upload['expiresAt'] as String,
          ).isAfter(DateTime.now())) {
        throw const RoomPhotoException();
      }
      final headers = Map<String, dynamic>.from(upload['headers'] as Map);
      if (headers.keys.any(
        (key) => !{
          'content-type',
          'content-length',
          'x-amz-checksum-sha256',
        }.contains(key.toLowerCase()),
      )) {
        throw const RoomPhotoException();
      }
      await transfer.put<void>(
        upload['url'] as String,
        data: Stream.value(photo.bytes),
        cancelToken: cancel,
        options: Options(
          headers: headers,
          contentType: 'image/jpeg',
          followRedirects: false,
        ),
      );
      final completed = await authenticated.post<Map<String, dynamic>>(
        '/media/uploads/$id/complete',
        options: options,
        cancelToken: cancel,
        data: {'contentSha256': hash},
      );
      final state = completed.data?['status'];
      if (state != 'READY') {
        throw RoomPhotoException(
          state == 'MANUAL_REVIEW'
              ? 'MANUAL_REVIEW'
              : state == 'REJECTED'
              ? 'PHOTO_REJECTED'
              : 'PHOTO_PROCESSING',
        );
      }
      return id;
    } on RoomPhotoException {
      rethrow;
    } on DioException catch (error) {
      final code = error.response?.data is Map
          ? (error.response!.data as Map)['code']
          : null;
      throw RoomPhotoException(
        {
              'MEDIA_UNAVAILABLE',
              'MEDIA_UPLOAD_LIMIT',
              'CURRENT_POLICY_ACCEPTANCE_REQUIRED',
              'MEDIA_TOO_LARGE',
              'MEDIA_DIMENSIONS_EXCEEDED',
              'MEDIA_ANIMATED_UPLOAD_UNSUPPORTED',
            }.contains(code)
            ? code as String
            : null,
      );
    } catch (_) {
      throw const RoomPhotoException();
    }
  }

  Future<Uint8List?> image(
    String id,
    String session,
    CancelToken cancel,
  ) async {
    try {
      final metadata = await authenticated.get<Map<String, dynamic>>(
        '/media/$id',
        cancelToken: cancel,
        options: Options(extra: {'expectedSessionId': session}),
      );
      final url = metadata.data?['url'];
      if (!storageUrl(url)) return null;
      final response = await transfer.get<ResponseBody>(
        url as String,
        cancelToken: cancel,
        options: Options(
          responseType: ResponseType.stream,
          followRedirects: false,
        ),
      );
      final bytes = BytesBuilder(copy: false);
      await for (final chunk in response.data!.stream) {
        if (bytes.length + chunk.length > 1572864) {
          cancel.cancel();
          return null;
        }
        bytes.add(chunk);
      }
      return bytes.takeBytes();
    } catch (_) {
      return null;
    } // Never propagate signed URLs into ImageProvider diagnostics.
  }
}
