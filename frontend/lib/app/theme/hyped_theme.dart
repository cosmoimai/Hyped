import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class HypedTheme {
  const HypedTheme._();
  static const softBlue = Color(0xFF6C8CFF);

  static ThemeData get light => _theme(
    brightness: Brightness.light,
    background: const Color(0xFFF7F9FF),
    surface: Colors.white,
    text: const Color(0xFF182033),
    primary: const Color(0xFF4567D6),
  );

  static ThemeData get dark => _theme(
    brightness: Brightness.dark,
    background: const Color(0xFF0E1320),
    surface: const Color(0xFF171E2E),
    text: const Color(0xFFF4F7FF),
    primary: const Color(0xFFAFC2FF),
  );

  static ThemeData _theme({
    required Brightness brightness,
    required Color background,
    required Color surface,
    required Color text,
    required Color primary,
  }) {
    final scheme = ColorScheme.fromSeed(
      seedColor: softBlue,
      brightness: brightness,
      surface: surface,
      primary: primary,
    );
    return ThemeData(
      colorScheme: scheme,
      scaffoldBackgroundColor: background,
      textTheme: GoogleFonts.manropeTextTheme(
        ThemeData(brightness: brightness).textTheme,
      ).apply(bodyColor: text, displayColor: text),
      useMaterial3: true,
      cardTheme: const CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(20)),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    );
  }
}
