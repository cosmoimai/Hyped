import 'package:dio/dio.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:hyped/core/api/api_environment.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/android_device_metadata_provider.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/data/firebase_identity_providers.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';

final tokenStoreProvider = Provider<TokenStore>(
  (ref) => SecureTokenStore(
    const FlutterSecureStorage(
      aOptions: AndroidOptions(),
      iOptions: IOSOptions(
        accessibility: KeychainAccessibility.first_unlock_this_device,
      ),
    ),
  ),
);

final sessionInvalidationProvider = Provider<SessionInvalidationSignal>(
  (ref) => SessionInvalidationSignal(),
);

final googleIdentityProviderProvider = Provider<GoogleIdentityProvider>(
  (ref) => GoogleIdentityProvider(
    DefaultFirebaseBootstrap(),
    DefaultGoogleAccountSelector(GoogleSignIn.instance),
    DefaultFirebaseCredentialAuthenticator(),
  ),
);

final dioOptionsProvider = Provider<BaseOptions>(
  (ref) => BaseOptions(
    baseUrl: ApiEnvironment.baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
    headers: {'Accept': 'application/json'},
  ),
);

final publicDioProvider = Provider<Dio>(
  (ref) => Dio(ref.read(dioOptionsProvider)),
);

final authenticatedDioProvider = Provider<Dio>((ref) {
  final dio = Dio(ref.read(dioOptionsProvider));
  final refresher = AuthApi(
    ref.read(publicDioProvider),
    Dio(ref.read(dioOptionsProvider)),
  );
  dio.interceptors.add(
    AuthenticationInterceptor(
      dio,
      ref.read(tokenStoreProvider),
      refresher,
      onAuthenticationFailed: ref.read(sessionInvalidationProvider).invalidate,
    ),
  );
  return dio;
});

final authApiProvider = Provider<AuthApi>((ref) {
  return AuthApi(
    ref.read(publicDioProvider),
    ref.read(authenticatedDioProvider),
  );
});

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(
    ref.read(authApiProvider),
    ref.read(tokenStoreProvider),
    AndroidDeviceMetadataProvider(DeviceInfoPlugin()),
    ref.read(googleIdentityProviderProvider),
  ),
);

final authControllerProvider = ChangeNotifierProvider<AuthController>((ref) {
  final controller = AuthController(
    ref.read(authRepositoryProvider),
    ref.read(sessionInvalidationProvider),
  );
  controller.initialize();
  return controller;
});

final identityProviderFactoryProvider =
    Provider<Future<IdentityProvider> Function(SignInProvider)>((ref) {
      return (provider) async {
        return switch (provider) {
          SignInProvider.google => ref.read(googleIdentityProviderProvider),
        };
      };
    });
