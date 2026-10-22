import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

class PolicyVersions {
  const PolicyVersions(this.terms, this.privacy, this.rules);
  final String terms;
  final String privacy;
  final String rules;
  String get key => '$terms/$privacy/$rules';
  factory PolicyVersions.fromJson(Map<String, dynamic> json) => PolicyVersions(
    json['termsVersion'] as String,
    json['privacyVersion'] as String,
    json['contentRulesVersion'] as String,
  );
}

class PolicyStatus {
  const PolicyStatus(
    this.required, {
    required this.available,
    required this.accepted,
  });
  final PolicyVersions required;
  final bool available;
  final bool accepted;
  factory PolicyStatus.fromJson(Map<String, dynamic> json) => PolicyStatus(
    PolicyVersions.fromJson(Map<String, dynamic>.from(json['required'] as Map)),
    available: json['documentsAvailable'] == true,
    accepted: json['currentAccepted'] == true,
  );
}

class LegalDocument {
  const LegalDocument(this.title, this.version, this.content, this.published);
  final String title;
  final String version;
  final String content;
  final bool published;
}

class PolicyApi {
  PolicyApi(this.publicDio, this.authenticatedDio);
  final Dio publicDio;
  final Dio authenticatedDio;
  Future<PolicyStatus> status(String session) async {
    try {
      final response = await authenticatedDio.get<Map<String, dynamic>>(
        '/me/policy-status',
        options: Options(extra: {'expectedSessionId': session}),
      );
      return PolicyStatus.fromJson(response.data!);
    } on DioException catch (failure) {
      throw PolicyApiException.from(failure);
    }
  }

  Future<PolicyStatus> accept(PolicyVersions versions, String session) async {
    try {
      final response = await authenticatedDio.post<Map<String, dynamic>>(
        '/me/policy-acceptances',
        data: {
          'termsVersion': versions.terms,
          'privacyVersion': versions.privacy,
          'contentRulesVersion': versions.rules,
          'accepted': true,
          'privacyAccepted': true,
          'adultAffirmed': true,
        },
        options: Options(
          headers: {'Idempotency-Key': const Uuid().v4()},
          extra: {'expectedSessionId': session},
        ),
      );
      return PolicyStatus.fromJson(response.data!);
    } on DioException catch (failure) {
      throw PolicyApiException.from(failure);
    }
  }

  Future<LegalDocument> document(String kind, [String? version]) async {
    if (kind != 'terms' && kind != 'privacy') {
      throw const PolicyApiException('LEGAL_DOCUMENT_NOT_FOUND');
    }
    try {
      if (version == null) {
        final response = await publicDio.get<Map<String, dynamic>>(
          '/public/policies',
        );
        final required = PolicyVersions.fromJson(
          Map<String, dynamic>.from(response.data!['required'] as Map),
        );
        version = kind == 'terms' ? required.terms : required.privacy;
      }
      if (!RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$').hasMatch(version)) {
        throw const PolicyApiException('LEGAL_DOCUMENT_NOT_FOUND');
      }
      final json = (await publicDio.get<Map<String, dynamic>>(
        '/public/legal/$kind/$version',
      )).data!;
      return LegalDocument(
        json['title'] as String,
        json['version'] as String,
        json['content'] as String,
        json['published'] == true,
      );
    } on DioException catch (failure) {
      throw PolicyApiException.from(failure);
    }
  }
}

class PolicyApiException implements Exception {
  const PolicyApiException(this.code);
  final String? code;
  factory PolicyApiException.from(DioException failure) => PolicyApiException(
    failure.response?.data is Map
        ? (failure.response!.data as Map)['code'] as String?
        : null,
  );
  @override
  String toString() => 'Could not complete the policy request.';
}
