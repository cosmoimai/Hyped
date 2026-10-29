import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Explicit destinations also work for direct links with no Navigator history.
class RoomAppBar extends StatelessWidget implements PreferredSizeWidget {
  const RoomAppBar({
    required this.title,
    this.backLocation = '/home',
    this.actions = const [],
    super.key,
  });

  final String title;
  final String backLocation;
  final List<Widget> actions;

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return AppBar(
      title: Text(title),
      leading: BackButton(onPressed: () => context.go(backLocation)),
      actions: [
        ...actions,
        if (backLocation != '/home')
          IconButton(
            tooltip: 'Home',
            icon: const Icon(Icons.home_outlined),
            onPressed: () => context.go('/home'),
          ),
      ],
    );
  }
}
