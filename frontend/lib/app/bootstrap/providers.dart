import 'package:flutter/painting.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';
import 'package:hyped/features/notifications/data/notification_platform.dart';
import 'package:hyped/features/notifications/data/notification_token_store.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:hyped/core/api/api_environment.dart';
import 'package:hyped/core/api/authentication_interceptor.dart';
import 'package:hyped/core/secure_storage/token_store.dart';
import 'package:hyped/features/android_widgets/data/widget_token_store.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/room_widget_providers.dart';
import 'package:hyped/features/authentication/data/android_device_metadata_provider.dart';
import 'package:hyped/features/authentication/data/auth_api.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/data/firebase_identity_providers.dart';
import 'package:hyped/features/authentication/domain/identity_provider.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';

final tokenStoreProvider = Provider<TokenStore>(
  (ref) => NotificationTokenStore(
    WidgetTokenStore(
      SecureTokenStore(
        const FlutterSecureStorage(
          aOptions: AndroidOptions(),
          iOptions: IOSOptions(
            accessibility: KeychainAccessibility.first_unlock_this_device,
          ),
        ),
      ),
      ref.read(roomWidgetRepositoryProvider),
    ),
    ref.read(notificationPlatformProvider),
  ),
);

final sessionInvalidationProvider = Provider<SessionInvalidationSignal>(
  (ref) => SessionInvalidationSignal(),
);

final googleAccountSelectorProvider = Provider<GoogleAccountSelector>(
  (ref) => DefaultGoogleAccountSelector(GoogleSignIn.instance),
);

final googleIdentityProviderProvider = Provider<GoogleIdentityProvider>(
  (ref) => GoogleIdentityProvider(
    DefaultFirebaseBootstrap(),
    ref.read(googleAccountSelectorProvider),
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
      onAccountDeleted: () {
        ref.read(pendingInviteControllerProvider).clear();
        imageCache.clear();
        imageCache.clearLiveImages();
      },
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
    ref.read(roomWidgetRepositoryProvider),
    ref.read(notificationPlatformProvider),
    () {
      ref.read(pendingInviteControllerProvider).clear();
      imageCache.clear();
      imageCache.clearLiveImages();
    },
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
