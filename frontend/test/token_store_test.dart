import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'helpers/fakes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  test('stores, reads and clears credentials in secure storage', () async {
    final store = SecureTokenStore(const FlutterSecureStorage());
    await store.write(tokens());
    final restored = await store.read();
    expect(restored?.accessToken, 'access-one');
    expect(restored?.refreshToken, 'refresh-one');
    await store.clear();
    expect(await store.read(), isNull);
  });

  test(
    'installation identifier is stable and survives token cleanup',
    () async {
      final store = SecureTokenStore(const FlutterSecureStorage());
      final first = await store.installationId();
      await store.write(tokens());
      await store.clear();
      expect(await store.installationId(), first);
    },
  );
}
