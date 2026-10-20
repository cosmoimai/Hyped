/// Keeps a validated native widget destination through startup and sign-in.
class WidgetNavigation {
  String? _pending;
  String? get pending => _pending;
  void receive(Object? location) {
    if (location is String &&
        (RegExp(r'^/rooms/[A-Za-z0-9_-]{1,128}$').hasMatch(location) ||
            RegExp(
              r'^/widgets/room\?widgetId=[1-9][0-9]*$',
            ).hasMatch(location))) {
      _pending = location;
    }
  }

  String? takeIfAuthenticated(bool authenticated) {
    if (!authenticated) return null;
    final location = _pending;
    _pending = null;
    return location;
  }
}
