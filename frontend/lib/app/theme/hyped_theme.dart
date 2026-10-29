import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class HypedTheme {
  const HypedTheme._();
  static const softBlue = Color(0xFF6C8CFF);
  static const ink = Color(0xFF182033);

  static ThemeData get light => _theme(Brightness.light);
  static ThemeData get dark => _theme(Brightness.dark);

  static ThemeData _theme(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final surface = dark ? const Color(0xFF171E2E) : Colors.white;
    final text = dark ? const Color(0xFFF4F7FF) : ink;
    final primary = dark ? const Color(0xFFAFC2FF) : const Color(0xFF4567D6);
    final inset = dark ? const Color(0xFF222D44) : const Color(0xFFEEF3FF);
    final scheme = ColorScheme.fromSeed(
      seedColor: softBlue,
      brightness: brightness,
      surface: surface,
      primary: primary,
      onSurface: text,
      onSurfaceVariant: dark
          ? const Color(0xFFB8C4DC)
          : const Color(0xFF5F687A),
      surfaceContainerLow: inset,
    );
    final base = ThemeData(
      brightness: brightness,
      fontFamily: 'Manrope',
    ).textTheme.apply(bodyColor: text, displayColor: text);
    TextStyle? heading(TextStyle? style) => style?.copyWith(
      fontFamily: 'Outfit',
      fontWeight: FontWeight.w600,
      letterSpacing: -0.6,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: dark
          ? const Color(0xFF0E1320)
          : const Color(0xFFF7F9FF),
      textTheme: base.copyWith(
        displayLarge: heading(base.displayLarge),
        displayMedium: heading(base.displayMedium),
        displaySmall: heading(base.displaySmall),
        headlineLarge: heading(base.headlineLarge),
        headlineMedium: heading(base.headlineMedium),
        headlineSmall: heading(base.headlineSmall),
        titleLarge: heading(base.titleLarge),
      ),
      appBarTheme: AppBarTheme(
        systemOverlayStyle: dark
            ? SystemUiOverlayStyle.light
            : SystemUiOverlayStyle.dark,
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleSpacing: 20,
        titleTextStyle: heading(base.titleLarge),
      ),
      cardTheme: CardThemeData(
        color: surface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: const EdgeInsets.symmetric(vertical: 6),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: inset,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 18,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: primary, width: 1.5),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: softBlue,
          foregroundColor: ink,
          minimumSize: const Size(0, 52),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          textStyle: const TextStyle(
            fontFamily: 'Manrope',
            fontWeight: FontWeight.w700,
            fontSize: 14,
          ),
          shape: const StadiumBorder(),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(0, 48),
          shape: const StadiumBorder(),
          side: BorderSide(color: primary.withValues(alpha: 0.25)),
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: primary,
        foregroundColor: scheme.onPrimary,
        shape: const CircleBorder(),
        elevation: 4,
      ),
      listTileTheme: ListTileThemeData(
        iconColor: primary,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: inset,
        selectedColor: softBlue.withValues(alpha: 0.25),
        side: BorderSide.none,
        shape: const StadiumBorder(),
      ),
      dividerTheme: DividerThemeData(color: primary.withValues(alpha: 0.1)),
    );
  }
}
