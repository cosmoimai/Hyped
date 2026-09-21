class ApiEnvironment {
  const ApiEnvironment._();
  static const baseUrl = String.fromEnvironment(
    'HYPED_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1',
  );
}
