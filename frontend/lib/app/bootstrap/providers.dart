import 'package:dio/dio.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hyped/core/api/api_environment.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/data/firebase_identity_providers.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';

final tokenStoreProvider = Provider<TokenStore>(
  (ref) =>
      SecureTokenStore(const FlutterSecureStorage(aOptions: AndroidOptions())),
);

final sessionInvalidationProvider = Provider<SessionInvalidationSignal>(
  (ref) => SessionInvalidationSignal(),
);

final authApiProvider = Provider<AuthApi>((ref) {
  final options = BaseOptions(
    baseUrl: ApiEnvironment.baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
    headers: {'Accept': 'application/json'},
  );
  final publicDio = Dio(options);
  final authenticatedDio = Dio(options);
  final api = AuthApi(publicDio, authenticatedDio);
  authenticatedDio.interceptors.add(
    AuthenticationInterceptor(
      authenticatedDio,
      ref.read(tokenStoreProvider),
      api,
      onAuthenticationFailed: ref.read(sessionInvalidationProvider).invalidate,
    ),
  );
  return api;
});

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) =>
      AuthRepository(ref.read(authApiProvider), ref.read(tokenStoreProvider)),
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
        if (Firebase.apps.isEmpty) await Firebase.initializeApp();
        return switch (provider) {
          SignInProvider.google => GoogleIdentityProvider(
            FirebaseAuth.instance,
          ),
          SignInProvider.apple => AppleIdentityProvider(FirebaseAuth.instance),
        };
      };
    });
