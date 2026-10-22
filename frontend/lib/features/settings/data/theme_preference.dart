import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/legacy.dart';

abstract interface class ThemePreferenceStore {
  Future<String?> read();
  Future<void> write(String value);
}

class StoredThemePreference implements ThemePreferenceStore {
  const StoredThemePreference(this.storage);
  final FlutterSecureStorage storage;
  static const key = 'app.theme_preference.v1';
  @override
  Future<String?> read() => storage.read(key: key);
  @override
  Future<void> write(String value) => storage.write(key: key, value: value);
}

class ThemeController extends ChangeNotifier {
  ThemeController(this.store);
  final ThemePreferenceStore store;
  ThemeMode mode = ThemeMode.system;
  bool saving = false;
  String? error;
  bool _disposed = false;
  int _revision = 0;
  Future<void> load() async {
    final revision = _revision;
    try {
      final stored = await store.read();
      if (revision != _revision || _disposed) return;
      mode =
          ThemeMode.values.where((value) => value.name == stored).firstOrNull ??
          ThemeMode.system;
    } catch (_) {
      /* Missing platform storage leaves the safe system default. */
    }
    if (!_disposed) notifyListeners();
  }

  Future<void> set(ThemeMode next) async {
    if (saving || _disposed) return;
    _revision++;
    saving = true;
    error = null;
    notifyListeners();
    try {
      await store.write(next.name);
      if (!_disposed) mode = next;
    } catch (_) {
      error = 'Could not save your theme preference.';
    } finally {
      saving = false;
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

final themeControllerProvider = ChangeNotifierProvider<ThemeController>((ref) {
  final controller = ThemeController(
    const StoredThemePreference(FlutterSecureStorage()),
  );
  controller.load();
  return controller;
});
