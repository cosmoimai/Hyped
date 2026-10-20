import 'package:hyped/core/auth/auth_tokens.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/android_widgets/data/room_widget_repository.dart';

/// Every credential deletion path also removes protected home-screen content.
/// Includes logout, revoked/deleted accounts and failed token refreshes.
class WidgetTokenStore implements TokenStore {
  WidgetTokenStore(this._tokens, this._widgets);
  final TokenStore _tokens;
  final RoomWidgetRepository _widgets;
  @override
  Future<AuthTokens?> read() => _tokens.read();
  @override
  Future<void> write(AuthTokens tokens) => _tokens.write(tokens);
  @override
  Future<String> installationId() => _tokens.installationId();
  @override
  Future<void> clear() async {
    try {
      await _widgets.clearProtectedContent();
    } finally {
      await _tokens.clear();
    }
  }
}
