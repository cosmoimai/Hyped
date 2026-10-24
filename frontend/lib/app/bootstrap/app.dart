import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/data/cache/cache_providers.dart';
import 'package:hyped/features/policy/presentation/policy_providers.dart';
import 'package:hyped/features/settings/data/theme_preference.dart';
import 'dart:async';
import 'package:hyped/features/notifications/presentation/push_providers.dart';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/app/theme/hyped_theme.dart';
import 'package:hyped/features/android_widgets/presentation/controllers/widget_navigation.dart';
import 'package:hyped/features/invitations/presentation/controllers/invitation_providers.dart';

class HypedApp extends ConsumerStatefulWidget {
  const HypedApp({super.key});
  @override
  ConsumerState<HypedApp> createState() => _HypedAppState();
}

class _HypedAppState extends ConsumerState<HypedApp>
    with WidgetsBindingObserver {
  GoRouter? _router;
  static const _widgets = MethodChannel('hyped/android_widgets');
  final _widgetNavigation = WidgetNavigation();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (!kIsWeb && Platform.isAndroid) {
      _widgets.setMethodCallHandler((call) async {
        if (call.method == 'widgetTapped') _receiveWidget(call.arguments);
      });
      _loadWidgetLaunch();
    }
  }

  Future<void> _loadWidgetLaunch() async {
    try {
      _receiveWidget(await _widgets.invokeMethod<String>('getLaunchLocation'));
    } catch (_) {}
  }

  void _receiveWidget(Object? location) {
    if (!mounted) return;
    _widgetNavigation.receive(location);
    _resumeWidget();
  }

  void _resumeWidget() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _router == null) return;
      final location = _widgetNavigation.takeIfAuthenticated(
        ref.read(authControllerProvider).state.isAuthenticated,
      );
      if (location != null) _router!.go(location);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  void _resumeNotification() {
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final location = await ref
          .read(pushControllerProvider)
          .takePendingLocation();
      if (mounted && location != null) _router?.go(location);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(ref.read(pushControllerProvider).sync(force: true));
      unawaited(ref.read(policyControllerProvider).load());
      ref.invalidate(roomListControllerProvider);
      ref.invalidate(roomDetailControllerProvider);
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(authControllerProvider, (_, auth) {
      ref
          .read(pushControllerProvider)
          .setAuthenticated(auth.state.isAuthenticated);
      if (auth.state.isAuthenticated) {
        _resumeWidget();
        _resumeNotification();
      }
    });
    ref.listen(pushControllerProvider, (_, _) => _resumeNotification());
    _router ??= createRouter(
      ref.read(authControllerProvider),
      ref.read(pendingInviteControllerProvider),
      ref.read(policyControllerProvider),
      ref.read(offlineStateProvider),
      () => ref.read(roomCacheSessionProvider).restoredSession,
    );
    return MaterialApp.router(
      title: 'Hyped!',
      theme: HypedTheme.light,
      darkTheme: HypedTheme.dark,
      themeMode: ref.watch(themeControllerProvider).mode,
      routerConfig: _router,
      debugShowCheckedModeBanner: false,
    );
  }

  @override
  void dispose() {
    if (!kIsWeb && Platform.isAndroid) _widgets.setMethodCallHandler(null);
    WidgetsBinding.instance.removeObserver(this);
    _router?.dispose();
    super.dispose();
  }
}
