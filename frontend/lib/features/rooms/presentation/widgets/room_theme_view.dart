import 'package:flutter/material.dart';
import 'package:hyped/features/rooms/domain/room.dart';

class RoomThemeView extends StatelessWidget {
  const RoomThemeView({
    required this.theme,
    required this.child,
    this.padding = const EdgeInsets.all(20),
    this.scrim = false,
    super.key,
  });

  final bool scrim;
  final RoomTheme theme;
  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final colors = colorsForTheme(theme);
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: colors,
        ),
      ),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          gradient: scrim
              ? const LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0xAA182033), Color(0xEE182033)],
                )
              : null,
        ),
        padding: padding,
        child: child,
      ),
    );
  }
}

List<Color> colorsForTheme(RoomTheme theme) => switch (theme.presetKey) {
  'mint-sky-01' => const [Color(0xFFB8F4DD), Color(0xFF9EC5FF)],
  'blue-lilac-02' => const [Color(0xFF6C8CFF), Color(0xFFC9A7FF)],
  'peach-gold-01' ||
  'sunset-coral-01' => const [Color(0xFFFFB199), Color(0xFFFF6A88)],
  'aurora-green-01' => const [Color(0xFF77E2A7), Color(0xFF2F7D68)],
  _ =>
    theme.kind == RoomThemeKind.gradient
        ? const [Color(0xFF6C8CFF), Color(0xFFC9A7FF)]
        : const [Color(0xFFDBE5FF), Color(0xFF6C8CFF)],
};

const roomThemeChoices = <RoomTheme>[
  RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'soft-blue-01',
    overlayKey: 'dark-soft',
  ),
  RoomTheme(
    kind: RoomThemeKind.preset,
    presetKey: 'mint-sky-01',
    overlayKey: 'dark-soft',
  ),
  RoomTheme(
    kind: RoomThemeKind.gradient,
    presetKey: 'blue-lilac-02',
    overlayKey: 'dark-soft',
  ),
  RoomTheme(
    kind: RoomThemeKind.gradient,
    presetKey: 'peach-gold-01',
    overlayKey: 'dark-soft',
  ),
];

String roomThemeName(RoomTheme theme) => switch (theme.presetKey) {
  'soft-blue-01' => 'Soft blue',
  'mint-sky-01' => 'Mint sky',
  'blue-lilac-02' => 'Blue lilac',
  'peach-gold-01' => 'Peach gold',
  'sunset-coral-01' => 'Sunset coral',
  'aurora-green-01' => 'Aurora green',
  _ => 'Room theme',
};
