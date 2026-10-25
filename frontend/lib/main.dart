import 'package:flutter/widgets.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/app/bootstrap/app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LicenseRegistry.addLicense(() async* {
    for (final family in ['Manrope', 'Outfit']) {
      yield LicenseEntryWithLineBreaks([
        family,
      ], await rootBundle.loadString('assets/fonts/$family-OFL.txt'));
    }
  });
  runApp(const ProviderScope(child: HypedApp()));
}
