import 'dart:async';
import 'package:flutter/painting.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/media/data/room_photo_api.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';

final roomPhotoApiProvider = Provider<RoomPhotoApi>(
  (ref) => RoomPhotoApi(ref.read(authenticatedDioProvider)),
);
final roomPhotoPickerProvider = Provider<RoomPhotoPicker>(
  (ref) => const RoomPhotoPicker(),
);
final roomPhotoBytesProvider = FutureProvider.autoDispose
    .family<Uint8List?, String>((ref, id) async {
      ref.watch(authControllerProvider.select((auth) => auth.state.status));
      final widgets = ref.read(roomWidgetRepositoryProvider);
      final epoch = widgets.sessionEpoch;
      final store = ref.read(tokenStoreProvider);
      final cancel = CancelToken();
      ref.onDispose(() => cancel.cancel());
      final session = (await store.read())?.sessionId;
      if (session == null || cancel.isCancelled) return null;
      final bytes = await ref
          .read(roomPhotoApiProvider)
          .image(id, session, cancel);
      if (cancel.isCancelled ||
          epoch != widgets.sessionEpoch ||
          (await store.read())?.sessionId != session) {
        return null;
      }
      if (bytes != null) {
        ref.onDispose(() => unawaited(MemoryImage(bytes).evict()));
      }
      return bytes;
    });

final roomPhotoSelectionProvider = ChangeNotifierProvider.autoDispose
    .family<RoomPhotoSelection, RoomFormModel>((ref, model) {
      final controller = RoomPhotoSelection(
        ref.read(roomPhotoApiProvider),
        ref.read(roomPhotoPickerProvider),
        ref.read(tokenStoreProvider),
        () => ref.read(roomWidgetRepositoryProvider).sessionEpoch,
        model,
      );
      ref.listen(
        authControllerProvider.select((auth) => auth.state.status),
        (_, _) => controller.clear(),
      );
      return controller;
    });

class RoomPhotoSelection extends ChangeNotifier {
  RoomPhotoSelection(
    this.api,
    this.picker,
    this.tokens,
    this.epoch,
    this.model,
  );
  final RoomPhotoApi api;
  final RoomPhotoPicker picker;
  final TokenStore tokens;
  final int Function() epoch;
  final RoomFormModel model;
  PickedRoomPhoto? selected;
  RoomTheme? previous;
  bool busy = false;
  bool ready = false;
  String? error;
  bool _disposed = false;
  int _generation = 0;
  CancelToken? _cancel;
  bool get blocked => busy || selected != null && !ready;
  Future<void> pick() async {
    if (busy || _disposed) return;
    final generation = ++_generation;
    final startEpoch = epoch();
    busy = true;
    error = null;
    notifyListeners();
    try {
      final session = (await tokens.read())?.sessionId;
      if (session == null) throw const RoomPhotoException();
      final photo = await picker.pick();
      if (_disposed ||
          generation != _generation ||
          startEpoch != epoch() ||
          (await tokens.read())?.sessionId != session) {
        if (!_disposed && generation == _generation) clear();
        return;
      }
      if (photo == null) return;
      previous ??= model.theme;
      selected = photo;
      ready = false;
      notifyListeners();
      await _upload(photo, session, generation, startEpoch);
    } catch (failure) {
      if (!_disposed && generation == _generation) error = _message(failure);
    } finally {
      if (!_disposed && generation == _generation) {
        busy = false;
        notifyListeners();
      }
    }
  }

  Future<void> retry() async {
    final photo = selected;
    if (photo == null || busy || _disposed) return;
    final generation = ++_generation;
    final startEpoch = epoch();
    busy = true;
    error = null;
    notifyListeners();
    try {
      final session = (await tokens.read())?.sessionId;
      if (session == null) throw const RoomPhotoException();
      await _upload(photo, session, generation, startEpoch);
    } catch (failure) {
      if (!_disposed && generation == _generation) error = _message(failure);
    } finally {
      if (!_disposed && generation == _generation) {
        busy = false;
        notifyListeners();
      }
    }
  }

  Future<void> _upload(
    PickedRoomPhoto photo,
    String session,
    int generation,
    int startEpoch,
  ) async {
    _cancel = CancelToken();
    final id = await api.upload(photo, session, _cancel!);
    if (_disposed ||
        generation != _generation ||
        startEpoch != epoch() ||
        (await tokens.read())?.sessionId != session) {
      if (!_disposed && generation == _generation) clear();
      return;
    }
    model.theme = RoomTheme(
      kind: RoomThemeKind.uploadedImage,
      presetKey: 'soft-blue-01',
      overlayKey: 'image-dark-40',
      mediaAssetId: id,
    );
    ready = true;
  }

  static String _message(Object failure) => switch (failure) {
    RoomPhotoException(code: 'MEDIA_UNAVAILABLE') =>
      'Photo sharing needs cloud storage and moderation setup. Your current room cover has not changed.',
    RoomPhotoException(code: 'MANUAL_REVIEW') =>
      'This photo needs review before it can be shared. Choose another photo or keep your current theme.',
    RoomPhotoException(code: 'PHOTO_REJECTED') =>
      'This photo cannot be used. Please choose another photo.',
    _ =>
      'Could not prepare your photo. Choose a static photo up to 5 MB, or retry when connected.',
  };
  void clear({bool restore = true}) {
    _generation++;
    _cancel?.cancel();
    if (restore && previous != null) model.theme = previous!;
    if (selected != null) unawaited(MemoryImage(selected!.bytes).evict());
    previous = null;
    selected = null;
    error = null;
    busy = false;
    ready = false;
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _cancel?.cancel();
    if (selected != null) unawaited(MemoryImage(selected!.bytes).evict());
    selected = null;
    super.dispose();
  }
}
