import 'package:hyped/features/invitations/domain/invitation.dart';

/// Only the configured exact HTTPS authority and the API's raw token path are accepted.
/// Parsing failures never carry the incoming URL into diagnostics.
class InviteLinkValidator {
  InviteLinkValidator(this.host);
  final String host;
  static bool validHost(String host) =>
      host.length >= 3 &&
      host.length <= 253 &&
      host.contains('.') &&
      host
          .split('.')
          .every(
            (label) => RegExp(
              r'^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$',
            ).hasMatch(label),
          );
  PendingInviteIntent? parse(Object? raw) {
    if (!validHost(host) ||
        raw is! String ||
        raw.length > 512 ||
        !RegExp(r'^[\x21-\x7e]+$').hasMatch(raw)) {
      return null;
    }
    final match = RegExp(
      r'^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]+)(/invite/([A-Za-z0-9_-]{43}))$',
    ).firstMatch(raw);
    if (match == null ||
        match[1]!.toLowerCase() != 'https' ||
        match[2]!.toLowerCase() != host) {
      return null;
    }
    return PendingInviteIntent(kind: InviteKind.link, credential: match[4]!);
  }
}
