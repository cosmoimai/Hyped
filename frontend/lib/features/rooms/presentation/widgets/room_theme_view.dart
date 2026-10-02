import 'package:flutter/material.dart';
import 'package:hyped/features/rooms/domain/room.dart';

class RoomThemeView extends StatelessWidget {
  const RoomThemeView({
    required this.theme,
    required this.child,
    this.padding = const EdgeInsets.all(20),
    super.key,
  });

  final RoomTheme theme;
  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final colors = colorsForTheme(theme);
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: colors,
        ),
      ),
      child: Padding(padding: padding, child: child),
    );
  }
}

List<Color> colorsForTheme(RoomTheme theme) => switch (theme.presetKey) {
  'mint-sky-01' => const [Color(0xFFB8F4DD), Color(0xFF9EC5FF)],
  'blue-lilac-02' => const [Color(0xFF6C8CFF), Color(0xFFC9A7FF)],
  'sunset-peach-01' => const [Color(0xFFFFB199), Color(0xFFFF6A88)],
  'forest-glow-01' => const [Color(0xFF77E2A7), Color(0xFF2F7D68)],
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
    presetKey: 'sunset-peach-01',
    overlayKey: 'dark-soft',
  ),
];
