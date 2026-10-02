import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';
import 'package:hyped/features/rooms/domain/room.dart';

void main() {
  test('controller maps invalid invites to enumeration-safe copy', () async {
    final controller = InviteFlowController(
      _FakeInvitationRepository(
        previewError: const InvitationApiException(code: 'INVITATION_INVALID'),
      ),
      const PendingInviteIntent(kind: InviteKind.link, credential: 'secret'),
    );

    await controller.loadPreview();

    expect(
      controller.error,
      'This invite is invalid, expired, or was rotated.',
    );
    expect(controller.error, isNot(contains('secret')));
  });

  test(
    'controller maps room full and joined-room limit join failures',
    () async {
      final full = InviteFlowController(
        _FakeInvitationRepository(
          previewValue: preview(),
          joinError: const InvitationApiException(code: 'ROOM_FULL'),
        ),
        const PendingInviteIntent(kind: InviteKind.link, credential: 'secret'),
      );
      await full.loadPreview();
      await full.join();
      expect(full.error, 'This room is full.');

      final limit = InviteFlowController(
        _FakeInvitationRepository(
          previewValue: preview(),
          joinError: const InvitationApiException(
            code: 'JOINED_ROOM_LIMIT_REACHED',
          ),
        ),
        const PendingInviteIntent(
          kind: InviteKind.roomCode,
          credential: '7K4MP9QX',
        ),
      );
      await limit.loadPreview();
      await limit.join();
      expect(limit.error, 'You have reached the joined-room limit.');
    },
  );

  test(
    'controller reports already-member join without treating it as an error',
    () async {
      final controller = InviteFlowController(
        _FakeInvitationRepository(
          previewValue: preview(),
          joinResult: InviteJoinResult(room: room(), created: false),
        ),
        const PendingInviteIntent(kind: InviteKind.link, credential: 'secret'),
      );

      await controller.loadPreview();
      final result = await controller.join();

      expect(result?.created, isFalse);
      expect(controller.message, 'You are already in this room.');
      expect(controller.error, isNull);
    },
  );
}

class _FakeInvitationRepository extends InvitationRepository {
  _FakeInvitationRepository({
    this.previewValue,
    this.previewError,
    this.joinResult,
    this.joinError,
  }) : super(InvitationApi(Dio(), Dio()));

  final InvitePreview? previewValue;
  final InvitationApiException? previewError;
  final InviteJoinResult? joinResult;
  final InvitationApiException? joinError;

  @override
  Future<InvitePreview> preview(PendingInviteIntent intent) async {
    if (previewError != null) throw previewError!;
    return previewValue!;
  }

  @override
  Future<InviteJoinResult> join(
    PendingInviteIntent intent,
    String previewReference,
  ) async {
    if (joinError != null) throw joinError!;
    return joinResult ?? InviteJoinResult(room: room(), created: true);
  }
}

InvitePreview preview() => InvitePreview(
  previewReference: 'preview-ref',
  expiresAt: DateTime.utc(2026, 9, 9, 10, 40),
  event: InviteEvent(
    title: 'Goa trip',
    eventAt: DateTime.utc(2030, 12, 20, 4, 30),
    eventTimeZone: 'Asia/Kolkata',
    theme: RoomTheme(
      kind: RoomThemeKind.preset,
      presetKey: 'soft-blue-01',
      overlayKey: 'dark-soft',
    ),
  ),
  inviter: const InviteInviter(displayName: 'Aarav'),
  memberCount: 8,
  requiresAuthentication: true,
);

Room room() => Room(
  id: 'room-id',
  title: 'Goa trip',
  eventAt: DateTime.utc(2030, 12, 20, 4, 30),
  eventTimeZone: 'Asia/Kolkata',
  theme: const RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
  status: RoomStatus.active,
  role: RoomRole.member,
  memberCount: 8,
  revision: 4,
  createdAt: DateTime.utc(2026, 9, 9, 10),
  updatedAt: DateTime.utc(2026, 9, 9, 10, 30),
  serverNow: DateTime.utc(2026, 9, 9, 11),
);
