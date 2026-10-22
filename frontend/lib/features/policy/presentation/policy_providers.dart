import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/policy/data/policy_api.dart';
import 'package:hyped/features/policy/presentation/policy_controller.dart';

final Provider<PolicyApi> policyApiProvider = Provider<PolicyApi>(
  (ref) => PolicyApi(
    ref.read(publicDioProvider),
    ref.read(authenticatedDioProvider),
  ),
);
final ChangeNotifierProvider<PolicyController> policyControllerProvider =
    ChangeNotifierProvider<PolicyController>(
      (ref) => PolicyController(
        ref.read(policyApiProvider),
        ref.read(authControllerProvider),
        ref.read(tokenStoreProvider),
      ),
    );
