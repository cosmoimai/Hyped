import 'package:go_router/go_router.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:hyped/features/authentication/presentation/screens/sign_in_screen.dart';
import 'package:hyped/features/authentication/presentation/screens/splash_screen.dart';
import 'package:hyped/features/home/presentation/screens/home_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_detail_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';
import 'package:hyped/features/onboarding/presentation/screens/onboarding_screen.dart';

GoRouter createRouter(AuthController auth) => GoRouter(
  initialLocation: '/splash',
  refreshListenable: auth,
  redirect: (context, state) {
    final status = auth.state.status;
    final path = state.matchedLocation;
    if (status == AuthStatus.initializing) {
      return path == '/splash' ? null : '/splash';
    }
    if (auth.state.isAuthenticated) {
      return _isPrivatePath(path) ? null : '/home';
    }
    if (status == AuthStatus.failure && path == '/splash') return '/sign-in';
    if (path == '/splash') return '/onboarding';
    if (_isPrivatePath(path)) return '/sign-in';
    return null;
  },
  routes: [
    GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
    GoRoute(path: '/onboarding', builder: (_, _) => const OnboardingScreen()),
    GoRoute(path: '/sign-in', builder: (_, _) => const SignInScreen()),
    GoRoute(path: '/home', builder: (_, _) => const HomeScreen()),
    GoRoute(path: '/rooms/new', builder: (_, _) => const CreateRoomScreen()),
    GoRoute(
      path: '/rooms/:roomId',
      builder: (_, state) =>
          RoomDetailScreen(roomId: state.pathParameters['roomId']!),
    ),
    GoRoute(
      path: '/rooms/:roomId/edit',
      builder: (_, state) =>
          EditRoomScreen(roomId: state.pathParameters['roomId']!),
    ),
  ],
);

bool _isPrivatePath(String path) =>
    path == '/home' || path.startsWith('/rooms');
