import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/invitations/data/app_links/invite_link_validator.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';

final androidInviteLinkPlatformProvider = Provider<AndroidInviteLinkPlatform>(
  (ref) => AndroidInviteLinkPlatform(),
);

class AndroidInviteLinkPlatform {
  AndroidInviteLinkPlatform([
    this.channel = const MethodChannel('hyped/android_invite_links'),
  ]);
  final MethodChannel channel;
  InviteLinkValidator? _validator;
  void Function(PendingInviteIntent)? _receive;
  ({Object? value, int epoch})? _early;
  int _epoch = 0;
  bool _closed = false;
  Future<void> initialize(void Function(PendingInviteIntent) receive) async {
    _receive = receive;
    _closed = false;
    final epoch = _epoch;
    channel.setMethodCallHandler((call) async {
      if (_closed || call.method != 'inviteLink') return;
      if (_validator == null) {
        _early = (value: call.arguments, epoch: _epoch);
      } else {
        _deliver(call.arguments);
      }
    });
    try {
      final initial = await channel.invokeMapMethod<String, Object?>(
        'getInitialLink',
      );
      if (_closed) return;
      _validator = InviteLinkValidator(
        initial?['host'] is String ? initial!['host'] as String : '',
      );
      final early = _early;
      _early = null;
      if (early != null && early.epoch == _epoch) {
        _deliver(early.value);
      } else if (epoch == _epoch) {
        _deliver(initial?['uri']);
      }
    } catch (_) {
      /* Missing/disabled platform links do not interrupt normal app startup. */
    }
  }

  void _deliver(Object? raw) {
    if (_closed) return;
    final intent = _validator?.parse(raw);
    if (intent != null) _receive?.call(intent);
  }

  Future<void> clearPending() async {
    _epoch++;
    _early = null;
    try {
      await channel.invokeMethod<void>('clearPending');
    } catch (_) {}
  }

  void close() {
    _closed = true;
    _epoch++;
    _early = null;
    _receive = null;
    _validator = null;
    channel.setMethodCallHandler(null);
  }
}
