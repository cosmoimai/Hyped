import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/invitations/data/invitation_api.dart';
import 'package:hyped/features/invitations/data/invitation_repository.dart';
import 'package:hyped/features/invitations/domain/invitation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_controllers.dart';

final pendingInviteControllerProvider =
    ChangeNotifierProvider<PendingInviteController>(
      (ref) => PendingInviteController(),
    );

final invitationApiProvider = Provider<InvitationApi>(
  (ref) => InvitationApi(
    ref.read(publicDioProvider),
    ref.read(authenticatedDioProvider),
  ),
);

final invitationRepositoryProvider = Provider<InvitationRepository>(
  (ref) => InvitationRepository(ref.read(invitationApiProvider)),
);

final inviteFlowControllerProvider =
    ChangeNotifierProvider.family<InviteFlowController, PendingInviteIntent>((
      ref,
      intent,
    ) {
      final controller = InviteFlowController(
        ref.read(invitationRepositoryProvider),
        intent,
      );
      controller.loadPreview();
      return controller;
    });
