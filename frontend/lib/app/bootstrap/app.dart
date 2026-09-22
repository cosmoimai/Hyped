import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/app/router/app_router.dart';
import 'package:hyped/app/theme/hyped_theme.dart';

class HypedApp extends ConsumerStatefulWidget {
  const HypedApp({super.key});
  @override
  ConsumerState<HypedApp> createState() => _HypedAppState();
}

class _HypedAppState extends ConsumerState<HypedApp> {
  GoRouter? _router;
  @override
  Widget build(BuildContext context) {
    _router ??= createRouter(ref.read(authControllerProvider));
    return MaterialApp.router(
      title: 'Hyped!',
      theme: HypedTheme.light,
      darkTheme: HypedTheme.dark,
      themeMode: ThemeMode.system,
      routerConfig: _router,
      debugShowCheckedModeBanner: false,
    );
  }

  @override
  void dispose() {
    _router?.dispose();
    super.dispose();
  }
}
